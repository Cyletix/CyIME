"""Build the 26-key "initials only" test: common 2-4 character words typed as their first letters.

Candidates are rime-ice base.dict.yaml words of 2-4 characters with one full reading. Each is scored by the
T9 word n-gram model (public-corpus model, never private chat text) with tools/ime_lab/ext_weights.py's
score; for every initials code (one letter per syllable, zh/ch/sh -> z/c/s, as people type "zdky") the
expected first candidate is the corpus' most likely word. Codes whose best word is not clearly ahead of the
runner-up (less than `--margin` nats) are left out: the corpus itself cannot say what a person meant.

Usage: python initials_phrases.py MODEL RIME_ICE_DIR OUTPUT.tsv [--per-length 150] [--margin 0.7]
Output rows use the lexicon_regression.tsv columns.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ext_weights  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("root")
    parser.add_argument("output")
    parser.add_argument("--per-length", type=int, default=150)
    parser.add_argument("--margin", type=float, default=0.7)
    args = parser.parse_args()
    model = ext_weights.FastModel(args.model)
    cache = {}
    best = {}
    for text, code, _ in ext_weights.entries(Path(args.root) / "cn_dicts" / "base.dict.yaml"):
        syllables = code.split()
        if not 2 <= len(text) <= 4 or len(syllables) != len(text) or not all("一" <= c <= "鿿" for c in text):
            continue
        initials = "".join(s[0] for s in syllables)
        value = ext_weights.score(model, text, cache)
        ranked = best.setdefault(initials, [])
        if not any(t == text for _, t in ranked):
            ranked.append((value, text))
            ranked.sort(reverse=True)
            del ranked[2:]
    rows = []
    for length in (2, 3, 4):
        clear = [(v[0][0], code, v[0][1], v[0][0] - (v[1][0] if len(v) > 1 else -99))
                 for code, v in best.items() if len(code) == length]
        clear = [c for c in clear if c[3] >= args.margin]
        clear.sort(reverse=True)
        for value, code, text, margin in clear[:args.per_length]:
            rows.append(f"init{length}-{code}\t26\t{code}\t{text}\t1\tcorpus-initials\t2026-10-18\t"
                        f"lnP {value:.2f}, ahead by {margin:.2f}\n")
    Path(args.output).write_text("".join(rows), encoding="utf-8", newline="\n")
    print(f"{len(rows)} rows", file=sys.stderr)


if __name__ == "__main__":
    main()
