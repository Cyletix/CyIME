"""把 KanjiVG 的平假名、片假名笔画转成内置默认假名模板，并用自己写的假名样本测准确率。

    python kana_kanjivg.py --kanjivg D:/CyIME-Data/datasets/kanjivg-kana --out app/src/main/assets/handwriting/kana_default_strokes.json
    python kana_kanjivg.py --kanjivg ... --eval <训练模式导出.jsonl>      # 只测，不写文件

模板文件只含 KanjiVG 的笔画（按笔顺，每笔若干点，坐标在 0..109 的方格里）；
App 第一次打开日语手写时用公式模型把它们算成特征，用户自己的样本永远优先、不足时才用它补。
测试用的个人样本只在电脑上读，不写进任何文件。
"""
from __future__ import annotations

import argparse
import json
import pathlib
import random
import re
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

# 只收单个假名（不含 ゕゖ 等罕用小写、ヷ 等合成字以外的都保留，按 KanjiVG 实际有的文件为准）
KANA = [chr(c) for c in list(range(0x3041, 0x3097)) + list(range(0x30A1, 0x30FB))]

_NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_CMD = re.compile(r"([MmCcSsLlZzHhVvQqTt])([^MmCcSsLlZzHhVvQqTt]*)")


def _cubic(p0, p1, p2, p3, n):
    t = np.linspace(0, 1, n)[:, None]
    return ((1 - t) ** 3) * p0 + 3 * ((1 - t) ** 2) * t * p1 + 3 * (1 - t) * t * t * p2 + t ** 3 * p3


def path_points(d: str, step: float = 2.0) -> list[tuple[float, float]]:
    """解析 KanjiVG 的 path d（M/C/S/L 及小写相对形式），按弧长约 step 取点。"""
    cur = np.zeros(2)
    start = cur.copy()
    last_ctrl = None
    pts: list[np.ndarray] = []
    for cmd, args in _CMD.findall(d):
        nums = [float(x) for x in _NUM.findall(args)]
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            for i in range(0, len(nums), 2):
                p = np.array(nums[i:i + 2]) + (cur if rel else 0)
                cur = p
                if i == 0:
                    start = p.copy()
                pts.append(p.copy())
            last_ctrl = None
        elif c == "L":
            for i in range(0, len(nums), 2):
                p = np.array(nums[i:i + 2]) + (cur if rel else 0)
                pts.append(p.copy())
                cur = p
            last_ctrl = None
        elif c in ("C", "S"):
            k = 6 if c == "C" else 4
            for i in range(0, len(nums) - k + 1, k):
                v = np.array(nums[i:i + k]).reshape(-1, 2) + (cur if rel else 0)
                if c == "C":
                    c1, c2, end = v
                else:
                    c1 = 2 * cur - last_ctrl if last_ctrl is not None else cur.copy()
                    c2, end = v
                n = max(3, int(np.linalg.norm(end - cur) / step) + 2)
                pts.extend(_cubic(cur, c1, c2, end, n)[1:])
                last_ctrl = c2
                cur = end
        elif c == "Z":
            pts.append(start.copy())
            cur = start.copy()
    return [(round(float(x), 1), round(float(y), 1)) for x, y in pts]


def load_kanjivg(directory: pathlib.Path, wanted=None) -> dict[str, list[list[tuple[float, float]]]]:
    out = {}
    for ch in (wanted or KANA):
        f = directory / f"{ord(ch):05x}.svg"
        if not f.is_file():
            continue
        text = f.read_text(encoding="utf-8")
        # 只取笔画组（kvg:StrokePaths）里的 path，文档顺序即笔顺
        body = text.split("StrokeNumbers")[0]
        strokes = [path_points(d) for d in re.findall(r'<path[^>]*\sd="([^"]+)"', body)]
        strokes = [s for s in strokes if len(s) >= 2]
        if strokes:
            out[ch] = strokes
    return out


