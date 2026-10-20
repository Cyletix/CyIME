"""Consistent weights for "word + 了" entries (嫌弃了, 恢复了) in rime-ice base.dict.yaml.

rime-ice lists 掀起了 and 恢复了 but not 嫌弃了, and 回复了 at a tenth of 恢复了 although 回复 is three times 恢复.
In a sentence the listed X了 is one word against X + 了 as two, so 被嫌弃了 came out as 被掀起了. Every X了 is
estimated from its word X instead: weight(X了) = weight(X) * P(了 follows X), with P from the T9 word n-gram model
(public LCCC): the model token X了 plus the bigram X → 了, over P(X). Existing X了 entries get that weight; base
words with P >= --floor get an X了 entry. Output: gzip TSV text, code, weight, read by prepareChineseDictionaries.

Usage: python le_weights.py MODEL RIME_ICE_DIR OUTPUT.tsv.gz [--floor 0.02]
"""
import argparse
import gzip
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ext_weights import FastModel, entries  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("root", type=Path)
    parser.add_argument("output")
    parser.add_argument("--floor", type=float, default=0.02)
    args = parser.parse_args()
    model = FastModel(args.model)
    le = model.id("了")
    base = {}
    for text, code, weight in entries(args.root / "cn_dicts/base.dict.yaml"):
        if code is not None and weight is not None:
            base.setdefault((text, code), weight)

    def share(word):
        ident = model.id(word)
        if ident is None:
            return None
        whole = model.id(word + "了")
        p = math.exp(model.ml(ident) + model.logp(ident, le))
        if whole is not None:
            p += math.exp(model.ml(whole))
        return p / math.exp(model.ml(ident))

    rows, added, updated = [], 0, 0
    for (text, code), weight in base.items():
        if text.endswith("了") or len(text) < 2 or weight <= 0:
            continue
        p = share(text)
        if p is None:
            continue
        key = (text + "了", code + " le")
        if key in base:
            updated += 1
        elif p >= args.floor:
            added += 1
        else:
            continue
        rows.append(f"{key[0]}\t{key[1]}\t{max(1, round(weight * p))}\n")
    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n", compresslevel=9) as out:
        out.write("".join(sorted(rows)))
    print(f"X了 entries: {updated} re-estimated, {added} added", file=sys.stderr)


if __name__ == "__main__":
    main()
