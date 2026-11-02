"""生成中文手写的生僻字模板：内置手写模型（ochwpro）字表里没有的字，用笔顺模板补上。

内置模型只认 7185 个汉字（CASIA 单字库的字表，GB2312 全部再多一点），通用规范汉字表缺 1126 个、
扩展A 一个没有，拼音拼不出、只能手写的生僻字反而写不出来。这里不重训模型，和日语字表外的漢字一样：
给每个生僻字做笔画模板，写的时候把模型输出当特征和模板比（见 HandwritingInference.RareTemplates）。

    python tools/handwriting_training/build_rare_hanzi.py --data <下载目录> --model <ochwpro 目录>

--data 下需要（都是公开数据，脚本不联网）：
    makemeahanzi/graphics.txt、makemeahanzi/dictionary.txt   https://github.com/skishore/makemeahanzi
    animcjk/graphicsZhHans.txt、graphicsJa.txt、graphicsZhHant.txt、graphicsKo.txt   https://github.com/parsimonhi/animCJK
    cjkvi-ids/ids.txt                                         https://github.com/cjkvi/cjkvi-ids
    opencc/TSCharacters.txt                                   https://github.com/BYVoid/OpenCC
    rime-ice/8105.dict.yaml、rime-ice/41448.dict.yaml          https://github.com/iDvel/rime-ice（cn_dicts）
--model 下需要 ochwpro.onnx 与 char_index.json（与 App 内置同一份）。依赖 numpy、onnxruntime。

收哪些字：通用规范 8105 与 rime-ice 41448 表里、基本平面（含扩展A）的汉字，去掉模型字表里已有的，
再去掉表外的繁体写法（OpenCC 繁→简后的字模型已有，比如 說：照旧出简体；通用规范表里的字都收）。

笔画从哪来：Make Me a Hanzi、AnimCJK 有笔顺的字直接用（每字 1 个模板）；没有的按 IDS 结构
（⿰⿱⿸…）用部件拼：版面和部件在这个位置的写法取自 Make Me a Hanzi 里同结构的字（女在左边是窄的女），
找不到时用部件本身缩放进格子。拼出来的字做 3 个模板（换不同的参照字）。

特征与 App 一致：笔画每笔最多 8 点 → 模型概率 → 概率最高的 320 类开方归一化；模板只存最大的 32 项
（类序号 uint16 + 数值 uint8），第一项就是模板的首位类，App 按它分桶，查询时只比首位类落在查询前 32 类里的模板。

输出（app/src/main/assets/handwriting/）：
    hanzi_rare_och.bin   每个模板 2 + 32×3 字节：字序号 uint16、32 组（类序号 uint16、数值×255 uint8），小端
    hanzi_rare_och.json  {"model_sha256", "n": 32, "count", "labels": 所有字连成的字符串}
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
import re
import struct
import sys

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "app/src/main/assets/handwriting"
FIXED_LEN = 200
MAX_POINTS_PER_STROKE = 8
QUERY_TOP = 320
KEEP = 32
VARIANTS = 3

IDC2 = set("⿰⿱⿴⿵⿶⿷⿸⿹⿺⿻")
IDC3 = set("⿲⿳")


# ── 数据 ──────────────────────────────────────────────────────────────

def load_graphics(path: pathlib.Path) -> dict[str, list]:
    """Make Me a Hanzi / AnimCJK 的 graphics：medians 在 1024 格里、y 向上，翻成 y 向下（与触屏一致）。"""
    out = {}
    for line in path.open(encoding="utf-8"):
        d = json.loads(line)
        c = d["character"]
        if len(c) == 1 and d.get("medians"):
            out[c] = [[(float(x), 900.0 - float(y)) for x, y in m] for m in d["medians"]]
    return out


def parse_ids(s: str, i: int = 0):
    c = s[i]
    if c in IDC3:
        a, i = parse_ids(s, i + 1)
        b, i = parse_ids(s, i)
        d, i = parse_ids(s, i)
        return (c, [a, b, d]), i
    if c in IDC2:
        a, i = parse_ids(s, i + 1)
        b, i = parse_ids(s, i)
        return (c, [a, b]), i
    return c, i + 1


def tree_str(t) -> str:
    return t if isinstance(t, str) else t[0] + "".join(tree_str(x) for x in t[1])


def load_ids(path: pathlib.Path) -> dict:
    """cjkvi-ids：同一字有几种写法时取标了大陆（G）的。"""
    ids = {}
    for line in path.open(encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) < 3:
            continue
        best = None
        for s in p[2:]:
            tag = re.search(r"\[([A-Z]+)\]", s)
            core = re.sub(r"\[[A-Z]+\]", "", s)
            if best is None or (tag and "G" in tag.group(1)):
                best = core
            if tag and "G" in tag.group(1):
                break
        if best and best != p[1] and best[0] in IDC2 | IDC3:
            try:
                t, n = parse_ids(best)
            except IndexError:
                continue
            if n == len(best):
                ids[p[1]] = t
    return ids


def load_rime_chars(path: pathlib.Path) -> set[str]:
    chars, body = set(), False
    for line in path.open(encoding="utf-8"):
        if line.startswith("..."):
            body = True
            continue
        if body and line.strip() and not line.startswith("#"):
            c = line.split("\t")[0]
            if len(c) == 1:
                chars.add(c)
    return chars


def load_t2s(path: pathlib.Path) -> dict[str, list[str]]:
    out = {}
    for line in path.open(encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if len(p) == 2:
            out[p[0]] = p[1].split()
    return out


# ── 部件拼字 ──────────────────────────────────────────────────────────

def bbox(strokes):
    xs = [p[0] for s in strokes for p in s]
    ys = [p[1] for s in strokes for p in s]
    return min(xs), min(ys), max(xs), max(ys)


def fit(strokes, box):
    x0, y0, x1, y1 = bbox(strokes)
    w, h = max(x1 - x0, 1.0), max(y1 - y0, 1.0)
    bx0, by0, bx1, by1 = box
    sx, sy = (bx1 - bx0) / w, (by1 - by0) / h
    # 一、丨这类很细的部件保持细，不拉满格子
    if w < 0.15 * (bx1 - bx0):
        sx = 1.0
    if h < 0.15 * (by1 - by0):
        sy = 1.0
    cx, cy, ox, oy = (bx0 + bx1) / 2, (by0 + by1) / 2, (x0 + x1) / 2, (y0 + y1) / 2
    return [[(cx + (x - ox) * sx, cy + (y - oy) * sy) for x, y in s] for s in strokes]


DEFAULT_BOX = {
    "⿰": [(0, 0, 400, 1000), (450, 0, 1000, 1000)],
    "⿱": [(0, 0, 1000, 420), (0, 470, 1000, 1000)],
    "⿲": [(0, 0, 300, 1000), (330, 0, 660, 1000), (700, 0, 1000, 1000)],
    "⿳": [(0, 0, 1000, 300), (0, 330, 1000, 660), (0, 700, 1000, 1000)],
}


class Donors:
    """Make Me a Hanzi 的结构拆分 + 每笔属于哪个部件：某个部件在某种结构的某个位置长什么样、占哪块。"""

    def __init__(self, dictionary: pathlib.Path, mmah: dict):
        self.slot: dict[tuple, list] = {}
        self.layout: dict[str, list] = {}
        for line in dictionary.open(encoding="utf-8"):
            d = json.loads(line)
            c, dec = d["character"], d.get("decomposition") or ""
            if c not in mmah or not dec or dec[0] not in IDC2 | IDC3 or "？" in dec:
                continue
            try:
                t, n = parse_ids(dec)
            except IndexError:
                continue
            if n != len(dec):
                continue
            op, kids = t
            strokes, matches = mmah[c], d.get("matches") or []
            if len(matches) != len(strokes) or any(not m for m in matches):
                continue
            groups, first = [[] for _ in kids], [10 ** 9] * len(kids)
            ok = True
            for i, path in enumerate(matches):
                if path[0] >= len(kids):
                    ok = False
                    break
                groups[path[0]].append(strokes[i])
                first[path[0]] = min(first[path[0]], i)
            if not ok or any(not g for g in groups):
                continue
            names = [tree_str(k) for k in kids]
            boxes = [bbox(g) for g in groups]
            order = sorted(range(len(kids)), key=lambda k: first[k])
            for k, name in enumerate(names):
                self.slot.setdefault((op, k, name), []).append((groups[k], c))
            self.layout.setdefault(op, []).append((boxes, order, names, c))

    def best_layout(self, op, names, exclude, variant):
        scored = [(sum(a == b for a, b in zip(lay[2], names)), lay) for lay in self.layout.get(op, []) if lay[3] != exclude]
        if not scored:
            return None, -1
        top = max(s for s, _ in scored)
        best = [lay for s, lay in scored if s == top]
        return best[variant % len(best)], top


class Composer:
    def __init__(self, sources: list[dict], ids: dict, donors: Donors):
        self.sources, self.ids, self.donors = sources, ids, donors
        self.variant = 0

    def real(self, c, exclude=None):
        if c == exclude:
            return None
        for s in self.sources:
            if c in s:
                return s[c]
        return None

    def strokes(self, node, exclude, depth):
        if isinstance(node, str):
            r = self.real(node, exclude)
            if r is not None:
                return r
            t = self.ids.get(node)
            if t is None or depth > 6:
                return None
            return self.compose(t, exclude, depth + 1)
        return self.compose(node, exclude, depth + 1)

    def compose(self, t, exclude=None, depth=0):
        op, kids = t
        names = [tree_str(k) for k in kids]
        lay, score = self.donors.best_layout(op, names, exclude, self.variant if depth <= 1 else 0)
        if lay is not None and score > 0:
            boxes, order = lay[0], lay[1]
        elif op in DEFAULT_BOX:
            boxes, order = DEFAULT_BOX[op], list(range(len(kids)))
        elif lay is not None:
            boxes, order = lay[0], lay[1]
        else:
            return None
        parts = []
        for k, kid in enumerate(kids):
            inst = [x for x in self.donors.slot.get((op, k, names[k]), []) if x[1] != exclude]
            if inst:
                g = inst[(self.variant * 7) % len(inst) if depth <= 1 else 0][0]
            else:
                g = self.strokes(kid, exclude, depth)
                if g is None:
                    return None
            parts.append(fit(g, boxes[k]))
        return [s for k in order for s in parts[k]]


# ── 与 App 一致的特征 ─────────────────────────────────────────────────

def densify(stroke, step=12.0):
    out = [stroke[0]]
    for a, b in zip(stroke, stroke[1:]):
        n = max(1, int(math.dist(a, b) / step))
        out += [(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n) for k in range(1, n + 1)]
    return out


def simplify(stroke):
    if len(stroke) <= MAX_POINTS_PER_STROKE:
        return stroke
    step = (len(stroke) - 1) / (MAX_POINTS_PER_STROKE - 1)
    return [stroke[min(max(int(round(i * step)), 0), len(stroke) - 1)] for i in range(MAX_POINTS_PER_STROKE)]


def to_sequence(strokes):
    pts, pen = [], []
    for st in strokes:
        for p in st:
            pts.append(p)
            pen.append(1.0)
        if pen:
            pen[-1] = 0.0
    a = np.asarray(pts, np.float32)
    mn = a.min(0)
    rng = np.maximum(a.max(0) - mn, 1.0)
    d = np.diff(a, axis=0, prepend=a[:1])
    t = min(len(a), FIXED_LEN)
    seq = np.zeros((FIXED_LEN, 5), np.float32)
    mask = np.zeros(FIXED_LEN, np.bool_)
    seq[:t, :2] = ((a - mn) / rng)[:t]
    seq[:t, 2:4] = (d / rng)[:t]
    seq[:t, 4] = np.asarray(pen, np.float32)[:t]
    mask[:t] = True
    return seq, mask


def probabilities(session, all_strokes):
    out = []
    for i in range(0, len(all_strokes), 256):
        batch = [to_sequence([simplify(densify(s)) for s in strokes]) for strokes in all_strokes[i:i + 256]]
        logits = session.run(None, {"input": np.stack([b[0] for b in batch]), "mask": np.stack([b[1] for b in batch])})[0]
        p = np.exp(logits - logits.max(1, keepdims=True))
        out.append(p / p.sum(1, keepdims=True))
        print(f"\r特征 {min(i + 256, len(all_strokes))}/{len(all_strokes)}", end="", file=sys.stderr)
    print(file=sys.stderr)
    return np.concatenate(out)


def template_entries(p: np.ndarray):
    """查询特征（前 320 类开方归一化）里最大的 32 项，按数值从大到小。"""
    top = np.argsort(-p)[:QUERY_TOP]
    v = np.sqrt(p[top])
    v /= np.linalg.norm(v)
    return [(int(top[i]), float(v[i])) for i in range(KEEP)]


# ── 主流程 ────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, type=pathlib.Path)
    ap.add_argument("--model", required=True, type=pathlib.Path)
    ap.add_argument("--out", default=OUT_DIR, type=pathlib.Path)
    args = ap.parse_args()
    import onnxruntime as ort

    d = args.data
    chars = json.loads((args.model / "char_index.json").read_text(encoding="utf-8"))["chars"]
    in_model = set(chars)
    mmah = load_graphics(d / "makemeahanzi/graphics.txt")
    sources = [mmah] + [load_graphics(d / f"animcjk/graphics{n}.txt") for n in ("ZhHans", "Ja", "ZhHant", "Ko")]
    ids = load_ids(d / "cjkvi-ids/ids.txt")
    t2s = load_t2s(d / "opencc/TSCharacters.txt")
    composer = Composer(sources, ids, Donors(d / "makemeahanzi/dictionary.txt", mmah))

    standard = load_rime_chars(d / "rime-ice/8105.dict.yaml")
    wanted = standard | load_rime_chars(d / "rime-ice/41448.dict.yaml")
    # 通用规范表里的字都收（犇 在 OpenCC 里算"奔"的异体，但它是规范字）；表外的繁体写法不收
    pool = sorted(
        c for c in wanted
        if ("㐀" <= c <= "䶿" or "一" <= c <= "鿿") and c not in in_model
        and (c in standard or not any(s in in_model for s in t2s.get(c, [])))
    )
    rows, row_labels = [], []
    real = 0
    for c in pool:
        r = composer.real(c)
        if r is not None:
            rows.append(r)
            row_labels.append(c)
            real += 1
    for v in range(VARIANTS):
        composer.variant = v
        for c in pool:
            if composer.real(c) is not None or not isinstance(ids.get(c), tuple):
                continue
            s = composer.compose(ids[c], exclude=c)
            if s:
                rows.append(s)
                row_labels.append(c)
    labels = sorted(set(row_labels))
    print(f"候选生僻字 {len(pool)}，有模板 {len(labels)}（真笔顺 {real}），模板 {len(rows)}", file=sys.stderr)

    session = ort.InferenceSession(str(args.model / "ochwpro.onnx"))
    probs = probabilities(session, rows)
    index = {c: i for i, c in enumerate(labels)}
    args.out.mkdir(parents=True, exist_ok=True)
    with (args.out / "hanzi_rare_och.bin").open("wb") as f:
        for c, p in zip(row_labels, probs):
            f.write(struct.pack("<H", index[c]))
            for cls, val in template_entries(p):
                f.write(struct.pack("<HB", cls, min(255, max(0, round(val * 255)))))
    sha = hashlib.sha256((args.model / "ochwpro.onnx").read_bytes()).hexdigest()
    meta = {"model_sha256": sha, "n": KEEP, "count": len(rows), "labels": "".join(labels)}
    (args.out / "hanzi_rare_och.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    print(f"写入 {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
