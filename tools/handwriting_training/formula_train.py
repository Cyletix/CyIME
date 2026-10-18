"""手写公式模型的正式训练：按电脑忙闲自动调负载，带早期检查、异常自动回滚和断点续跑。

用法：
    python formula_train.py --data D:/CyIME-Data/datasets/mathwriting/prepared --out <目录> [--arch segments-rel-t] [--epochs 22]

负载：每 15 秒读一次键鼠最后操作时间（Windows GetLastInputInfo）。
    有人在用（10 分钟内动过键鼠）：每步算完歇“本步耗时 × busy_throttle”，显卡大部分时间空闲，进程优先级调低；
    10 分钟没人动：满速。
防止白跑一晚：
    - 用 bf16（范围同 fp32），空笔画段的溢出问题已在模型里修掉；
    - 每步检查 loss 和梯度是否有限，异常批次存到 bad_batches/ 便于复查，这一步不更新权重；
      1000 步内超过 5 次异常 → 回到最近一次好的存档、学习率减半继续；回滚 3 次仍不行 → 停止并在 status.json 写明原因；
    - 早期检查：第 2000、6000 步各验证一次，6000 步时整条全对率低于 20%（正常应在 40% 左右）就停下报告；
    - 之后每 8000 步验证一次，存 best.pt；整条全对率比最好成绩低 8 个百分点以上 → 回滚到 best、学习率减半；
    - 每 2000 步存 last.pt，重跑同一条命令从断点接着训。
status.json 随时写当前进度、负载模式、最好成绩和异常次数。
数据许可 CC BY-NC-SA 4.0：模型只在本机和自己设备上用。
"""
from __future__ import annotations

import argparse
import ctypes
import json
import math
import pathlib
import random
import sys
import time

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import formula_arch_ablation as abl  # noqa: E402
import mathwriting_baseline as base  # noqa: E402


class LASTINPUTINFO(ctypes.Structure):
    _fields_ = [("cbSize", ctypes.c_uint), ("dwTime", ctypes.c_uint)]


def idle_seconds() -> float:
    """距离最后一次键盘鼠标操作的秒数；读不到时当作“有人在用”。"""
    try:
        info = LASTINPUTINFO()
        info.cbSize = ctypes.sizeof(info)
        if not ctypes.windll.user32.GetLastInputInfo(ctypes.byref(info)):
            return 0.0
        return (ctypes.windll.kernel32.GetTickCount() - info.dwTime) / 1000.0
    except Exception:
        return 0.0


