"""读取 CyIME 手写训练模式导出的 JSONL 样本。

用法：
    python tools/handwriting_training/read_samples.py <导出文件.jsonl>          # 打印统计
    python tools/handwriting_training/read_samples.py <导出文件.jsonl> --show 3  # 再打印前 3 条的特征

每行一个样本，字段见 app/.../handwriting/training/TrainingSampleStore.kt 的 TrainingSampleCodec。
to_sequence() 按 HandwritingInference.strokesToSequence 的算法把原始点转成
ochwpro 输入 (x_norm, y_norm, dx, dy, pen_down)：整段笔迹按 bounding box 归一化，
每笔最后一个点 pen_down=0。
"""
from __future__ import annotations

import argparse
import collections
import json
import sys
from typing import Iterator

FORMAT = "cyime-handwriting-training"


def iter_samples(path: str) -> Iterator[dict]:
    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            sample = json.loads(line)
            if sample.get("format") != FORMAT:
                raise ValueError(f"第 {line_no} 行不是 {FORMAT} 格式")
            yield sample


def to_sequence(sample: dict, keep_aspect: bool = False) -> list[tuple[float, float, float, float, int]]:
    """原始点 → (x_norm, y_norm, dx, dy, pen_down)。

    默认与输入法推理一致：平移到左上角，横竖各自缩放到 0~1（不保持长宽比，适合单字）。
    keep_aspect=True 时横竖用同一个比例（按较长的一边缩放），公式、长句的宽高比和上下标的相对高度不会被压扁，
    训练公式模型时建议用它。旋转不做处理，轻微倾斜和大小变化留给模型去学，或在训练时做数据增强。
    """
    points: list[tuple[float, float]] = []
    pen: list[int] = []
    for stroke in sample["strokes"]:
        pts = stroke["points"]
        for x, y, _t in pts:
            points.append((x, y))
            pen.append(1)
        if pts:
            pen[-1] = 0
    if not points:
        return []
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    min_x, min_y = min(xs), min(ys)
    range_x = max(max(xs) - min_x, 1.0)
    range_y = max(max(ys) - min_y, 1.0)
    if keep_aspect:
        range_x = range_y = max(range_x, range_y)
    seq = []
    for i, (x, y) in enumerate(points):
        dx = 0.0 if i == 0 else (x - points[i - 1][0]) / range_x
        dy = 0.0 if i == 0 else (y - points[i - 1][1]) / range_y
        seq.append(((x - min_x) / range_x, (y - min_y) / range_y, dx, dy, pen[i]))
    return seq


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("path")
    parser.add_argument("--show", type=int, default=0, help="打印前 N 条样本的序列摘要")
    args = parser.parse_args()

    by_type: collections.Counter[str] = collections.Counter()
    labels: collections.Counter[str] = collections.Counter()
    shown = 0
    total = 0
    for sample in iter_samples(args.path):
        total += 1
        by_type[sample["type"]] += 1
        labels[sample["label"]] += 1
        if shown < args.show:
            seq = to_sequence(sample)
            strokes = sample["strokes"]
            duration = strokes[-1]["points"][-1][2] if strokes and strokes[-1]["points"] else 0
            print(f"[{sample['type']}] {sample['label']!r}  笔画 {len(strokes)}  点 {len(seq)}  用时 {duration} ms")
            for row in seq[:3]:
                print("   ", tuple(round(v, 3) for v in row))
            shown += 1
    print(f"样本 {total} 条；按类型 {dict(by_type)}；不同题目 {len(labels)} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
