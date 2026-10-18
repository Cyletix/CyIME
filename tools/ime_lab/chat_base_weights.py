"""Blend rime-ice base.dict.yaml weights with chat-corpus counts (chat_word_counts.py output).

rime-ice's base weights come from written text, so typing initials favours 核心 over 好像 and 报告 over 不过.
For every base entry the new weight is the geometric blend base^(1-beta) * chat^beta, where chat is the word's
chat count rescaled to the base total (polyphonic words share their count in proportion to the base weights,
unseen words get a floor of `--floor` on that scale). beta is chosen on CyIME's own fixtures, never on the
public test sets.

Usage: python chat_base_weights.py RIME_ICE_DIR COUNTS.tsv OUTPUT.tsv.gz --beta 0.5 [--floor 1]
Output: gzip TSV text, code, weight for every base entry.
"""
import argparse
import gzip
import math
from collections import defaultdict
from pathlib import Path


def entries(path):
    body = False
    for line in Path(path).open(encoding="utf-8"):
        if line.strip() == "...":
            body = True
            continue
        if not body or line.startswith("#"):
            continue
        fields = line.rstrip("\n").split("\t")
        if len(fields) >= 3 and fields[2].isdigit():
            yield fields[0], fields[1], int(fields[2])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("counts")
    parser.add_argument("output")
    parser.add_argument("--beta", type=float, required=True)
    parser.add_argument("--floor", type=float, default=1.0)
    args = parser.parse_args()
    base = list(entries(Path(args.root) / "cn_dicts" / "base.dict.yaml"))
    counts = {}
    for line in open(args.counts, encoding="utf-8"):
        word, count = line.rstrip("\n").split("\t")
        counts[word] = int(count)
    by_text = defaultdict(int)
    for text, _, weight in base:
        by_text[text] += weight
    chat_total = sum(counts.get(text, 0) for text in by_text)
    scale = sum(w for _, _, w in base) / max(1, chat_total)
    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n", compresslevel=9) as out:
        for text, code, weight in base:
            share = weight / by_text[text] if by_text[text] else 1.0
            chat = max(args.floor, counts.get(text, 0) * share * scale)
            blended = math.exp((1 - args.beta) * math.log(max(weight, 1)) + args.beta * math.log(chat))
            out.write(f"{text}\t{code}\t{max(1, round(blended))}\n")


if __name__ == "__main__":
    main()