def evaluate(templates, samples_path: str, aug: int):
    import fewshot_eval as fe
    feats = fe.Feats("D:/CyIME-Data/handwriting_training/latex-c-t/20261014/onnx", str(fe.och.APP_MODEL))
    queries = [(l, st) for l, st, _ in fe.load(samples_path, {"kana"}) if l in templates]
    rnd = random.Random(0)
    support = [(l, st) for l, st in templates.items()]
    if aug:
        support += [(l, fe.augment(st, rnd)) for l, st in templates.items() for _ in range(aug)]
    S = [(l, feats.formula(st)) for l, st in support]
    S = [(l, v) for l, v in S if v is not None]
    labels = [l for l, _ in S]
    M = np.stack([v for _, v in S])
    top1 = top3 = n = 0
    miss = []
    for ql, st in queries:
        q = feats.formula(st)
        if q is None:
            continue
        order = np.argsort(-(M @ q))
        ranked = []
        for i in order:
            if labels[i] not in ranked:
                ranked.append(labels[i])
            if len(ranked) >= 3:
                break
        n += 1
        top1 += ranked[0] == ql
        top3 += ql in ranked
        if ranked[0] != ql:
            miss.append(f"{ql}→{''.join(ranked)}")
    print(f"模板 {len(templates)} 个字（增强×{aug}），测试 {n} 条：前1 {top1 / max(n, 1):.1%}  前3 {top3 / max(n, 1):.1%}")
    print("认错举例：", " ".join(miss[:40]))


