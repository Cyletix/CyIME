"""Count rime-ice base words in a chat corpus (LCCC), for spoken-language base weights.

Han runs of each line are segmented by unigram Viterbi over the base dictionary words (and 8105 single
characters), scored by the dictionary's own weights; every chosen word is counted. Reading the corpus is a
script statistic only; no line is printed or stored.

Usage: python chat_word_counts.py RIME_ICE_DIR CORPUS.txt OUTPUT.tsv [--lines N]
Output: word<TAB>count, words with count > 0.
"""
import argparse
import math
import re
import sys
from collections import Counter
from pathlib import Path

HAN = re.compile(r"[一-鿿]+")
MAX_WORD = 6


def load_vocabulary(root):
    weights = Counter()
    for name in ("8105", "base"):
        body = False
        for line in (Path(root) / "cn_dicts" / f"{name}.dict.yaml").open(encoding="utf-8"):
            if line.strip() == "...":
                body = True
                continue
            if not body or line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if len(fields) >= 3 and fields[2].isdigit() and len(fields[0]) <= MAX_WORD and HAN.fullmatch(fields[0]):
                weights[fields[0]] += int(fields[2])
    total = sum(weights.values())
    return {word: math.log((weight + 1) / total) for word, weight in weights.items()}


def segment(run, logp, unknown):
    n = len(run)
    best = [(0.0, 0)] + [(-math.inf, 0)] * n
    for end in range(1, n + 1):
        for start in range(max(0, end - MAX_WORD), end):
            score = best[start][0]
            if score == -math.inf:
                continue
            word = run[start:end]
            value = logp.get(word)
            if value is None:
                if end - start > 1:
                    continue
                value = unknown
            if score + value > best[end][0]:
                best[end] = (score + value, start)
    words, end = [], n
    while end > 0:
        start = best[end][1]
        words.append(run[start:end])
        end = start
    return words


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("corpus")
    parser.add_argument("output")
    parser.add_argument("--lines", type=int, default=0, help="read only the first N lines (0 = all)")
    args = parser.parse_args()
    logp = load_vocabulary(args.root)
    unknown = min(logp.values()) - 5
    counts = Counter()
    with open(args.corpus, encoding="utf-8", errors="replace") as corpus:
        for number, line in enumerate(corpus, 1):
            if args.lines and number > args.lines:
                break
            for run in HAN.findall(line):
                counts.update(segment(run, logp, unknown))
            if number % 1000000 == 0:
                print(f"{number} lines, {sum(counts.values())} words", file=sys.stderr, flush=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        for word, count in counts.most_common():
            out.write(f"{word}\t{count}\n")
    print(f"{len(counts)} distinct words, {sum(counts.values())} tokens", file=sys.stderr)


if __name__ == "__main__":
    main()
