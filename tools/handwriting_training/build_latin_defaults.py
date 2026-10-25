"""英文手写内置模板：从 MathWriting 单字符样本里每个字母 / 数字取几份不同人的笔迹，存成笔画。

    python build_latin_defaults.py [--mathwriting <converted 目录>] [--per-label 8]

输出 app/src/main/assets/handwriting/latin_default_strokes.json（格式同 kana_default_strokes.json）。
特征由设备上的公式模型第一次打开英文手写时现算并缓存（公式模型不在安装包里，没法预算）。
离线评估：只用个人模板时别人写的字母首选约 72%，每个字母加 5～20 份内置模板后约 98%（不分大小写）。
"""
from __future__ import annotations

import argparse
import gzip
import json
import pathlib
import random
import string

LABELS = set(string.ascii_letters + string.digits)


def simplify(stroke, step):
    """丢掉离上一个保留点太近的点（公式模型编码前本来就会按最小步长重采样），文件小一半以上。"""
    out = [stroke[0]]
    for p in stroke[1:]:
        if abs(p[0] - out[-1][0]) + abs(p[1] - out[-1][1]) >= step:
            out.append(p)
    if out[-1] != stroke[-1]:
        out.append(stroke[-1])
    return [[round(x, 1), round(y, 1)] for x, y in out]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mathwriting", default="D:/CyIME-Data/datasets/mathwriting/converted")
    ap.add_argument("--per-label", type=int, default=8)
    ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/handwriting/latin_default_strokes.json"))
    args = ap.parse_args()
    by: dict[str, list] = {}
    with gzip.open(pathlib.Path(args.mathwriting) / "symbols.jsonl.gz", "rt", encoding="utf-8") as f:
        for line in f:
            s = json.loads(line)
            if s["label"] in LABELS:
                by.setdefault(s["label"], []).append(s["strokes"])
    rnd = random.Random(0)
    chars = {}
    for label in sorted(by):
        samples = by[label]
        rnd.shuffle(samples)
        picked = []
        for st in samples[: args.per_label]:
            xs = [p[0] for q in st for p in q]
            ys = [p[1] for q in st for p in q]
            size = max(max(xs) - min(xs), max(ys) - min(ys), 1e-6)
            picked.append([simplify([(p[0], p[1]) for p in q], size * 0.01) for q in st if q])
        chars[label] = picked
    pathlib.Path(args.out).write_text(json.dumps({"source": "MathWriting symbols", "chars": chars}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(len(chars), "labels", sum(len(v) for v in chars.values()), "samples ->", args.out, pathlib.Path(args.out).stat().st_size // 1024, "KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
