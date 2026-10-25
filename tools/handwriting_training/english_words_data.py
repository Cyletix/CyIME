"""英文整词手写的训练数据：用单个字母的笔迹拼成整词（从左往右，按小写 / 大写 / 上下伸出的字母摆高低、留字距），
再混进一部分原公式数据，免得接着训练后公式能力退化。

    python english_words_data.py --out D:/CyIME-Data/handwriting_training/english-words/data [--words 200000] [--formula 150000]

字母来源：MathWriting 单字符样本（不同人写的，每个字母约 30 份，八成拼训练词、两成拼验证词）、
训练模式里自己写的字母（--samples，导出的 JSONL，可给多个）。词表：app/src/main/assets/english.txt（按常用度排序，
越常用的词抽到得越多），另有一成是随机字母串，免得只会认词表里的词。
输出格式同 mathwriting_baseline.py prep（*_x / *_xo / *_y / *_yo / *_k.npy + vocab.json），可直接给 formula_train.py 用；
kind 3 = 合成英文词。
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import pathlib
import random
import string
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mathwriting_baseline as base  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
SMALL = set("acemnorsuvwxz")
DESCEND = set("gpqy")
# 字母的上下范围（以 x 高为 1、基线为 0，向上为正）：(底, 顶)
def extent(ch: str, tall: float = 1.45) -> tuple[float, float]:
    """tall：大写和 b d h k l t 的高度（各人差别大，演示里用户的约为 x 高的 2.2 倍），整词共用一个值。"""
    drop = (tall - 1.0) * 0.9
    if ch in SMALL:
        return 0.0, 1.0
    if ch in DESCEND:
        return -drop, 1.0
    if ch == "j":
        return -drop, 1.0 + (tall - 1.0) * 0.8
    if ch == "i":
        return 0.0, 1.0 + (tall - 1.0) * 0.8
    return 0.0, tall  # 大写、数字、b d f h k l t


def load_letters(mathwriting: pathlib.Path, samples: list[str], rnd: random.Random):
    bank_train: dict[str, list] = {}
    bank_valid: dict[str, list] = {}
    letters = set(string.ascii_letters)
    by: dict[str, list] = {}
    with gzip.open(mathwriting / "symbols.jsonl.gz", "rt", encoding="utf-8") as f:
        for line in f:
            s = json.loads(line)
            if s["label"] in letters:
                by.setdefault(s["label"], []).append([[(p[0], p[1]) for p in st] for st in s["strokes"] if st])
    for ch, v in by.items():
        rnd.shuffle(v)
        cut = max(1, len(v) // 5)
        bank_valid[ch] = v[:cut]
        bank_train[ch] = v[cut:]
    seen = set()
    own = 0
    for path in samples:
        for line in open(path, encoding="utf-8"):
            if not line.strip():
                continue
            s = json.loads(line)
            if s.get("type") != "latin" or s.get("label") not in letters or s.get("id") in seen:
                continue
            seen.add(s.get("id"))
            st = [[(float(p[0]), float(p[1])) for p in q["points"]] for q in s["strokes"]]
            st = [q for q in st if q]
            if st:
                # 自己的笔迹多放几份，拼词时更常抽到
                bank_train.setdefault(s["label"], []).extend([st] * 6)
                own += 1
    print(f"字母：MathWriting {sum(len(v) for v in by.values())} 份，自己写的 {own} 份", flush=True)
    return bank_train, bank_valid


def place(letter_strokes, ch: str, x0: float, unit: float, baseline: float, rnd: random.Random, tall: float = 1.45):
    pts = [p for st in letter_strokes for p in st]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    w0 = max(max(xs) - min(xs), 1e-3)
    h0 = max(max(ys) - min(ys), 1e-3)
    lo, hi = extent(ch, tall)
    h = (hi - lo) * unit * rnd.uniform(0.88, 1.12)
    s = h / h0
    # 很扁的字母（横、点）按宽度限制，别放成一长条
    if w0 * s > 1.6 * unit:
        s = 1.6 * unit / w0
    top = baseline - hi * unit + rnd.uniform(-0.08, 0.08) * unit  # 屏幕坐标 y 向下
    out = [[(x0 + (p[0] - min(xs)) * s, top + (p[1] - min(ys)) * s) for p in st] for st in letter_strokes]
    return out, x0 + w0 * s


def make_word(word: str, bank, rnd: random.Random):
    unit = 40.0
    x = 0.0
    strokes = []
    tall = rnd.uniform(1.3, 2.4)
    # 字距：一半紧凑（手写常见），一半很松（平板上一个字母一个字母地写，演示里字距约为 x 高的 2.5 倍）
    wide = rnd.random() < 0.5
    for ch in word:
        cands = bank.get(ch) or bank.get(ch.swapcase())
        if not cands:
            return None
        placed, right = place(rnd.choice(cands), ch, x, unit, 100.0, rnd, tall)
        strokes.extend(placed)
        x = right + (rnd.uniform(0.6, 3.2) if wide else rnd.uniform(-0.05, 0.6)) * unit
    # 整词轻微倾斜、旋转
    shear = rnd.uniform(-0.2, 0.2)
    rot = math.radians(rnd.uniform(-4, 4))
    c, s = math.cos(rot), math.sin(rot)
    res = []
    for st in strokes:
        q = []
        for px, py in st:
            px = px + shear * (100.0 - py)
            q.append((px * c - py * s, px * s + py * c))
        res.append(q)
    return res


def pick_word(words: list[str], rnd: random.Random) -> str:
    if rnd.random() < 0.1:
        return "".join(rnd.choice(string.ascii_lowercase) for _ in range(rnd.randint(1, 8)))
    # 常用词抽得多：按名次的 1/sqrt 加权
    i = min(int(len(words) * rnd.random() ** 2), len(words) - 1)
    w = words[i]
    r = rnd.random()
    if r < 0.25:
        w = w[:1].upper() + w[1:]
    elif r < 0.3:
        w = w.upper()
    return w


def build_split(n: int, words, bank, index, rnd, name):
    feats, labels = [], []
    while len(feats) < n:
        w = pick_word(words, rnd)
        ink = make_word(w, bank, rnd)
        if ink is None:
            continue
        x = base.featurize(ink)
        if x is None:
            continue
        feats.append(x)
        labels.append([index[t] for t in base.tokenize(w)])
    print(f"{name}：合成英文词 {len(feats)} 条", flush=True)
    return feats, labels


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--mathwriting", default="D:/CyIME-Data/datasets/mathwriting/converted")
    ap.add_argument("--prepared", default="D:/CyIME-Data/datasets/mathwriting/prepared")
    ap.add_argument("--samples", nargs="*", default=[])
    ap.add_argument("--wordlist", default=str(ROOT / "app/src/main/assets/english.txt"))
    ap.add_argument("--words", type=int, default=200000)
    ap.add_argument("--valid-words", type=int, default=1000)
    ap.add_argument("--formula", type=int, default=150000, help="混进的原公式训练样本数")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    rnd = random.Random(args.seed)
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    prepared = pathlib.Path(args.prepared)
    vocab = json.loads((prepared / "vocab.json").read_text(encoding="utf-8"))
    (out / "vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    index = {t: i for i, t in enumerate(vocab)}
    words = [w.strip() for w in open(args.wordlist, encoding="utf-8") if w.strip().isascii() and w.strip().isalpha()]
    bank_train, bank_valid = load_letters(pathlib.Path(args.mathwriting), args.samples, rnd)

    for name, n_words, bank, n_formula in (("train", args.words, bank_train, args.formula),
                                            ("valid", args.valid_words, bank_valid, 1000)):
        feats, labels = build_split(n_words, words, bank, index, rnd, name)
        kinds = [3] * len(feats)
        src = base.Split(prepared, name)
        for i in rnd.sample(range(src.n), min(n_formula, src.n)):
            x, y = src.item(i)
            feats.append(x.astype(np.float16))
            labels.append(y.tolist())
            kinds.append(0)
        order = list(range(len(feats)))
        rnd.shuffle(order)
        xo, yo = [0], [0]
        for i in order:
            xo.append(xo[-1] + len(feats[i]))
            yo.append(yo[-1] + len(labels[i]))
        np.save(out / f"{name}_x.npy", np.concatenate([feats[i] for i in order]).astype(np.float16))
        np.save(out / f"{name}_xo.npy", np.asarray(xo, np.int64))
        np.save(out / f"{name}_y.npy", np.asarray([t for i in order for t in labels[i]], np.int16))
        np.save(out / f"{name}_yo.npy", np.asarray(yo, np.int64))
        np.save(out / f"{name}_k.npy", np.asarray([kinds[i] for i in order], np.int8))
        print(f"{name}：共 {len(order)} 条（公式 {len(order) - n_words}）", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
