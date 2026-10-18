"""手写公式识别：几种编码器结构的对比实验，和 mathwriting_baseline.py 的基线用同一份数据、同样步数比较。

结构（--arch）：
    points     基线：点序列 → 两层一维卷积降采样 → 二维绝对位置编码 → Transformer（直接复用基线代码）
    points-sc  A：同上，但卷积不跨笔——卷积窗口里只取和中心点同一笔的点，相邻两笔首尾不再混在一起
    segments   B：长笔画切成不超过 16 点的“笔画段”，每段单独用小卷积编码成一个单元，
               单元加二维位置（段中心）和大小，Transformer 只看空间位置、不看书写先后
    segments-rel C：B + 二维相对位置注意力：每对单元按 (Δx, Δy, 大小差) 算一个注意力偏置

用法：
    python formula_arch_ablation.py train --arch segments --data …/prepared --out …/ablation/segments --max-steps 19454
    python formula_arch_ablation.py eval  --data …/prepared --ckpt …/last.pt [--samples 训练模式导出.jsonl]

评估除了验证集整条全对率（ExpRate）和记号错误率（CER），还测“笔顺打乱”：
把每条公式的笔画先后随机重排（每笔内部方向不变）后再识别，看掉多少分。
数据许可 CC BY-NC-SA 4.0：模型只在本机和自己设备上用。
"""
from __future__ import annotations

import argparse
import json
import math
import pathlib
import random
import sys
import time

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mathwriting_baseline as base  # noqa: E402

FEAT = base.FEAT
PAD, BOS, EOS, UNK = base.PAD, base.BOS, base.EOS, base.UNK
SEG_LEN = 16
SEG_FEAT = 6  # 段中心 x、y，段宽、高，是否一笔开头、是否一笔结尾
ARCHS = ("points", "points-sc", "segments", "segments-rel", "segments-rel-t")
TIME_FEAT = 2  # 弱时间信息：这一段所在笔画的书写先后（0~1）、有没有给时间（被遮掉时两个都为 0）


# ── 数据：按结构组织输入 ─────────────────────────────────────────────

def split_strokes(x: np.ndarray) -> list[np.ndarray]:
    starts = np.flatnonzero(x[:, 5] > 0.5)
    if len(starts) == 0 or starts[0] != 0:
        starts = np.concatenate([[0], starts])
    return [x[a:b] for a, b in zip(starts, list(starts[1:]) + [len(x)])]


def join_strokes(strokes: list[np.ndarray]) -> np.ndarray:
    """按新顺序拼回去，重算 dx、dy（笔画之间的跳跃也按新顺序算）。"""
    f = np.concatenate(strokes).copy()
    f[0, 2:4] = 0
    f[1:, 2:4] = f[1:, 0:2] - f[:-1, 0:2]
    return f


def shuffle_strokes(x: np.ndarray, rnd: random.Random) -> np.ndarray:
    strokes = split_strokes(x)
    rnd.shuffle(strokes)
    return join_strokes(strokes)


