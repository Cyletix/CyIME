"""Find frequent chat words the rime-ice dictionary does not have (我勒个去, 逗比, 辣鸡), from a public chat corpus.

Each Han run is segmented by forward maximum matching against every dictionary word (base, ext, tencent,
8105 single characters...). Characters left standing alone form "uncovered" runs; every 2-6 character
substring of such a run is a candidate, counted with its left and right neighbouring characters. A candidate
is kept when it is frequent, cohesive (pointwise mutual information of its weakest split) and free at both ends
(entropy of the neighbouring characters), the usual new-word statistics. Reading the corpus is a script
statistic only; no line is printed or stored.

Usage: python chat_new_words.py RIME_ICE_DIR CORPUS.txt OUTPUT.tsv [--lines N] [--min-count N]
Output: word<TAB>count<TAB>pmi<TAB>left entropy<TAB>right entropy, most frequent first.
"""
import argparse
import math
import re
from collections import Counter, defaultdict
from pathlib import Path

HAN = re.compile(r"[一-鿿]+")
MAX_DICT = 8
MAX_NEW = 6


def dictionary_words(root):
    text = (root / "rime_ice.dict.yaml").read_text(encoding="utf-8")
    block = text.split("import_tables:", 1)[1].split("\n...", 1)[0]
    words = set()
    for name in re.findall(r"(?m)^  - ([^\s#]+)", block):
        body = False
        for line in (root / f"{name}.dict.yaml").open(encoding="utf-8"):
            if line.strip() == "...":
                body = True
                continue
            if body and line and not line.startswith("#"):
                word = line.split("\t", 1)[0].strip()
                if len(word) >= 2:
                    words.add(word)
    return words


def uncovered_runs(run, words):
    """Forward maximum matching; yields maximal runs of characters no dictionary word covered."""
    i, start = 0, None
    while i < len(run):
        for size in range(min(MAX_DICT, len(run) - i), 1, -1):
            if run[i:i + size] in words:
                if start is not None:
                    yield start, i
                    start = None
                i += size
                break
        else:
            if start is None:
                start = i
            i += 1
    if start is not None:
        yield start, len(run)


def entropy(counter):
    """Entropy of the neighbouring characters; a line edge counts as a new neighbour every time
    (chat lines are short, so a word standing at the start of many lines is still free there)."""
    total = sum(counter.values())
    if not total:
        return 0.0
    edges = counter.get("^", 0) + counter.get("$", 0)
    value = -sum(c / total * math.log(c / total) for k, c in counter.items() if k not in ("^", "$"))
    return value + (edges / total) * math.log(total) if edges else value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("corpus")
    parser.add_argument("output")
    parser.add_argument("--lines", type=int, default=8_000_000)
    parser.add_argument("--min-count", type=int, default=30)
    args = parser.parse_args()
    words = dictionary_words(args.root)
    chars = Counter()
    grams = Counter()
    left = defaultdict(Counter)
    right = defaultdict(Counter)
    total = 0
    with open(args.corpus, encoding="utf-8", errors="ignore") as corpus:
        for number, line in enumerate(corpus):
            if number >= args.lines:
                break
            for run in HAN.findall(line):
                chars.update(run)
                total += len(run)
                for a, b in uncovered_runs(run, words):
                    if b - a < 2:
                        continue
                    for i in range(a, b):
                        for j in range(i + 2, min(b, i + MAX_NEW) + 1):
                            gram = run[i:j]
                            grams[gram] += 1
                            left[gram][run[i - 1] if i > 0 else "^"] += 1
                            right[gram][run[j] if j < len(run) else "$"] += 1
    rows = []
    for gram, count in grams.items():
        if count < args.min_count:
            continue
        # Cohesion: pointwise mutual information against its characters taken independently, per link.
        pmi = (math.log(count) + (len(gram) - 1) * math.log(total)
               - sum(math.log(chars[c]) for c in gram)) / (len(gram) - 1)
        rows.append((gram, count, pmi, entropy(left[gram]), entropy(right[gram])))
    rows.sort(key=lambda r: -r[1])
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        for gram, count, pmi, le, re_ in rows:
            out.write(f"{gram}\t{count}\t{pmi:.2f}\t{le:.2f}\t{re_:.2f}\n")
    print(len(rows), "candidates", total, "characters")


if __name__ == "__main__":
    main()
