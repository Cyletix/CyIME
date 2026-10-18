"""生成训练模式的“中文汉字”题库，用来收集内置中文手写模型（ochwpro）微调样本。

用法：
    python tools/handwriting_training/extract_chinese.py D:/CyIME-Data/handwriting_training/banks/chinese_chars.tsv

收录内置手写模型字表里的全部汉字，按使用频率从高到低一个一个出题（模型字表外的字认不出来，
写了也没法拿来微调，所以不收）。频率取自 App 内置拼音词库 pinyin_simp.dict.yaml 的单字权重，
多音字各读音权重相加。训练模式写的时候会当场识别，认错的字要一直写到认对为止。
输出 TSV 每行：`类别\t长题(0/1)\t题目\t提示\t来源`。
"""
from __future__ import annotations

import argparse
import collections
import json
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
DICT = REPO / "app/src/main/assets/rime/pinyin_simp.dict.yaml"
VOCAB = REPO / ".gradle/bundled-model-cache/ochwpro/char_index.json"


def is_hanzi(ch: str) -> bool:
    return len(ch) == 1 and ("\u4e00" <= ch <= "\u9fff" or "\u3400" <= ch <= "\u4dbf")


def char_weights(dict_path: pathlib.Path) -> collections.Counter[str]:
    weights: collections.Counter[str] = collections.Counter()
    body = False
    with dict_path.open(encoding="utf-8") as f:
        for line in f:
            if line.startswith("..."):
                body = True
                continue
            if not body or line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            if len(cols) >= 3 and is_hanzi(cols[0]) and cols[2].strip().isdigit():
                weights[cols[0]] += int(cols[2])
    return weights


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out")
    ap.add_argument("--dict", default=str(DICT), help="带单字权重的 Rime 词库")
    ap.add_argument("--vocab", default=str(VOCAB), help="内置手写模型的 char_index.json")
    args = ap.parse_args()

    vocab = {c for c in json.loads(pathlib.Path(args.vocab).read_text(encoding="utf-8-sig"))["chars"] if is_hanzi(c)}
    weights = char_weights(pathlib.Path(args.dict))
    # 词库里有权重的按权重排，没出现过的生僻字放最后（按码位，保证每次生成顺序一样）
    ranked = sorted(vocab, key=lambda c: (-weights.get(c, 0), c))

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for rank, ch in enumerate(ranked, 1):
            f.write(f"char\t0\t{ch}\t常用度第 {rank}\tfreq:pinyin_simp\n")
    print(f"{out}: {len(ranked)} 题（{sum(1 for c in ranked if weights.get(c))} 个有词频）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
