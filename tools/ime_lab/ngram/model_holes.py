"""Raise the unigram of words a filtered training corpus could not show (T9 word model, version 2 or compact 3).

The nine-key word models were counted from text that includes LCCC, whose blacklist removed every line holding
certain characters (tools/ime_lab/corpus_holes.py lists them: 轮, 靠, 灾, 税, ...). Words holding them might have
too little unigram mass (靠谱 sits at ln P -13.5, below 老去 -11.6; 52678 ranks it 8th). This writes a copy
of a model where each such word's unigram (lower and ml, same shift) is raised, never lowered, to what a typical
word of the same upstream dictionary weight has in this model: ln(upstream share) + a quantile of
(model lower - ln(upstream share)) over the words the corpus does cover. Bigrams, trigrams and backoffs are
unchanged, and single characters in the keep list (profane or sexual single uses) keep their unigram.

Measured 2026-10-21 on the public v3 model (q 0.25): 5773 of 16383 hole words raised (轮 -11.79 → -10.37, but 靠谱
-13.47 → -13.36), and the nine-key host replay ranked 轮, 一轮, 靠谱, 灾难, 交税, 靠, 爽 exactly as before. Not shipped:
the model is not starved by the filter (hole words sit as close to their upstream weights as other words do); 靠谱
and 一轮 rank low because rime-ice's written-language weights are low, which only chat counts for those words can
fix (a retrain on an unfiltered corpus). Kept so the experiment is not repeated.

Usage: model_holes.py MODEL.c3.lm OUT.c3.lm UPSTREAM_ZIP HOLES.txt KEEP_SINGLE.txt [--quantile 0.25]
UPSTREAM_ZIP: the pinned rime-ice archive (cn_dicts/base, 8105, ext, tencent supply word texts and weights).
"""
import argparse
import io
import math
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ngram_query import LOG_SCALE, Model  # noqa: E402


def dictionary_rows(archive):
    with zipfile.ZipFile(archive) as zf:
        for name in ("8105", "base", "ext", "tencent"):
            path = next((n for n in zf.namelist() if n.endswith(f"cn_dicts/{name}.dict.yaml")), None)
            if not path:
                continue
            body = False
            for line in io.TextIOWrapper(zf.open(path), encoding="utf-8"):
                if line.strip() == "...":
                    body = True
                    continue
                if not body or line.startswith("#"):
                    continue
                fields = line.rstrip("\n").split("\t")
                weight = fields[-1].strip()
                if len(fields) >= 2 and weight.isdigit():
                    yield fields[0], int(weight)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("holes", type=Path)
    parser.add_argument("keep_single", type=Path)
    parser.add_argument("--quantile", type=float, default=0.25)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit(f"{args.output} exists; a patched model never overwrites a file")
    holes = {line.strip() for line in args.holes.open(encoding="utf-8") if line.strip()}
    keep = {line.strip() for line in args.keep_single.open(encoding="utf-8")
            if line.strip() and not line.startswith("#")}

    weights = {}
    for text, weight in dictionary_rows(args.upstream):
        weights[text] = weights.get(text, 0) + weight
    total = sum(weights.values())
    model = Model(args.model)

    covered, targets = [], []
    for text, weight in weights.items():
        ident = model.id(text)
        if ident is None:
            continue
        share = math.log(weight / total)
        if holes.intersection(text):
            if text not in keep:
                targets.append((text, ident, share))
        else:
            covered.append(model.entry(ident)[0] - share)
    covered.sort()
    typical = covered[int(len(covered) * args.quantile)]

    data = bytearray(model.m[:])
    raised = 0
    examples = []
    for text, ident, share in targets:
        lower, ml, _ = model.entry(ident)
        delta = share + typical - lower
        if delta <= 0:
            continue
        if model.compact:  # int16 / LOG_SCALE
            clip = lambda v: max(-32768, min(32767, int(round(v * LOG_SCALE))))
            struct.pack_into("<hh", data, model.word_offset + 6 * ident, clip(lower + delta), clip(ml + delta))
        else:  # version 2: float lower, ml, backoff per 16-byte word record
            struct.pack_into("<ff", data, model.word_offset + 16 * ident, lower + delta, ml + delta)
        raised += 1
        if len(examples) < 8 or text in ("轮", "一轮", "靠谱", "交税", "灾难"):
            examples.append(f"{text} {lower:.2f}→{lower + delta:.2f}")
    with args.output.open("xb") as out:
        out.write(data)
    print(f"typical lower - ln share at quantile {args.quantile}: {typical:.2f}; "
          f"{len(targets)} hole words in the model, {raised} raised")
    print("  " + ", ".join(examples))


if __name__ == "__main__":
    main()
