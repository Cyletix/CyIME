"""Estimate frequencies for rime-ice cn_dicts/ext.dict.yaml, whose entries all carry the placeholder weight 100.

Each entry is scored with the T9 word n-gram model (tools/ime_lab/ngram): ln P of its best split into model
words, chained as the decoder does (unigram, bigram, trigram with back-off). Entries keep upstream's tier (median
100) and are spread around it by their score, so ties break by real usage without lifting ext above counted
base words. Output: gzip TSV of text, code, weight for every ext entry, read by the prepareChineseDictionaries
Gradle task.

Usage: python ext_weights.py MODEL RIME_ICE_DIR OUTPUT.tsv.gz
"""
import gzip
import math
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "ngram"))
from ngram_query import Model  # noqa: E402

MAX_WORD = 8
# Upstream's tier for every ext entry; the median entry keeps it, so ext as a whole stays where rime-ice put it.
UPSTREAM = 100
# How far the corpus spreads entries around that tier (weight ratio per unit ln P). Larger values let estimated
# phrases outrank counted base words and act as shortcuts in 26-key sentences (houhuisile -> 后惠斯勒). 0.13 is the
# setting compared in docs/development/ext-weights-20261018.md (7 clauses fixed, 1 regressed).
STRENGTH = 0.13


class FastModel(Model):
    """Binary search on the mapped id lists instead of decoding whole successor lists."""

    def _find(self, offset, begin, end, value):
        lo, hi = begin, end
        if self.compact:
            m = self.m
            while lo < hi:
                mid = (lo + hi) >> 1
                p = offset + 3 * mid
                v = m[p] | (m[p + 1] << 8) | (m[p + 2] << 16)
                if v < value:
                    lo = mid + 1
                else:
                    hi = mid
            if lo < end:
                p = offset + 3 * lo
                if (self.m[p] | (self.m[p + 1] << 8) | (self.m[p + 2] << 16)) == value:
                    return lo
            return None
        while lo < hi:
            mid = (lo + hi) >> 1
            if struct.unpack_from("<I", self.m, offset + 4 * mid)[0] < value:
                lo = mid + 1
            else:
                hi = mid
        if lo < end and struct.unpack_from("<I", self.m, offset + 4 * lo)[0] == value:
            return lo
        return None


def score(model, text, cache):
    """ln P of text under its best segmentation (Viterbi over unigrams), then chained with context."""
    n = len(text)
    best = [(-math.inf, None)] * (n + 1)
    best[0] = (0.0, None)
    for end in range(1, n + 1):
        for start in range(max(0, end - MAX_WORD), end):
            if best[start][0] == -math.inf:
                continue
            word = text[start:end]
            if word not in cache:
                cache[word] = model.id(word)
            ident = cache[word]
            if ident is None and end - start > 1:
                continue
            value = best[start][0] + model.ml(ident)
            if value > best[end][0]:
                best[end] = (value, start)
    words, end = [], n
    while end > 0:
        start = best[end][1]
        words.append(text[start:end])
        end = start
    words.reverse()
    ids = [cache[w] for w in words]
    total = model.ml(ids[0])
    for k in range(1, len(ids)):
        total += model.logp(ids[k - 1], ids[k]) if k == 1 else model.logp3(ids[k - 2], ids[k - 1], ids[k])
    return total


def entries(path):
    body = False
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip() == "...":
            body = True
            continue
        if not body or not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) >= 2:
            yield fields[0], fields[1], (int(fields[2]) if len(fields) > 2 and fields[2].isdigit() else None)


def main():
    model_path, root, output = sys.argv[1:4]
    model = FastModel(model_path)
    cache = {}
    root = Path(root) / "cn_dicts"
    scored = [(text, code, score(model, text, cache)) for text, code, _ in entries(root / "ext.dict.yaml")]
    middle = sorted(s for _, _, s in scored)[len(scored) // 2]
    rows, weights = [], []
    for text, code, value in scored:
        weight = max(1, round(UPSTREAM * math.exp(STRENGTH * (value - middle))))
        rows.append(f"{text}\t{code}\t{weight}\n")
        weights.append(weight)
    with gzip.open(output, "wt", encoding="utf-8", newline="\n", compresslevel=9) as out:
        out.write("".join(rows))
    weights.sort()
    q = lambda f: weights[int(f * (len(weights) - 1))]
    print(f"ext entries {len(weights)}; weight quantiles 10% {q(0.1)} 50% {q(0.5)} 90% {q(0.9)} 99% {q(0.99)}"
          f" max {weights[-1]}", file=sys.stderr)


if __name__ == "__main__":
    main()