def local_swaps(x: np.ndarray, rnd: random.Random, p: float) -> np.ndarray:
    """贴近真人的笔顺变化：以概率 p 交换少数几对相邻笔画（不是完全打乱）。"""
    if p <= 0 or rnd.random() >= p:
        return x
    strokes = split_strokes(x)
    if len(strokes) < 2:
        return x
    for _ in range(rnd.randint(1, max(1, len(strokes) // 4))):
        i = rnd.randrange(len(strokes) - 1)
        strokes[i], strokes[i + 1] = strokes[i + 1], strokes[i]
    return join_strokes(strokes)


def collate_points(items, device):
    return base.collate(items, device)


def collate_segments(items, device, time_drop: float | None = None, rnd: random.Random | None = None):
    """每条样本：笔画 → 段（每段 ≤ SEG_LEN 点，长笔画均分）。段内点坐标相对段中心，笔画间的跳跃位移置零。
    time_drop 不为 None 时每段多两个弱时间特征，并以 time_drop 的概率整条样本遮掉。"""
    import torch
    per_sample = []
    for x, _ in items:
        segs = []
        strokes = split_strokes(x)
        keep_time = time_drop is not None and (rnd is None or rnd.random() >= time_drop)
        for si, st in enumerate(strokes):
            n = max(1, math.ceil(len(st) / SEG_LEN))
            bounds = np.linspace(0, len(st), n + 1).round().astype(int)
            for k in range(n):
                p = st[bounds[k]:bounds[k + 1]]
                if len(p) == 0:
                    continue
                mn, mx = p[:, :2].min(0), p[:, :2].max(0)
                c = (mn + mx) / 2
                q = p.copy()
                q[:, 0:2] -= c
                if k == 0:
                    q[0, 2:4] = 0  # 不带“从上一笔跳过来”的位移，单元本身不含书写先后
                seg_feat = [c[0], c[1], mx[0] - mn[0], mx[1] - mn[1], float(k == 0), float(k == n - 1)]
                if time_drop is not None:
                    seg_feat += [si / max(len(strokes) - 1, 1), 1.0] if keep_time else [0.0, 0.0]
                segs.append((q, seg_feat))
        per_sample.append(segs)
    B = len(items)
    S = max(len(s) for s in per_sample)
    P = np.zeros((B, S, SEG_LEN, FEAT), np.float32)
    PM = np.zeros((B, S, SEG_LEN), np.bool_)
    C = np.zeros((B, S, SEG_FEAT + (TIME_FEAT if time_drop is not None else 0)), np.float32)
    SM = np.zeros((B, S), np.bool_)
    for b, segs in enumerate(per_sample):
        for s, (q, f) in enumerate(segs):
            P[b, s, :len(q)] = q
            PM[b, s, :len(q)] = True
            C[b, s] = f
            SM[b, s] = True
    t = max(len(y) for _, y in items) + 2
    Y = np.full((B, t), PAD, np.int64)
    for b, (_, y) in enumerate(items):
        Y[b, 0] = BOS
        Y[b, 1:len(y) + 1] = y
        Y[b, len(y) + 1] = EOS
    to = lambda a: torch.from_numpy(a).to(device, non_blocking=True)  # noqa: E731
    return (to(P), to(PM), to(C), to(SM)), to(Y)


def collate(arch: str, items, device, time_drop: float = 0.0, rnd: random.Random | None = None):
    """time_drop 只在训练时用；评估时时间信息总是给（和真实输入一样）。"""
    if arch.endswith("-t"):
        return collate_segments(items, device, time_drop, rnd)
    if arch.startswith("segments"):
        return collate_segments(items, device)
    x, m, y = collate_points(items, device)
    return (x, m), y


# ── 模型 ──────────────────────────────────────────────────────────────

def build_model(arch: str, vocab_size: int, d: int = 256, enc_layers: int = 6, dec_layers: int = 3, heads: int = 4):
    import torch
    import torch.nn as nn

    # PyTorch 推理快速路径处理逐样本的浮点注意力偏置（C 的相对位置）会算出 NaN，统一关掉
    torch.backends.mha.set_fastpath_enabled(False)

    if arch == "points":
        return base.build_model(vocab_size, d, enc_layers, dec_layers, heads)

    class Pos2D(nn.Module):
        def __init__(self, d):
            super().__init__()
            q = d // 4
            self.register_buffer("freq", torch.exp(torch.arange(q) * (-math.log(1000.0) / q)) * 40.0)

        def forward(self, xy):
            ax = xy[..., :1] * self.freq
            ay = xy[..., 1:2] * self.freq
            return torch.cat([ax.sin(), ax.cos(), ay.sin(), ay.cos()], -1)

    class StrokeConv(nn.Module):
        """步长 2、核 5 的一维卷积，但窗口里只取和中心点同一笔的点（其余当 0）。"""

        def __init__(self, d, k=5):
            super().__init__()
            self.k = k
            self.lin = nn.Linear(d * k, d)

        def forward(self, h, sid):
            k, p = self.k, self.k // 2
            hp = nn.functional.pad(h, (0, 0, p, p))
            sp = nn.functional.pad(sid, (p, p), value=-1)
            hw = hp.unfold(1, k, 2)  # (B, L', d, k)
            sw = sp.unfold(1, k, 2)  # (B, L', k)
            same = (sw == sw[..., k // 2:k // 2 + 1]).to(h.dtype)
            hw = hw * same[:, :, None, :]
            out = self.lin(hw.reshape(hw.shape[0], hw.shape[1], -1))
            return out, sw[..., k // 2]

    class Decoder(nn.Module):
        def __init__(self):
            super().__init__()
            self.emb = nn.Embedding(vocab_size, d)
            self.tpos = nn.Embedding(base.MAX_TOKENS + 2, d)
            self.dec = nn.TransformerDecoder(
                nn.TransformerDecoderLayer(d, heads, d * 4, 0.1, batch_first=True, norm_first=True), dec_layers)
            self.norm = nn.LayerNorm(d)
            self.out = nn.Linear(d, vocab_size)

        def forward(self, mem, mm, y):
            t = y.shape[1]
            causal = torch.triu(torch.ones(t, t, dtype=torch.bool, device=y.device), 1)
            e = self.emb(y) + self.tpos(torch.arange(t, device=y.device))
            h = self.dec(e, mem, tgt_mask=causal, tgt_key_padding_mask=(y == PAD), memory_key_padding_mask=~mm)
            return self.out(self.norm(h))

    class PointsSC(nn.Module):
        def __init__(self):
            super().__init__()
            self.inp = nn.Linear(FEAT, d)
            self.c1, self.c2 = StrokeConv(d), StrokeConv(d)
            self.act = nn.GELU()
            self.pos = Pos2D(d)
            self.enc = nn.TransformerEncoder(
                nn.TransformerEncoderLayer(d, heads, d * 4, 0.1, batch_first=True, norm_first=True), enc_layers)
            self.dec = Decoder()

        def encode(self, x, m):
            sid = torch.cumsum((x[..., 5] > 0.5).long(), 1)
            sid = torch.where(m, sid, torch.full_like(sid, -2))
            h, s1 = self.c1(self.inp(x), sid)
            h, _ = self.c2(self.act(h), s1)
            h = self.act(h)
            xy = x[:, ::4, :2][:, :h.shape[1]]
            mm = m[:, ::4][:, :h.shape[1]]
            h = h + self.pos(xy)
            return self.enc(h, src_key_padding_mask=~mm), mm

        def decode(self, mem, mm, y):
            return self.dec(mem, mm, y)

        def forward(self, x, m, y):
            return self.decode(*self.encode(x, m), y)

    class Segments(nn.Module):
        def __init__(self, relative: bool, seg_feat: int = SEG_FEAT):
            super().__init__()
            self.relative = relative
            self.pin = nn.Linear(FEAT, d)
            self.pconv = nn.Sequential(nn.Conv1d(d, d, 3, padding=1), nn.GELU(), nn.Conv1d(d, d, 3, padding=1), nn.GELU())
            self.pool = nn.Linear(2 * d, d)
            self.seg = nn.Linear(seg_feat, d)
            self.pos = Pos2D(d)
            self.layers = nn.ModuleList(
                nn.TransformerEncoderLayer(d, heads, d * 4, 0.1, batch_first=True, norm_first=True) for _ in range(enc_layers))
            self.enc_norm = nn.LayerNorm(d)
            if relative:
                # 每层一组偏置：输入 (Δx, Δy, |Δx|, |Δy|, Δ宽, Δ高)
                self.rel = nn.ModuleList(
                    nn.Sequential(nn.Linear(6, 64), nn.GELU(), nn.Linear(64, heads)) for _ in range(enc_layers))
            self.dec = Decoder()

        def encode(self, P, PM, C, SM):
            B, S, L, _ = P.shape
            h = self.pin(P.reshape(B * S, L, FEAT))
            h = self.pconv(h.transpose(1, 2)).transpose(1, 2)
            pm = PM.reshape(B * S, L, 1)
            mx = h.masked_fill(~pm, -1e4).max(1).values
            mean = (h * pm).sum(1) / pm.sum(1).clamp_min(1)
            # 补齐出来的空段没有点，max 会取到填充值 -1e4，经线性层放大到十几万，fp16 溢出成 Inf，
            # 再经注意力 0×Inf 变 NaN 传给真实单元——这就是 fp16 训练中途坏掉的原因。空段一律置零。
            has = pm.any(1)
            mx = torch.where(has, mx, torch.zeros_like(mx))
            tok = self.pool(torch.cat([mx, mean], -1)).reshape(B, S, -1)
            tok = tok + self.seg(C) + self.pos(C[..., :2])
            pad = torch.zeros(B, 1, S, device=P.device, dtype=tok.dtype).masked_fill(~SM[:, None, :], -1e4)
            if self.relative:
                c = C[..., :4]
                dxy = c[:, None, :, :2] - c[:, :, None, :2]
                dwh = c[:, None, :, 2:4] - c[:, :, None, 2:4]
                feats = torch.cat([dxy, dxy.abs(), dwh], -1)  # (B, S, S, 6)
            h = tok
            for i, layer in enumerate(self.layers):
                if self.relative:
                    bias = self.rel[i](feats).permute(0, 3, 1, 2).to(h.dtype)  # (B, H, S, S)
                    mask = (bias + pad[:, None]).reshape(B * heads, S, S)
                else:
                    mask = pad.expand(B, S, S).repeat_interleave(heads, 0)
                h = layer(h, src_mask=mask)
            return self.enc_norm(h), SM

        def decode(self, mem, mm, y):
            return self.dec(mem, mm, y)

        def forward(self, P, PM, C, SM, y):
            return self.decode(*self.encode(P, PM, C, SM), y)

    if arch == "points-sc":
        return PointsSC()
    if arch == "segments":
        return Segments(relative=False)
    if arch == "segments-rel":
        return Segments(relative=True)
    if arch == "segments-rel-t":
        return Segments(relative=True, seg_feat=SEG_FEAT + TIME_FEAT)
    raise SystemExit(f"未知结构 {arch}")


def greedy(model, inp, max_len=base.MAX_TOKENS):
    import torch
    mem, mm = model.encode(*inp)
    B = mem.shape[0]
    y = torch.full((B, 1), BOS, dtype=torch.long, device=mem.device)
    done = torch.zeros(B, dtype=torch.bool, device=mem.device)
    for _ in range(max_len):
        nxt = model.decode(mem, mm, y)[:, -1].argmax(-1)
        nxt = torch.where(done, torch.full_like(nxt, PAD), nxt)
        y = torch.cat([y, nxt[:, None]], 1)
        done |= nxt == EOS
        if done.all():
            break
    out = []
    for row in y[:, 1:].tolist():
        seq = []
        for t in row:
            if t in (EOS, PAD):
                break
            seq.append(t)
        out.append(seq)
    return out


def amp_dtype(arch: str):
    """笔画段结构用 fp16 训到 5000 步后数值溢出成 NaN，改用 bf16（范围同 fp32）。"""
    import torch
    return torch.bfloat16 if arch.startswith("segments") else torch.float16


def evaluate(arch, model, split, device, limit: int, shuffle: bool = False, max_points: int = 60000) -> dict:
    import torch
    rnd = random.Random(0)
    idx = sorted(rnd.sample(range(split.n), min(limit, split.n)), key=lambda i: split.lens[i])
    srnd = random.Random(1)
    exact = errs = total = 0
    model.eval()
    with torch.no_grad(), torch.autocast("cuda", dtype=amp_dtype(arch), enabled=device.type == "cuda"):
        i = 0
        while i < len(idx):
            chunk = [idx[i]]
            while i + len(chunk) < len(idx) and split.lens[idx[i + len(chunk)]] * (len(chunk) + 1) <= max_points and len(chunk) < 128:
                chunk.append(idx[i + len(chunk)])
            i += len(chunk)
            items = [split.item(j) for j in chunk]
            if shuffle:
                items = [(shuffle_strokes(x, srnd), y) for x, y in items]
            inp, _ = collate(arch, items, device)
            for pred, (_, gold) in zip(greedy(model, inp), items):
                gold = gold.tolist()
                exact += int(pred == gold)
                errs += base.edit_distance(pred, gold)
                total += len(gold)
    model.train()
    return {"n": len(idx), "exp_rate": exact / max(len(idx), 1), "cer": errs / max(total, 1)}


def cmd_train(args) -> int:
    import torch
    import torch.nn.functional as F

    data, out = pathlib.Path(args.data), pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    vocab = json.loads((data / "vocab.json").read_text(encoding="utf-8"))
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train, valid = base.Split(data, "train"), base.Split(data, "valid")
    model = build_model(args.arch, len(vocab)).to(device)
    params = sum(p.numel() for p in model.parameters())
    print(f"结构 {args.arch}，训练 {train.n} 条，参数 {params / 1e6:.1f}M，设备 {device}", flush=True)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    dtype = amp_dtype(args.arch)
    scaler = torch.amp.GradScaler(enabled=device.type == "cuda" and dtype == torch.float16)
    step, seen, start, skipped = 0, 0, time.time(), 0
    ckpt = out / "last.pt"
    if ckpt.exists():
        state = torch.load(ckpt, map_location=device)
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["opt"])
        step, seen = state["step"], state["seen"]
        print(f"从第 {step} 步继续", flush=True)
    rnd = random.Random(args.seed + step)
    log = (out / "log.jsonl").open("a", encoding="utf-8")

    def save():
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "step": step, "seen": seen,
                    "vocab": vocab, "arch": args.arch}, ckpt)

    def report():
        r = {"step": step, "minutes": (time.time() - start) / 60,
             "valid": evaluate(args.arch, model, valid, device, args.eval_n),
             "valid_shuffled": evaluate(args.arch, model, valid, device, args.eval_n, shuffle=True)}
        print("验证", json.dumps(r, ensure_ascii=False), flush=True)
        log.write(json.dumps(r, ensure_ascii=False) + "\n")
        log.flush()

    while step < args.max_steps:
        for chunk in base.batches(train, args.batch_points, rnd):
            t0 = time.time()
            inp, y = collate(args.arch, [train.item(i) for i in chunk], device)
            lr = args.lr * min(1.0, (step + 1) / args.warmup)
            for g in opt.param_groups:
                g["lr"] = lr
            with torch.autocast("cuda", dtype=dtype, enabled=device.type == "cuda"):
                logits = model(*inp, y[:, :-1])
                loss = F.cross_entropy(logits.reshape(-1, logits.shape[-1]).float(), y[:, 1:].reshape(-1),
                                       ignore_index=PAD, label_smoothing=0.1)
            if not torch.isfinite(loss):
                # 出现 NaN/Inf 就跳过这一批，不让坏梯度污染权重
                skipped += 1
                opt.zero_grad(set_to_none=True)
                continue
            opt.zero_grad(set_to_none=True)
            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(opt)
            scaler.update()
            step += 1
            seen += len(chunk)
            if args.throttle > 0:
                if device.type == "cuda":
                    torch.cuda.synchronize()
                time.sleep((time.time() - t0) * args.throttle)
            if step % 200 == 0:
                mem = torch.cuda.max_memory_allocated() / 2**30 if device.type == "cuda" else 0
                print(f"step {step} loss {loss.item():.3f} {(time.time() - start) / 60:.1f} 分钟 显存峰值 {mem:.1f}G 跳过 {skipped}", flush=True)
            if step % args.eval_every == 0:
                report()
                save()
            if step >= args.max_steps:
                break
    report()
    save()
    return 0


def cmd_eval(args) -> int:
    import torch

    data = pathlib.Path(args.data)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    state = torch.load(args.ckpt, map_location=device)
    vocab, arch = state["vocab"], state.get("arch", "points")
    model = build_model(arch, len(vocab)).to(device)
    model.load_state_dict(state["model"])
    result = {"arch": arch}
    for name in ("valid", "test"):
        split = base.Split(data, name)
        result[name] = evaluate(arch, model, split, device, args.eval_n)
        result[name + "_shuffled"] = evaluate(arch, model, split, device, args.eval_n, shuffle=True)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    if args.samples:
        index = {t: i for i, t in enumerate(vocab)}
        exact = n = 0
        model.eval()
        with torch.no_grad():
            for path in args.samples:
                for line in open(path, encoding="utf-8"):
                    if not line.strip():
                        continue
                    s = json.loads(line)
                    if s.get("type") != "formula":
                        continue
                    x = base.featurize([[(p[0], p[1]) for p in st["points"]] for st in s["strokes"]])
                    if x is None:
                        continue
                    inp, _ = collate(arch, [(x.astype(np.float32), np.zeros(1, np.int64))], device)
                    ids = greedy(model, inp)[0]
                    ok = ids == [index.get(t, UNK) for t in base.tokenize(s["label"])]
                    exact += ok
                    n += 1
                    print(("✓ " if ok else "✗ ") + s["label"] + "\n    → " + " ".join(vocab[t] for t in ids), flush=True)
        print(f"你的公式：整条全对 {exact}/{n}", flush=True)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    t = sub.add_parser("train")
    t.add_argument("--arch", choices=ARCHS, required=True)
    t.add_argument("--data", required=True)
    t.add_argument("--out", required=True)
    t.add_argument("--max-steps", type=int, default=19454, help="和基线 30 分钟走的步数相同")
    t.add_argument("--batch-points", type=int, default=48000)
    t.add_argument("--lr", type=float, default=5e-4)
    t.add_argument("--warmup", type=int, default=2000)
    t.add_argument("--eval-every", type=int, default=5000)
    t.add_argument("--eval-n", type=int, default=1000)
    t.add_argument("--seed", type=int, default=7)
    t.add_argument("--throttle", type=float, default=0.0)
    e = sub.add_parser("eval")
    e.add_argument("--data", required=True)
    e.add_argument("--ckpt", required=True)
    e.add_argument("--eval-n", type=int, default=2000)
    e.add_argument("--samples", nargs="*")
    args = ap.parse_args()
    return {"train": cmd_train, "eval": cmd_eval}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
