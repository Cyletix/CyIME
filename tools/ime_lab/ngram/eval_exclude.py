"""Collect every evaluation sentence so the training corpus can drop it verbatim.

Sources are the fixed replay fixtures, the public IME test sets, the four-IME comparison
sheet, the lexicon regression fixture and the LCCC-base validation lines. Only the Han text
is kept (one sentence per line, deduplicated); rows shorter than --min-chars are not
written, because 2–4 character messages such as 好的 are ordinary vocabulary rather than a
memorisable test item (the rule is recorded in the handover document). Prints counts only.

Usage: eval_exclude.py OUT.txt [--min-chars 5] [--fixture F.json ...] [--jsonl F ...]
                       [--csv F:COLUMN ...] [--tsv F:COLUMN ...] [--text F ...]
"""
import argparse
import csv
import gzip
import json
import re
from pathlib import Path

HAN_RUN = re.compile(r"[㐀-䶿一-鿿〇]+")


def opener(path):
    return gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else open(path, encoding="utf-8-sig")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--min-chars", type=int, default=5)
    parser.add_argument("--fixture", type=Path, action="append", default=[], help="t9_fixture.py JSON (cases[].text)")
    parser.add_argument("--jsonl", type=Path, action="append", default=[], help="JSONL with a text field")
    parser.add_argument("--csv", action="append", default=[], help="PATH:COLUMN")
    parser.add_argument("--tsv", action="append", default=[], help="PATH:COLUMN (header row)")
    parser.add_argument("--text", type=Path, action="append", default=[], help="plain text, one sentence per line (.gz ok)")
    args = parser.parse_args()
    sentences = set()
    per_source = {}

    def add(source, text):
        for run in HAN_RUN.findall(text or ""):
            if len(run) >= args.min_chars:
                sentences.add(run)
                per_source[source] = per_source.get(source, 0) + 1

    for path in args.fixture:
        for case in json.load(opener(path))["cases"]:
            add(path.name, case.get("text", ""))
    for path in args.jsonl:
        for line in opener(path):
            row = json.loads(line)
            add(path.name, row.get("text") or row.get("target") or "")
    for spec in args.csv + args.tsv:
        path, _, column = spec.rpartition(":")
        delimiter = "\t" if spec in args.tsv else ","
        for row in csv.DictReader(opener(path), delimiter=delimiter):
            add(Path(path).name, row.get(column, ""))
    for path in args.text:
        for line in opener(path):
            add(path.name, line.strip())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        for sentence in sorted(sentences):
            out.write(sentence + "\n")
    print(json.dumps({"sentences": len(sentences), "min_chars": args.min_chars, "per_source_runs": per_source},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
