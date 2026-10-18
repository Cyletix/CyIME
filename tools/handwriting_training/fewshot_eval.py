"""小样本（1-shot）手写识别能做到多少：每个字只拿一份样本当“模板”，认另一份。

    python fewshot_eval.py --samples <训练模式导出.jsonl> [--formula-onnx <目录>] [--mathwriting <converted 目录>]

假名：每个假名写了两遍的，一遍当模板、另一遍当测试（两个方向都算），所有写过的假名都当候选。
英文字母：用户每个只写了一遍，当模板；测试用 MathWriting 里别人写的单字母（跨书写人，更难）。
比较的办法（都不用训练）：
    dtw       笔迹重采样后逐点比距离（经典模板匹配，依赖笔顺）
    formula   公式模型编码器输出取平均当特征，余弦相似度找最近的模板（不看笔顺）
    ochwpro   内置汉字模型 7000 多类的输出分布当特征（汉字模型没学过假名，但形状相似的字会给出相似分布）
    combo     formula 和 ochwpro 的相似度相加
另加“模板增强”：模板做几份旋转、缩放、抖动的变体，取最相近的那份。
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
import pathlib
import random
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import finetune_ochwpro as och  # noqa: E402
import formula_arch_ablation as abl  # noqa: E402
import mathwriting_baseline as base  # noqa: E402


def load(path, types):
    out = []
    for line in open(path, encoding="utf-8"):
        if not line.strip():
            continue
        s = json.loads(line)
        if s.get("type") in types:
            st = [[(float(p[0]), float(p[1])) for p in q["points"]] for q in s["strokes"]]
            st = [q for q in st if q]
            if st:
                out.append((s["label"], st, s.get("created_at_ms", 0)))
    return out


def resample(strokes, n=48):
    pts = np.asarray([p for st in strokes for p in st], np.float32)
    mn, mx = pts.min(0), pts.max(0)
    pts = (pts - (mn + mx) / 2) / max(float((mx - mn).max()), 1e-3)
    d = np.r_[0, np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))]
    if d[-1] == 0:
        return np.repeat(pts[:1], n, 0)
    t = np.linspace(0, d[-1], n)
    return np.stack([np.interp(t, d, pts[:, 0]), np.interp(t, d, pts[:, 1])], 1)


def dtw(a, b):
    n, m = len(a), len(b)
    D = np.full((n + 1, m + 1), np.inf)
    D[0, 0] = 0
    cost = np.linalg.norm(a[:, None] - b[None], axis=2)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            D[i, j] = cost[i - 1, j - 1] + min(D[i - 1, j], D[i, j - 1], D[i - 1, j - 1])
    return D[n, m]


def augment(strokes, rnd):
    return och.augment(strokes, rnd)


class Feats:
    def __init__(self, formula_dir, app_model):
        import onnxruntime as ort
        self.enc = ort.InferenceSession(str(pathlib.Path(formula_dir) / "formula_encoder.onnx"), providers=["CPUExecutionProvider"])
        self.och = ort.InferenceSession(str(pathlib.Path(app_model) / "ochwpro.onnx"), providers=["CPUExecutionProvider"])
        self.och_in = [i.name for i in self.och.get_inputs()]
        self.och_bool = self.och.get_inputs()[1].type == "tensor(bool)"

    def formula(self, strokes):
        x = base.featurize(strokes)
        if x is None:
            return None
        (P, PM, C, SM), _ = abl.collate("segments-rel-t", [(x.astype(np.float32), np.zeros(1, np.int64))], "cpu", time_drop=0.0)
        mem = self.enc.run(None, {"P": P.numpy(), "PM": PM.numpy(), "C": C.numpy(), "SM": SM.numpy()})[0][0]
        v = mem.mean(0)
        return v / (np.linalg.norm(v) + 1e-6)

    def ochwpro(self, strokes):
        x, m = och.to_sequence(strokes)
        mm = m[None] if self.och_bool else m[None].astype(np.uint8)
        lg = self.och.run(None, {self.och_in[0]: x[None], self.och_in[1]: mm})[0][0]
        p = np.exp(lg - lg.max())
        p = np.sqrt(p / p.sum())  # 开方后的概率向量，余弦相似度即 Bhattacharyya 系数
        return p / (np.linalg.norm(p) + 1e-6)


def evaluate(name, support, queries, sim, k=5):
    labels = [l for l, _ in support]
    top1 = top5 = 0
    for ql, qf in queries:
        s = np.asarray([sim(qf, sf) for _, sf in support])
        order = np.argsort(-s)
        ranked = []
        for i in order:
            if labels[i] not in ranked:
                ranked.append(labels[i])
            if len(ranked) >= k:
                break
        top1 += ranked[0] == ql
        top5 += ql in ranked
    n = max(len(queries), 1)
    print(f"  {name:<22} top1 {top1 / n:6.1%}   top5 {top5 / n:6.1%}   （{len(queries)} 条，{len(set(labels))} 选 1）", flush=True)


def run_task(title, support_raw, query_raw, feats: Feats, aug_n: int, seed=0):
    rnd = random.Random(seed)
    print(f"\n{title}", flush=True)
    sup = [(l, st) for l, st in support_raw]
    if aug_n:
        sup_aug = sup + [(l, augment(st, rnd)) for l, st in sup for _ in range(aug_n)]
    else:
        sup_aug = sup
    # dtw
    S = [(l, resample(st)) for l, st in sup]
    Q = [(l, resample(st)) for l, st in query_raw]
    evaluate("dtw（模板匹配）", S, Q, lambda a, b: -dtw(a, b))
    for tag, s_list in (("", sup), (f" + 模板增强×{aug_n}", sup_aug)) if aug_n else (("", sup),):
        Sf = [(l, feats.formula(st)) for l, st in s_list]
        Sf = [(l, f) for l, f in Sf if f is not None]
        Qf = [(l, feats.formula(st)) for l, st in query_raw]
        Qf = [(l, f) for l, f in Qf if f is not None]
        evaluate("formula" + tag, Sf, Qf, lambda a, b: float(a @ b))
        So = [(l, feats.ochwpro(st)) for l, st in s_list]
        Qo = [(l, feats.ochwpro(st)) for l, st in query_raw]
        evaluate("ochwpro" + tag, So, Qo, lambda a, b: float(a @ b))
        Sc = [(l, (a, b)) for (l, a), (_, b) in zip(Sf, So)] if len(Sf) == len(So) else []
        Qc = [(l, (a, b)) for (l, a), (_, b) in zip(Qf, Qo)] if len(Qf) == len(Qo) else []
        if Sc and Qc:
            evaluate("combo" + tag, Sc, Qc, lambda q, s: float(q[0] @ s[0]) + float(q[1] @ s[1]))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--samples", required=True)
    ap.add_argument("--formula-onnx", default="D:/CyIME-Data/handwriting_training/latex-c-t/20261014/onnx")
    ap.add_argument("--app-model", default=str(och.APP_MODEL))
    ap.add_argument("--mathwriting", default="D:/CyIME-Data/datasets/mathwriting/converted")
    ap.add_argument("--aug", type=int, default=4)
    args = ap.parse_args()
    feats = Feats(args.formula_onnx, args.app_model)

    kana = load(args.samples, {"kana"})
    by = {}
    for l, st, t in sorted(kana, key=lambda r: r[2]):
        by.setdefault(l, []).append(st)
    # 方向一：第一遍当模板认第二遍；方向二：反过来
    for direction in (0, 1):
        support = [(l, v[direction] if len(v) > 1 else v[0]) for l, v in by.items()]
        query = [(l, v[1 - direction]) for l, v in by.items() if len(v) > 1]
        run_task(f"假名 1-shot（{'第一遍当模板' if direction == 0 else '第二遍当模板'}）", support, query, feats, args.aug)

    latin = load(args.samples, {"latin"})
    support = [(l, st) for l, st, _ in latin]
    letters = {l for l, _ in support}
    query = []
    with gzip.open(pathlib.Path(args.mathwriting) / "symbols.jsonl.gz", "rt", encoding="utf-8") as f:
        for line in f:
            s = json.loads(line)
            if s["label"] in letters:
                query.append((s["label"], [[(p[0], p[1]) for p in st] for st in s["strokes"]]))
    random.Random(0).shuffle(query)
    run_task("英文字母 1-shot（你的一遍当模板，认别人写的）", support, query[:600], feats, args.aug)
    return 0


if __name__ == "__main__":
    sys.exit(main())
