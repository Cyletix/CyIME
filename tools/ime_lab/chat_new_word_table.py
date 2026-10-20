"""Turn chat_new_words.py candidates into dictionary rows: text, pinyin, weight on the base.dict scale.

Kept: count >= --min-count, cohesion (PMI per link) >= --min-pmi, both neighbour entropies >= --min-entropy,
every character in the 8105 table (drops 馀/羨 variant spellings), and not a kept word plus a particle at
either end (的孩纸, 更博了). Weight = chat count x (base.dict total / chat word-count total), the scale
chat_base_weights.py gives counted chat words. Pinyin from pypinyin with phrase context.

Usage: python chat_new_word_table.py CANDIDATES.tsv RIME_ICE_DIR COUNTS.tsv OUTPUT.tsv
"""
import argparse
from pathlib import Path

from pypinyin import Style, lazy_pinyin

PARTICLES = set("的了啊呀吧呢哦嘛哈么吗啦呗嘞哟喔噢")


def table(path):
    body = False
    for line in Path(path).open(encoding="utf-8"):
        if line.strip() == "...":
            body = True
            continue
        if body and line and not line.startswith("#"):
            yield line.rstrip("\n").split("\t")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("candidates")
    parser.add_argument("root", type=Path)
    parser.add_argument("counts")
    parser.add_argument("output")
    parser.add_argument("--min-count", type=int, default=20)
    parser.add_argument("--min-pmi", type=float, default=3.0)
    parser.add_argument("--min-entropy", type=float, default=1.0)
    args = parser.parse_args()
    chars = {f[0] for f in table(args.root / "cn_dicts/8105.dict.yaml") if len(f[0]) == 1}
    base_total = sum(float(f[2]) for f in table(args.root / "cn_dicts/base.dict.yaml") if len(f) >= 3 and f[2])
    chat_total = sum(int(line.split("\t")[1]) for line in open(args.counts, encoding="utf-8"))
    scale = base_total / chat_total
    kept = {}
    for line in open(args.candidates, encoding="utf-8"):
        word, count, pmi, left, right = line.rstrip("\n").split("\t")
        if (int(count) >= args.min_count and float(pmi) >= args.min_pmi and float(left) >= args.min_entropy
                and float(right) >= args.min_entropy and all(c in chars for c in word)):
            kept[word] = int(count)
    # A spelling the dictionary already has (恩恩 for 嗯嗯, 我勒个去 for 我嘞个去, 猴猴 for 吼吼) is a variant,
    # not a gap: adding it only splits that reading between two spellings.
    codes = {f[1] for name in ("base", "ext", "others") for f in table(args.root / f"cn_dicts/{name}.dict.yaml")
             if len(f) >= 2 and len(f[0]) >= 2}
    rows = []
    for word, count in kept.items():
        if (word[0] in PARTICLES and word[1:] in kept) or (word[-1] in PARTICLES and word[:-1] in kept):
            continue
        code = " ".join(lazy_pinyin(word, style=Style.NORMAL))
        if code in codes:
            continue
        rows.append((count, f"{word}\t{code}\t{max(1, round(count * scale))}\n"))
    rows.sort(key=lambda r: -r[0])
    Path(args.output).write_text("".join(r for _, r in rows), encoding="utf-8", newline="\n")
    print(len(rows), "words; scale", round(scale, 1))


if __name__ == "__main__":
    main()
