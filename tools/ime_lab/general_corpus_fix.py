"""Put back rime-ice's first choice where the chat blend flipped it and a large general corpus agrees with rime-ice.

The chat blend (chat_base_weights.py) moved the first candidate of about 4200 codes. LCCC drops some words entirely
(美国, 政府, 台湾, 爸爸 count 0), so for those the blend is plainly wrong. Each flipped pair is counted at word level in
three registers (wiki, subtitles, LCCC; tools: D:/CyIME-Data/eval/flips-general-20261030/count_seg.py). A pair goes
back to rime-ice's order only when the per-million rate averaged over the three is at least 2x in rime-ice's favour
and at least two of the three corpora agree. Only that word's entry for that code changes: it gets 1.2x the weight of
the word now on top, never more than its rime-ice weight.

Usage: python general_corpus_fix.py BLENDED.tsv.gz RIME_ICE_BASE.dict.yaml COUNTS.json OUTPUT.tsv.gz CHANGES.tsv
"""
import argparse
import collections
import gzip
import json

from chat_base_weights import entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("blended")
    parser.add_argument("base_dict")
    parser.add_argument("counts")
    parser.add_argument("output")
    parser.add_argument("changes")
    args = parser.parse_args()
    rows = [line.rstrip("\n").split("\t") for line in gzip.open(args.blended, "rt", encoding="utf-8")]
    shipped = {(t, c): int(w) for t, c, w in rows}
    orig = {(t, c): w for t, c, w in entries(args.base_dict)}
    counts = json.load(open(args.counts, encoding="utf-8"))
    total = {k: sum(v.values()) for k, v in counts.items()}

    def rate(w):
        return sum(counts[k].get(w, 0) / total[k] for k in counts) / len(counts)

    by_code = collections.defaultdict(list)
    for t, c in shipped:
        if (t, c) in orig:
            by_code[c].append(t)
    raised = {}
    for code, words in by_code.items():
        if len(words) < 2:
            continue
        top = max(words, key=lambda w: shipped[w, code])
        want = max(words, key=lambda w: orig[w, code])
        if top == want or orig[want, code] < 1000:
            continue
        agree = sum(1 for k in counts if counts[k].get(want, 0) > counts[k].get(top, 0))
        if rate(want) >= 2 * rate(top) and agree >= 2:
            raised[want, code] = (top, min(orig[want, code], int(shipped[top, code] * 1.2) + 1))
    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n") as out:
        for t, c, w in rows:
            out.write(f"{t}\t{c}\t{raised[t, c][1] if (t, c) in raised else w}\n")
    with open(args.changes, "w", encoding="utf-8", newline="\n") as out:
        for (t, c), (top, w) in sorted(raised.items(), key=lambda kv: kv[0][1]):
            out.write(f"{c}\t{t}\t{shipped[t, c]}->{w}\tover {top} {shipped[top, c]}\n")
    print(f"{len(raised)} entries raised")


if __name__ == "__main__":
    main()