def lower_priority() -> None:
    try:
        BELOW_NORMAL = 0x4000
        ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), BELOW_NORMAL)
    except Exception:
        pass


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--arch", default="segments-rel-t", choices=abl.ARCHS)
    ap.add_argument("--epochs", type=int, default=22)
    ap.add_argument("--batch-points", type=int, default=48000)
    ap.add_argument("--lr", type=float, default=5e-4)
    ap.add_argument("--warmup", type=int, default=2000)
    ap.add_argument("--time-drop", type=float, default=0.3, help="训练时整条样本遮掉时间信息的概率")
    ap.add_argument("--swap-p", type=float, default=0.3, help="训练时做相邻笔画交换的概率")
    ap.add_argument("--idle-after", type=float, default=600, help="键鼠多少秒不动算空闲，转满速")
    ap.add_argument("--busy-throttle", type=float, default=4.0, help="有人用电脑时每步歇 本步耗时×此值")
    ap.add_argument("--eval-n", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()

    import torch
    import torch.nn.functional as F

    lower_priority()
    data, out = pathlib.Path(args.data), pathlib.Path(args.out)
    (out / "bad_batches").mkdir(parents=True, exist_ok=True)
    vocab = json.loads((data / "vocab.json").read_text(encoding="utf-8"))
    device = torch.device("cuda")
    train, valid = base.Split(data, "train"), base.Split(data, "valid")
    model = build(args, vocab, device)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    st = {"step": 0, "seen": 0, "epoch": 0, "lr_scale": 1.0, "best": None, "rollbacks": 0, "bad_total": 0,
          "history": [], "state": "running", "reason": ""}
    last, best = out / "last.pt", out / "best.pt"
    if last.exists():
        ck = torch.load(last, map_location=device)
        model.load_state_dict(ck["model"])
        opt.load_state_dict(ck["opt"])
        st.update(ck["st"])
        st["state"], st["reason"] = "running", ""
        print(f"从第 {st['step']} 步继续", flush=True)
    steps_per_epoch = len(base.batches(train, args.batch_points, random.Random(0)))
    total = steps_per_epoch * args.epochs
    rnd = random.Random(args.seed + st["step"])
    log = (out / "log.jsonl").open("a", encoding="utf-8")
    start = time.time()
    recent_bad: list[int] = []
    mode, mode_checked = "busy", 0.0

    def write_status(**extra):
        s = dict(st, total_steps=total, mode=mode, idle_seconds=round(idle_seconds()),
                 hours=round((time.time() - start) / 3600, 2), updated=time.strftime("%Y-%m-%d %H:%M:%S"), **extra)
        s.pop("history", None)
        s["recent_evals"] = st["history"][-4:]
        (out / "status.json").write_text(json.dumps(s, ensure_ascii=False, indent=1), encoding="utf-8")

    def save(path):
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "vocab": vocab, "arch": args.arch, "st": st}, path)

    def restore(path, why):
        ck = torch.load(path, map_location=device)
        model.load_state_dict(ck["model"])
        opt.load_state_dict(ck["opt"])
        st["lr_scale"] *= 0.5
        st["rollbacks"] += 1
        print(f"回滚到 {path.name}（{why}），学习率减半为 ×{st['lr_scale']}", flush=True)

    def evaluate(tag):
        r = {"step": st["step"], "tag": tag,
             "valid": abl.evaluate(args.arch, model, valid, device, args.eval_n),
             "valid_shuffled": abl.evaluate(args.arch, model, valid, device, args.eval_n, shuffle=True)}
        st["history"].append(r)
        log.write(json.dumps(r, ensure_ascii=False) + "\n")
        log.flush()
        print("验证", json.dumps(r, ensure_ascii=False), flush=True)
        return r["valid"]["exp_rate"]

    good_ckpt = last if last.exists() else None
    print(f"共 {total} 步（{args.epochs} 轮），键鼠已空闲 {idle_seconds():.0f} 秒", flush=True)
    write_status()
    while st["step"] < total:
        for chunk in base.batches(train, args.batch_points, rnd):
            if st["step"] >= total:
                break
            t0 = time.time()
            if t0 - mode_checked > 15:
                mode = "idle" if idle_seconds() >= args.idle_after else "busy"
                mode_checked = t0
            items = [train.item(i) for i in chunk]
            if args.swap_p > 0:
                items = [(abl.local_swaps(x, rnd, args.swap_p), y) for x, y in items]
            inp, y = abl.collate(args.arch, items, device, args.time_drop, rnd)
            step = st["step"]
            lr = args.lr * min(1.0, (step + 1) / args.warmup)
            lr *= 0.05 + 0.95 * 0.5 * (1 + math.cos(math.pi * min(step / total, 1.0)))
            lr *= st["lr_scale"]
            for g in opt.param_groups:
                g["lr"] = lr
            with torch.autocast("cuda", dtype=torch.bfloat16):
                logits = model(*inp, y[:, :-1])
                loss = F.cross_entropy(logits.reshape(-1, logits.shape[-1]).float(), y[:, 1:].reshape(-1),
                                       ignore_index=base.PAD, label_smoothing=0.1)
            opt.zero_grad(set_to_none=True)
            ok = bool(torch.isfinite(loss))
            gnorm = float("nan")
            if ok:
                loss.backward()
                gnorm = float(torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0))
                ok = math.isfinite(gnorm)
            if not ok:
                st["bad_total"] += 1
                recent_bad.append(step)
                np.savez_compressed(out / "bad_batches" / f"step{step}.npz",
                                    ids=np.asarray(chunk), loss=float(loss.detach().float().cpu()), gnorm=gnorm)
                print(f"第 {step} 步出现异常数值（loss={loss.item()}, 梯度范数={gnorm}），这一步不更新，批次已保存", flush=True)
                opt.zero_grad(set_to_none=True)
                recent_bad = [s for s in recent_bad if s > step - 1000]
                if len(recent_bad) > 5:
                    if st["rollbacks"] >= 3 or good_ckpt is None:
                        st["state"], st["reason"] = "stopped", "异常数值反复出现，回滚 3 次仍未解决"
                        write_status()
                        return 1
                    restore(good_ckpt, "1000 步内异常超过 5 次")
                    recent_bad = []
                continue
            opt.step()
            st["step"] += 1
            st["seen"] += len(chunk)
            if mode == "busy" and args.busy_throttle > 0:
                torch.cuda.synchronize()
                time.sleep((time.time() - t0) * args.busy_throttle)
            step = st["step"]
            if step % 100 == 0:
                print(f"step {step}/{total} loss {loss.item():.3f} 梯度 {gnorm:.2f} lr {lr:.2e} 模式 {mode} "
                      f"{(time.time() - start) / 60:.1f} 分钟", flush=True)
                write_status(loss=round(loss.item(), 4))
            if step % 2000 == 0:
                save(last)
                good_ckpt = last
            if step in (2000, 6000) or step % 8000 == 0:
                acc = evaluate("early" if step <= 6000 else "regular")
                if step == 6000 and acc < 0.20:
                    st["state"], st["reason"] = "stopped", f"早期检查未过：6000 步整条全对 {acc:.1%}（正常约 40%）"
                    save(last)
                    write_status()
                    return 1
                if st["best"] is None or acc > st["best"]["exp_rate"]:
                    st["best"] = {"step": step, "exp_rate": acc}
                    save(best)
                elif step > 6000 and acc < st["best"]["exp_rate"] - 0.08:
                    restore(best, f"成绩从 {st['best']['exp_rate']:.1%} 掉到 {acc:.1%}")
                save(last)
                good_ckpt = last
                write_status()
        else:
            st["epoch"] += 1
            print(f"第 {st['epoch']} 轮结束", flush=True)
    acc = evaluate("final")
    if st["best"] is None or acc > st["best"]["exp_rate"]:
        st["best"] = {"step": st["step"], "exp_rate": acc}
        save(best)
    st["state"], st["reason"] = "done", ""
    save(last)
    write_status()
    return 0


def build(args, vocab, device):
    return abl.build_model(args.arch, len(vocab)).to(device)


if __name__ == "__main__":
    sys.exit(main())
