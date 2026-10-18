"""Mix two spoken-word count sources geometrically before chat_base_weights.py.

LCCC chat counts alone (8M lines, chat_word_counts.py) are thin for near-homophones: 童话 179 vs 通话 161
made the pair a coin flip. The T9 word model (LCCC-base + subtitles) has much larger counts, but used alone it
lowered spoken-initials scores. A geometric mix count = lccc^(1-g) * model^g keeps the chat signal and lets
the larger corpus break near-ties; g=0.25 was chosen on the tuning sample (spoken groups unchanged).

Model counts are exp(unigram ml) * N for every rime-ice word the model knows (needs tools/ime_lab/ngram).

Usage: python mix_chat_counts.py LCCC_COUNTS.tsv MODEL.lm RIME_ICE_DIR OUTPUT.tsv [--gamma 0.25]
"""
import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "ngram"))
from ngram_query import Model  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("lccc")
    parser.add_argument("model")
    parser.add_argument("rime_ice")
    parser.add_argument("output")
    parser.add_argument("--gamma", type=float, default=0.25)
    args = parser.parse_args()
    lccc = {}
    for line in open(args.lccc, encoding="utf-8"):
        word, count = line.rstrip("\n").split("\t")
        lccc[word] = int(count)
    total = sum(lccc.values())
    model = Model(args.model)
    words = set()
    for path in Path(args.rime_ice, "cn_dicts").glob("*.dict.yaml"):
        body = False
        for line in path.open(encoding="utf-8"):
            if line.strip() == "...":
                body = True
                continue
            fields = line.rstrip("\n").split("\t")
            if body and not line.startswith("#") and len(fields) >= 3:
                words.add(fields[0])
    g = args.gamma
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        for word in sorted(words | set(lccc)):
            ident = model.id(word) if word in words else None
            model_count = math.exp(model.ml(ident)) * total if ident is not None else 0.0
            count = math.exp((1 - g) * math.log(max(lccc.get(word, 0), 0.5)) + g * math.log(max(model_count, 0.5)))
            if count >= 1:
                out.write(f"{word}\t{round(count)}\n")


if __name__ == "__main__":
    main()
