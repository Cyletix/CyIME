"""Raise chat-blended weights of entries the chat corpus never shows (one-off experiment, see docstring of main)."""
import argparse
import gzip
import io
import math
import zipfile
from collections import defaultdict
from pathlib import Path


def rows(lines):
    body = False
    for line in lines:
        if line.strip() == "...":
            body = True
            continue
        if not body or line.startswith("#"):
            continue
        fields = line.rstrip("\n").split("\t")
        if len(fields) >= 3 and fields[2].strip().isdigit():
            yield fields[0], fields[1], int(fields[2])


def main():
    """chat_base_weights.py gives a word the chat corpus never shows the floor 1 on the dictionary's scale, so its
    blended weight is sqrt(upstream): 轮 (197302 upstream, always inside 一轮/轮到 in the segmented corpus) became 444
    and fell behind 囵. Not seen in N lines means fewer than one occurrence, not none: give such entries half an
    occurrence on the same scale. Only entries whose text has no chat count change, and only upward.

    Usage: unseen_floor.py GENERATED_RIME_ICE_DIR UPSTREAM_ZIP COUNTS.tsv DICT OUT.tsv.gz [--half 0.5]"""
    parser = argparse.ArgumentParser()
    parser.add_argument("generated", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("counts", type=Path)
    parser.add_argument("dict")
    parser.add_argument("output", type=Path)
    parser.add_argument("--half", type=float, default=0.5)
    parser.add_argument("--beta", type=float, default=0.5)
    args = parser.parse_args()
    current = list(rows((args.generated / "cn_dicts" / f"{args.dict}.dict.yaml").open(encoding="utf-8")))
    with zipfile.ZipFile(args.upstream) as zf:
        name = next(n for n in zf.namelist() if n.endswith(f"cn_dicts/{args.dict}.dict.yaml"))
        upstream = {(t, c): w for t, c, w in rows(io.TextIOWrapper(zf.open(name), encoding="utf-8"))}
    counts = {}
    for line in args.counts.open(encoding="utf-8"):
        word, count = line.rstrip("\n").split("\t")
        counts[word] = int(count)
    by_text = defaultdict(int)
    for (text, _), weight in upstream.items():
        by_text[text] += weight
    scale = sum(upstream.values()) / max(1, sum(counts.get(text, 0) for text in by_text))
    raised = 0
    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n") as out:
        for text, code, weight in current:
            up = upstream.get((text, code))
            if up and counts.get(text, 0) == 0:
                share = up / by_text[text] if by_text[text] else 1.0
                floor = round(math.exp((1 - args.beta) * math.log(max(up, 1)) +
                                       args.beta * math.log(max(1.0, args.half * share * scale))))
                if floor > weight:
                    weight = floor
                    raised += 1
            out.write(f"{text}\t{code}\t{weight}\n")
    print(f"{args.dict}: scale {scale:.0f}, {raised} unseen entries raised")


if __name__ == "__main__":
    main()
