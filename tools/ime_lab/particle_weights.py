"""Re-split the weights of XX的 / XX地 / XX得 siblings by how often each is actually written in chat.

rime-ice gives same-reading siblings weights from written text, often the reverse of chat: 你说得 7275 vs
你说的 47, 生活得 16021 vs 生活的 53, so nishuode offered 你说得 first. For every group of entries that share
a reading, are three characters long, end in 的/地/得 read de and differ only in that last character, the
group's total weight is kept and re-split in proportion to the exact-string counts in the chat corpus
(script statistics only), smoothed toward the original split with `--prior` pseudo-counts so groups the
corpus barely sees keep their order. Other entries are copied unchanged.

Usage: python particle_weights.py BASE.tsv.gz EXT.tsv.gz CORPUS.txt OUT_BASE.tsv.gz OUT_EXT.tsv.gz
       [--lines 8000000] [--prior 5]
Tables are text<TAB>code<TAB>weight (the files applied by app/build-logic/tasks-chinese.gradle.kts).
"""
import argparse
import gzip
from collections import Counter, defaultdict
from itertools import islice

PARTICLES = "的地得"


def read(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return [line.rstrip("\n").split("\t") for line in f if line.strip()]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("base")
    parser.add_argument("ext")
    parser.add_argument("corpus")
    parser.add_argument("out_base")
    parser.add_argument("out_ext")
    parser.add_argument("--lines", type=int, default=8_000_000)
    parser.add_argument("--prior", type=float, default=5.0)
    args = parser.parse_args()
    tables = {"base": read(args.base), "ext": read(args.ext)}
    groups = defaultdict(list)  # (stem, code) -> [(table, index)]
    for name, rows in tables.items():
        for i, (text, code, _) in enumerate(rows):
            if len(text) == 3 and text[-1] in PARTICLES and code.endswith(" de") and code.count(" ") == 2:
                groups[(text[:2], code)].append((name, i))
    groups = {k: v for k, v in groups.items() if len({tables[n][i][0] for n, i in v}) > 1}
    wanted = {tables[n][i][0] for v in groups.values() for n, i in v}
    counts = Counter()
    with open(args.corpus, encoding="utf-8", errors="ignore") as corpus:
        for line in islice(corpus, args.lines):
            for j in range(len(line) - 2):
                if line[j + 2] in PARTICLES and line[j:j + 3] in wanted:
                    counts[line[j:j + 3]] += 1
    changed = 0
    for members in groups.values():
        weights = [int(tables[n][i][2]) for n, i in members]
        total = sum(weights)
        seen = [counts[tables[n][i][0]] for n, i in members]
        if total <= 0 or sum(seen) == 0:
            continue
        mass = sum(seen) + args.prior
        for (n, i), w, c in zip(members, weights, seen):
            share = (c + args.prior * w / total) / mass
            new = max(1, round(total * share))
            if new != w:
                tables[n][i][2] = str(new)
                changed += 1
    for name, out in (("base", args.out_base), ("ext", args.out_ext)):
        with gzip.open(out, "wt", encoding="utf-8", newline="\n") as f:
            for row in tables[name]:
                f.write("\t".join(row) + "\n")
    print(f"{len(groups)} sibling groups, {changed} weights changed")


if __name__ == "__main__":
    main()
