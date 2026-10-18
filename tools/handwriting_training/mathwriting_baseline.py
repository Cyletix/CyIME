"""手写公式识别基线：笔迹轨迹 → LaTeX，用 Google MathWriting 训练，看看这条路能做到什么程度。

三个子命令：
    python mathwriting_baseline.py prep  --src D:/CyIME-Data/datasets/mathwriting/converted --out D:/CyIME-Data/datasets/mathwriting/prepared
    python mathwriting_baseline.py train --data D:/CyIME-Data/datasets/mathwriting/prepared --out D:/CyIME-Data/handwriting_training/latex-baseline/<日期> [--minutes 30]
    python mathwriting_baseline.py eval  --data … --ckpt …/last.pt [--samples 训练模式导出.jsonl]

做法（基线，不追求最好）：
- 预处理：按公式高度统一缩放（不分别拉伸横竖，保留上下标的高低和大小），去掉太密的点，
  每点特征 (x, y, dx, dy, 笔画结束, 笔画开始)，最多 1024 点；LaTeX 按命令/单字符切成记号。
- 模型：两层步长 2 的一维卷积把点降采样 4 倍 → 加二维正弦位置编码（按点在纸面上的 x、y，
  不只按书写先后）→ Transformer 编码器 → Transformer 解码器逐个输出 LaTeX 记号。
- 评估：验证集整条全对率（ExpRate）和记号错误率（CER），贪心解码。
数据许可 CC BY-NC-SA 4.0：训出的模型只在本机和自己设备上用，不进公开发布。
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import pathlib
import random
import re
import sys
import time

import numpy as np

MAX_POINTS = 1024
MAX_TOKENS = 200
FEAT = 6
TOKEN_RE = re.compile(r"\\[A-Za-z]+|\\.|\S")
SPECIAL = ["<pad>", "<bos>", "<eos>", "<unk>"]
PAD, BOS, EOS, UNK = range(4)


def tokenize(label: str) -> list[str]:
    return TOKEN_RE.findall(label)


# ── 预处理 ────────────────────────────────────────────────────────────

def featurize(strokes) -> np.ndarray | None:
    """原始笔画（像素 + 毫秒）→ (N, 6) float16。"""
    pts = [(p[0], p[1]) for st in strokes for p in st]
    if len(pts) < 2:
        return None
    a = np.asarray(pts, np.float32)
    mn = a.min(0)
    h = float(a[:, 1].max() - mn[1])
    w = float(a[:, 0].max() - mn[0])
    # 按高度缩放；很扁的公式（一行长式）至少按宽度的 1/8 算，免得几个点被放得太大
    scale = max(h, w / 8.0, 1e-3)
    parts = []
    min_step = 0.015  # 沿笔迹每走公式高度的 1.5% 留一个点，太密的点丢掉
    for st in strokes:
        s = (np.asarray([(p[0], p[1]) for p in st], np.float32) - mn) / scale
        if len(s) > 2:
            seg = np.abs(np.diff(s, axis=0)).sum(1)
            bucket = np.floor(np.concatenate([[0.0], np.cumsum(seg)]) / min_step)
            keep = np.flatnonzero(np.diff(bucket, prepend=-1) > 0)
            if keep[-1] != len(s) - 1:
                keep = np.append(keep, len(s) - 1)
            s = s[keep]
        g = np.zeros((len(s), FEAT), np.float32)
        g[:, 0:2] = s
        g[-1, 4] = 1.0
        g[0, 5] = 1.0
        parts.append(g)
    f = np.concatenate(parts)
    f[1:, 2:4] = f[1:, 0:2] - f[:-1, 0:2]
    if len(f) > MAX_POINTS:
        return None
    return f.astype(np.float16)


def cmd_prep(args) -> int:
    src, out = pathlib.Path(args.src), pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    splits = {"train": ["train", "synthetic", "symbols"], "valid": ["valid"], "test": ["test"]}
    # 记号表只从训练数据建
    counts: dict[str, int] = {}
    for part in splits["train"]:
        with gzip.open(src / f"{part}.jsonl.gz", "rt", encoding="utf-8") as f:
            for line in f:
                for t in tokenize(json.loads(line)["label"]):
                    counts[t] = counts.get(t, 0) + 1
    vocab = SPECIAL + sorted(t for t, c in counts.items() if c >= 3)
    (out / "vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    index = {t: i for i, t in enumerate(vocab)}
    print(f"记号表 {len(vocab)} 个", flush=True)
    for name, parts in splits.items():
        feats, offsets, labels, lab_off, kinds = [], [0], [], [0], []
        dropped = 0
        for part in parts:
            with gzip.open(src / f"{part}.jsonl.gz", "rt", encoding="utf-8") as f:
                for line in f:
                    s = json.loads(line)
                    ids = [index.get(t, UNK) for t in tokenize(s["label"])]
                    if not ids or len(ids) > MAX_TOKENS - 2:
                        dropped += 1
                        continue
                    x = featurize(s["strokes"])
                    if x is None:
                        dropped += 1
                        continue
                    feats.append(x)
                    offsets.append(offsets[-1] + len(x))
                    labels.extend(ids)
                    lab_off.append(len(labels))
                    kinds.append({"train": 0, "synthetic": 1, "symbols": 2}.get(part, 0))
        np.save(out / f"{name}_x.npy", np.concatenate(feats))
        np.save(out / f"{name}_xo.npy", np.asarray(offsets, np.int64))
        np.save(out / f"{name}_y.npy", np.asarray(labels, np.int16))
        np.save(out / f"{name}_yo.npy", np.asarray(lab_off, np.int64))
        np.save(out / f"{name}_k.npy", np.asarray(kinds, np.int8))
        print(f"{name}: {len(feats)} 条，丢弃 {dropped}（太长或没有笔迹）", flush=True)
    return 0


# ── 数据 ──────────────────────────────────────────────────────────────

class Split:
    def __init__(self, root: pathlib.Path, name: str):
        self.x = np.load(root / f"{name}_x.npy", mmap_mode="r")
        self.xo = np.load(root / f"{name}_xo.npy")
        self.y = np.load(root / f"{name}_y.npy")
        self.yo = np.load(root / f"{name}_yo.npy")
        self.n = len(self.xo) - 1
        self.lens = np.diff(self.xo)

    def item(self, i: int):
        return np.asarray(self.x[self.xo[i]:self.xo[i + 1]], np.float32), self.y[self.yo[i]:self.yo[i + 1]].astype(np.int64)


def collate(items, device):
    import torch
    n = max(len(x) for x, _ in items)
    n = (n + 3) // 4 * 4
    t = max(len(y) for _, y in items) + 2
    X = np.zeros((len(items), n, FEAT), np.float32)
    M = np.zeros((len(items), n), np.bool_)
    Y = np.full((len(items), t), PAD, np.int64)
    for b, (x, y) in enumerate(items):
        X[b, :len(x)] = x
        M[b, :len(x)] = True
        Y[b, 0] = BOS
        Y[b, 1:len(y) + 1] = y
        Y[b, len(y) + 1] = EOS
    return (torch.from_numpy(X).to(device, non_blocking=True), torch.from_numpy(M).to(device),
            torch.from_numpy(Y).to(device))


def batches(split: Split, max_points: int, rnd: random.Random, shuffle=True):
    """按长度分桶、每批总点数不超过 max_points，减少补零浪费。"""
    order = np.argsort(split.lens + np.asarray([rnd.random() * 8 for _ in range(split.n)]) if shuffle else split.lens)
    chunks, cur, cur_max = [], [], 0
    for i in order:
        L = int(split.lens[i])
        if cur and max(cur_max, L) * (len(cur) + 1) > max_points:
            chunks.append(cur)
            cur, cur_max = [], 0
        cur.append(int(i))
        cur_max = max(cur_max, L)
    if cur:
        chunks.append(cur)
    if shuffle:
        rnd.shuffle(chunks)
    return chunks


# ── 模型 ──────────────────────────────────────────────────────────────

def build_model(vocab_size: int, d: int = 256, enc_layers: int = 6, dec_layers: int = 3, heads: int = 4):
    import torch
    import torch.nn as nn

    class Pos2D(nn.Module):
        """按纸面坐标 (x, y) 的二维正弦位置编码：前一半通道编码 x，后一半编码 y。"""

        def __init__(self, d):
            super().__init__()
            q = d // 4
            self.register_buffer("freq", torch.exp(torch.arange(q) * (-math.log(1000.0) / q)) * 40.0)

        def forward(self, xy):
            ax = xy[..., :1] * self.freq
            ay = xy[..., 1:2] * self.freq
            return torch.cat([ax.sin(), ax.cos(), ay.sin(), ay.cos()], -1)

    class Model(nn.Module):
        def __init__(self):
            super().__init__()
            self.inp = nn.Linear(FEAT, d)
            self.sub = nn.Sequential(
                nn.Conv1d(d, d, 5, stride=2, padding=2), nn.GELU(),
                nn.Conv1d(d, d, 5, stride=2, padding=2), nn.GELU(),
            )
            self.pos = Pos2D(d)
            self.enc = nn.TransformerEncoder(
                nn.TransformerEncoderLayer(d, heads, d * 4, 0.1, batch_first=True, norm_first=True), enc_layers)
            self.emb = nn.Embedding(vocab_size, d)
            self.tpos = nn.Embedding(MAX_TOKENS + 2, d)
            self.dec = nn.TransformerDecoder(
                nn.TransformerDecoderLayer(d, heads, d * 4, 0.1, batch_first=True, norm_first=True), dec_layers)
            self.norm = nn.LayerNorm(d)
            self.out = nn.Linear(d, vocab_size)

        def encode(self, x, m):
            h = self.sub(self.inp(x).transpose(1, 2)).transpose(1, 2)
            xy = x[:, ::4, :2][:, :h.shape[1]]
            mm = m[:, ::4][:, :h.shape[1]]
            h = h + self.pos(xy)
            return self.enc(h, src_key_padding_mask=~mm), mm

        def decode(self, mem, mm, y):
            t = y.shape[1]
            causal = torch.triu(torch.ones(t, t, dtype=torch.bool, device=y.device), 1)
            e = self.emb(y) + self.tpos(torch.arange(t, device=y.device))
            h = self.dec(e, mem, tgt_mask=causal, tgt_key_padding_mask=(y == PAD), memory_key_padding_mask=~mm)
            return self.out(self.norm(h))

        def forward(self, x, m, y):
            mem, mm = self.encode(x, m)
            return self.decode(mem, mm, y)

    return Model()


def greedy(model, x, m, max_len=MAX_TOKENS):
    import torch
    mem, mm = model.encode(x, m)
    y = torch.full((x.shape[0], 1), BOS, dtype=torch.long, device=x.device)
    done = torch.zeros(x.shape[0], dtype=torch.bool, device=x.device)
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


def edit_distance(a, b) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def evaluate(model, split: Split, device, limit: int, max_points: int = 60000) -> dict:
    import torch
    rnd = random.Random(0)
    idx = sorted(rnd.sample(range(split.n), min(limit, split.n)), key=lambda i: split.lens[i])
    exact = errs = total = 0
    model.eval()
    with torch.no_grad(), torch.autocast("cuda", dtype=torch.float16, enabled=device.type == "cuda"):
        i = 0
        while i < len(idx):
            chunk = [idx[i]]
            while i + len(chunk) < len(idx) and split.lens[idx[i + len(chunk)]] * (len(chunk) + 1) <= max_points and len(chunk) < 128:
                chunk.append(idx[i + len(chunk)])
            i += len(chunk)
            items = [split.item(j) for j in chunk]
            x, m, _ = collate(items, device)
            for pred, (_, gold) in zip(greedy(model, x, m), items):
                gold = gold.tolist()
                exact += int(pred == gold)
                errs += edit_distance(pred, gold)
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
    train, valid = Split(data, "train"), Split(data, "valid")
    model = build_model(len(vocab)).to(device)
    params = sum(p.numel() for p in model.parameters())
    print(f"训练 {train.n} 条，验证 {valid.n} 条，记号 {len(vocab)}，参数 {params / 1e6:.1f}M，设备 {device}", flush=True)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    scaler = torch.amp.GradScaler(enabled=device.type == "cuda")
    step, start, seen = 0, time.time(), 0
    ckpt = out / "last.pt"
    if ckpt.exists():
        state = torch.load(ckpt, map_location=device)
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["opt"])
        step, seen = state["step"], state["seen"]
        print(f"从第 {step} 步继续", flush=True)
        epoch = state.get("epoch", 0)
    else:
        epoch = 0
    rnd = random.Random(args.seed + step)
    deadline = start + args.minutes * 60 if args.minutes else None
    log = (out / "log.jsonl").open("a", encoding="utf-8")
    steps_per_epoch = len(batches(train, args.batch_points, random.Random(0)))
    total_steps = steps_per_epoch * args.epochs if args.epochs else 0
    while True:
        for chunk in batches(train, args.batch_points, rnd):
            t0 = time.time()
            x, m, y = collate([train.item(i) for i in chunk], device)
            lr = args.lr * min(1.0, (step + 1) / args.warmup)
            if total_steps:
                # 按总步数余弦退火到 5%，续跑时按已走步数接着降
                lr *= 0.05 + 0.95 * 0.5 * (1 + math.cos(math.pi * min(step / total_steps, 1.0)))
            for g in opt.param_groups:
                g["lr"] = lr
            with torch.autocast("cuda", dtype=torch.float16, enabled=device.type == "cuda"):
                logits = model(x, m, y[:, :-1])
                loss = F.cross_entropy(logits.reshape(-1, logits.shape[-1]).float(), y[:, 1:].reshape(-1),
                                       ignore_index=PAD, label_smoothing=0.1)
            opt.zero_grad(set_to_none=True)
            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(opt)
            scaler.update()
            step += 1
            seen += len(chunk)
            if args.throttle > 0:
                # 低负载：每步算完歇一会儿（歇的时间 = 本步耗时 × throttle），显卡不满载
                if device.type == "cuda":
                    torch.cuda.synchronize()
                time.sleep((time.time() - t0) * args.throttle)
            if step % 100 == 0:
                el = time.time() - start
                mem = torch.cuda.max_memory_allocated() / 2**30 if device.type == "cuda" else 0
                print(f"step {step} loss {loss.item():.3f} 已见 {seen} 条 {seen / max(el, 1):.0f} 条/秒 显存峰值 {mem:.1f}G", flush=True)
            timeout = deadline is not None and time.time() > deadline
            if step % args.eval_every == 0 or timeout:
                r = evaluate(model, valid, device, args.eval_n)
                r.update(step=step, seen=seen, loss=loss.item(), minutes=(time.time() - start) / 60)
                print("验证", json.dumps(r, ensure_ascii=False), flush=True)
                log.write(json.dumps(r, ensure_ascii=False) + "\n")
                log.flush()
                torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "step": step, "seen": seen,
                            "vocab": vocab, "epoch": epoch}, ckpt)
            if timeout:
                return 0
        epoch += 1
        print(f"第 {epoch} 轮结束", flush=True)
        if args.epochs and epoch >= args.epochs:
            break
    torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "step": step, "seen": seen, "vocab": vocab, "epoch": epoch}, ckpt)
    return 0


def cmd_eval(args) -> int:
    import torch

    data = pathlib.Path(args.data)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    state = torch.load(args.ckpt, map_location=device)
    vocab = state["vocab"]
    model = build_model(len(vocab)).to(device)
    model.load_state_dict(state["model"])
    for name in ("valid", "test"):
        print(name, json.dumps(evaluate(model, Split(data, name), device, args.eval_n), ensure_ascii=False), flush=True)
    if args.samples:
        # 训练模式导出的公式（type=formula），看在你自己的字迹上效果如何
        index = {t: i for i, t in enumerate(vocab)}
        rows = []
        for path in args.samples:
            for line in open(path, encoding="utf-8"):
                if line.strip():
                    s = json.loads(line)
                    if s.get("type") == "formula":
                        x = featurize([[(p[0], p[1]) for p in st["points"]] for st in s["strokes"]])
                        if x is not None:
                            rows.append((s["label"], x))
        model.eval()
        exact = 0
        with torch.no_grad():
            for label, x in rows:
                xx, mm, _ = collate([(x.astype(np.float32), np.zeros(1, np.int64))], device)
                ids = greedy(model, xx, mm)[0]
                pred = " ".join(vocab[t] for t in ids)
                ok = ids == [index.get(t, UNK) for t in tokenize(label)]
                exact += ok
                print(("✓ " if ok else "✗ ") + label + "\n    → " + pred, flush=True)
        print(f"你的公式：整条全对 {exact}/{len(rows)}", flush=True)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("prep")
    p.add_argument("--src", required=True)
    p.add_argument("--out", required=True)
    t = sub.add_parser("train")
    t.add_argument("--data", required=True)
    t.add_argument("--out", required=True)
    t.add_argument("--minutes", type=float, default=0, help="跑这么多分钟后评估、存档并退出（0 表示按轮数）")
    t.add_argument("--epochs", type=int, default=0)
    t.add_argument("--batch-points", type=int, default=48000, help="每批补齐后的总点数上限，控制显存")
    t.add_argument("--lr", type=float, default=5e-4)
    t.add_argument("--warmup", type=int, default=2000)
    t.add_argument("--eval-every", type=int, default=5000)
    t.add_argument("--eval-n", type=int, default=1000)
    t.add_argument("--seed", type=int, default=7)
    t.add_argument("--throttle", type=float, default=0.0, help="低负载：每步后休息 本步耗时×此值 秒")
    e = sub.add_parser("eval")
    e.add_argument("--data", required=True)
    e.add_argument("--ckpt", required=True)
    e.add_argument("--eval-n", type=int, default=2000)
    e.add_argument("--samples", nargs="*")
    args = ap.parse_args()
    return {"prep": cmd_prep, "train": cmd_train, "eval": cmd_eval}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