def write_vectors(strokes_json: pathlib.Path, formula_dir: str):
    """用公式模型把模板文件里的每一份笔画预先算成特征（与 App 的 FormulaRecognizer.embed 相同的归一化向量），
    存成 float16 小端二进制 + 记录模型哈希与标签顺序的 json。App 发现模型哈希一致时直接用，不一致再现算。"""
    import hashlib
    import fewshot_eval as fe
    data = json.loads(strokes_json.read_text(encoding="utf-8"))
    feats = fe.Feats(formula_dir, str(fe.och.APP_MODEL))
    labels, rows = [], []
    for ch, variants in data["chars"].items():
        for st in variants:
            v = feats.formula([[tuple(p) for p in q] for q in st])
            if v is not None:
                labels.append(ch)
                rows.append(v.astype(np.float32))
    mat = np.stack(rows)
    encoder = pathlib.Path(formula_dir) / "formula_encoder.onnx"
    sha = hashlib.sha256(encoder.read_bytes()).hexdigest()
    base = strokes_json.with_name("kana_default_vectors")
    base.with_suffix(".bin").write_bytes(mat.astype("<f2").tobytes())
    base.with_suffix(".json").write_text(json.dumps(
        {"model_sha256": sha, "dim": int(mat.shape[1]), "labels": labels}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    print(f"预算特征 {mat.shape}，模型 {sha[:12]}…，写入 {base}.bin / .json")


def write_och_vectors(strokes_json: pathlib.Path, och_dir: str, top_n: int = 320, name: str = "kana_default_och"):
    """用内置汉字手写模型（ochwpro）的输出当假名特征：每份模板取概率最高的 top_n 类，值为开方后的概率并归一化。
    这样日语手写不需要公式模型。存成 [uint16 类序号, float16 值] × top_n 的小端二进制 + 记录模型哈希与标签顺序的 json。
    App 用同一模型且没启用个人模型时直接读，否则在设备上现算。"""
    import hashlib
    import fewshot_eval as fe
    data = json.loads(strokes_json.read_text(encoding="utf-8"))
    feats = fe.Feats("D:/CyIME-Data/handwriting_training/latex-c-t/20261014/onnx", och_dir)
    labels, out = [], bytearray()
    for ch, variants in data["chars"].items():
        for st in variants:
            strokes = [[tuple(p) for p in q] for q in st]
            x, m = fe.och.to_sequence(strokes)
            if not m.any():
                continue
            mm = m[None] if feats.och_bool else m[None].astype(np.uint8)
            lg = feats.och.run(None, {feats.och_in[0]: x[None], feats.och_in[1]: mm})[0][0]
            p = np.exp(lg - lg.max()); p /= p.sum()
            idx = np.argsort(-p)[:top_n]
            v = np.sqrt(p[idx]); v /= np.linalg.norm(v) + 1e-9
            rec = np.zeros(top_n, dtype=[("i", "<u2"), ("v", "<f2")])
            rec["i"], rec["v"] = idx, v
            out += rec.tobytes()
            labels.append(ch)
    sha = hashlib.sha256((pathlib.Path(och_dir) / "ochwpro.onnx").read_bytes()).hexdigest()
    base = strokes_json.with_name(name)
    base.with_suffix(".bin").write_bytes(bytes(out))
    base.with_suffix(".json").write_text(json.dumps(
        {"model_sha256": sha, "n": top_n, "labels": labels}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"汉字模型特征 {len(labels)} 份 × top{top_n}，模型 {sha[:12]}…，写入 {base}.bin / .json")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--kanjivg", required=True)
    ap.add_argument("--out")
    ap.add_argument("--eval")
    ap.add_argument("--aug", type=int, default=8)
    ap.add_argument("--vectors", help="公式模型 onnx 目录：给 --out 写出的模板预算特征")
    ap.add_argument("--och-vectors", help="内置汉字手写模型目录（ochwpro.onnx + char_index.json）：预算不靠公式模型的假名特征")
    ap.add_argument("--extra-kanji", help="japanese_kanji_map.py 输出的 json：把其中 extra（模型认不出的日本字）也做成模板")
    ap.add_argument("--kanjivg-kanji", help="这些日本字的 KanjiVG 笔画目录")
    ap.add_argument("--kanji-out", help="日本字模板单独写到这个 json（与假名模板分开），特征写到同目录 japanese_kanji_och.*")
    args = ap.parse_args()
    templates = load_kanjivg(pathlib.Path(args.kanjivg))
    print(f"KanjiVG 假名 {len(templates)} 个")
    if args.extra_kanji and args.kanjivg_kanji and args.kanji_out:
        # 模型认不出的日本字单独成一组模板，不混进假名模板
        extra = json.loads(pathlib.Path(args.extra_kanji).read_text(encoding="utf-8"))["extra"]
        kanji = load_kanjivg(pathlib.Path(args.kanjivg_kanji), extra)
        import finetune_ochwpro as och
        rnd = random.Random(0)
        variants = {ch: [st] + [[[(round(x, 1), round(y, 1)) for x, y in q] for q in och.augment(st, rnd)] for _ in range(args.aug)]
                    for ch, st in kanji.items()}
        kout = pathlib.Path(args.kanji_out)
        kout.write_text(json.dumps({"source": "KanjiVG", "canvas": 109, "chars": variants}, ensure_ascii=False,
                                   separators=(",", ":")), encoding="utf-8")
        print(f"KanjiVG 日本字 {len(kanji)} 个，写入 {kout}（{kout.stat().st_size // 1024} KB）")
        if args.och_vectors:
            write_och_vectors(kout, args.och_vectors, name="japanese_kanji_och")
        return 0
    if args.eval:
        evaluate(templates, args.eval, args.aug)
    if args.out:
        out = pathlib.Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        # 每个字：原笔画 + aug 份变体（和 --eval 同一随机种子与顺序，测出的准确率就是装进 App 的这一套）
        import finetune_ochwpro as och
        rnd = random.Random(0)
        variants = {ch: [st] for ch, st in templates.items()}
        for ch, st in templates.items():
            for _ in range(args.aug):
                variants[ch].append([[(round(x, 1), round(y, 1)) for x, y in q] for q in och.augment(st, rnd)])
        data = {"source": "KanjiVG", "canvas": 109, "chars": variants}
        out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(f"写入 {out}（{out.stat().st_size // 1024} KB）")
        if args.vectors:
            write_vectors(out, args.vectors)
        if args.och_vectors:
            write_och_vectors(out, args.och_vectors)
    return 0


if __name__ == "__main__":
    sys.exit(main())
