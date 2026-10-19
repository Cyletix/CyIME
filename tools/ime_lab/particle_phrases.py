"""Build a test set of "XX的 / XX地 / XX得" dictionary phrases ranked by chat-corpus frequency.

Candidates are rime-ice base/ext entries of three characters ending in 的/地/得 whose particle is read de
(the adverbial 地 and the complement 得). Each is counted as an exact string in the chat corpus (script
statistics only) and the most frequent per particle are kept. Expectations follow how people type them:
  * XX的 + de: first candidate;
  * XX地 + de and XX得 + de: within the first four (XX的 is usually just as valid);
  * XX地 + di: first candidate, because many people type the adverbial 地 as di
    (2026-10-19 user report: manmandi should give 慢慢地).

Usage: python particle_phrases.py RIME_ICE_DIR CORPUS.txt OUTPUT.tsv [--skip 8000000] [--lines 4000000] [--per 150]
Output: lexicon_regression fixture rows; run with CYIME_EXTRA_CASES=OUTPUT.tsv.
"""
import argparse
from collections import Counter
from itertools import islice
from pathlib import Path


def candidates(root):
    found = {}
    for name in ("base", "ext"):
        body = False
        for line in (Path(root) / "cn_dicts" / f"{name}.dict.yaml").open(encoding="utf-8"):
            if line.strip() == "...":
                body = True
                continue
            fields = line.rstrip("\n").split("\t")
            if not body or line.startswith("#") or len(fields) < 2:
                continue
            text, code = fields[0], fields[1].split()
            if len(text) == 3 and text[-1] in "的地得" and len(code) == 3 and code[-1] == "de":
                found.setdefault(text, "".join(code[:2]))
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("rime_ice")
    parser.add_argument("corpus")
    parser.add_argument("output")
    parser.add_argument("--lines", type=int, default=4_000_000)
    parser.add_argument("--per", type=int, default=150)
    parser.add_argument("--skip", type=int, default=8_000_000,
                        help="corpus lines to skip; particle_weights.py counts the first 8M, so the test set comes from later lines")
    args = parser.parse_args()
    phrases = candidates(args.rime_ice)
    counts = Counter()
    with open(args.corpus, encoding="utf-8", errors="ignore") as corpus:
        for line in islice(corpus, args.skip, args.skip + args.lines):
            for i in range(len(line) - 2):
                if line[i + 2] in "的地得" and line[i:i + 3] in phrases:
                    counts[line[i:i + 3]] += 1
    rows = []
    for particle in "的地得":
        picked = [t for t, _ in counts.most_common() if t[-1] == particle][:args.per]
        for text in picked:
            stem = phrases[text]
            rows.append((f"particle-{text}-de", f"{stem}de", text, 1 if particle == "的" else 4))
            if particle == "地":
                rows.append((f"particle-{text}-di", f"{stem}di", text, 1))
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        out.write("id\tkeyboard\tinput\texpect\ttop_k\tsource\tdate\tnote\n")
        for ident, code, text, top_k in rows:
            out.write(f"{ident}\t26\t{code}\t{text}\t{top_k}\tparticle\t2026-10-19\t聊天语料常见 XX的/地/得\n")
    print(len(rows), "rows")


if __name__ == "__main__":
    main()
