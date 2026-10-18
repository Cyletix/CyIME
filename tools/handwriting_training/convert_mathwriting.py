"""把 Google MathWriting 压缩包直接转成按数据划分的 jsonl.gz，不解开 65 万个小文件。

用法：
    python tools/handwriting_training/convert_mathwriting.py \
        D:/CyIME-Data/datasets/mathwriting/mathwriting-2024.tgz \
        D:/CyIME-Data/datasets/mathwriting/converted

每行一个样本：{"id","split","label","creation","strokes":[[[x,y,t],…],…]}。
label 用 normalizedLabel（symbols 没有，用 label）；坐标保留两位小数，未归一化，
和训练模式导出的样本一样是“y 向下的像素坐标 + 毫秒”。最后打印每个划分的条数、点数分布和标签长度。
数据许可 CC BY-NC-SA 4.0：只在本机实验用，训出的模型不能放进公开发布。
"""
from __future__ import annotations

import gzip
import json
import pathlib
import statistics
import sys
import tarfile
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2003/InkML}"


def parse(data: bytes) -> dict:
    root = ET.fromstring(data)
    ann = {a.get("type"): (a.text or "") for a in root.iter(NS + "annotation")}
    strokes = []
    for trace in root.iter(NS + "trace"):
        pts = []
        for item in (trace.text or "").split(","):
            v = item.split()
            if len(v) >= 2:
                pts.append([round(float(v[0]), 2), round(float(v[1]), 2), round(float(v[2]), 1) if len(v) > 2 else 0.0])
        if pts:
            strokes.append(pts)
    return {
        "id": ann.get("sampleId", ""),
        "split": ann.get("splitTagOriginal", ""),
        "label": ann.get("normalizedLabel") or ann.get("label", ""),
        "creation": ann.get("inkCreationMethod", ""),
        "strokes": strokes,
    }


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    writers: dict[str, gzip.GzipFile] = {}
    stats: dict[str, dict[str, list[int]]] = {}
    with tarfile.open(src, "r|gz") as tar:
        for member in tar:
            if not member.isfile() or not member.name.endswith(".inkml"):
                continue
            split = pathlib.PurePosixPath(member.name).parent.name
            f = tar.extractfile(member)
            if f is None:
                continue
            s = parse(f.read())
            if not s["strokes"] or not s["label"]:
                continue
            s["split"] = split
            if split not in writers:
                writers[split] = gzip.open(out / f"{split}.jsonl.gz", "wt", encoding="utf-8", compresslevel=6)
                stats[split] = {"points": [], "strokes": [], "label": []}
            writers[split].write(json.dumps(s, ensure_ascii=False, separators=(",", ":")) + "\n")
            st = stats[split]
            st["points"].append(sum(len(x) for x in s["strokes"]))
            st["strokes"].append(len(s["strokes"]))
            st["label"].append(len(s["label"]))
            n = sum(len(v["label"]) for v in stats.values())
            if n % 50000 == 0:
                print(f"已转换 {n} 条", flush=True)
    for w in writers.values():
        w.close()
    for split, st in sorted(stats.items()):
        def q(xs: list[int]) -> str:
            xs = sorted(xs)
            return f"中位 {statistics.median(xs):.0f} / 95% {xs[int(len(xs) * 0.95)]} / 最大 {xs[-1]}"
        print(f"{split}: {len(st['label'])} 条；点数 {q(st['points'])}；笔画 {q(st['strokes'])}；标签长度 {q(st['label'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
