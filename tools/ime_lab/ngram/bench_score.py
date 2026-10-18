"""Re-score lexicon_regression.py result files on a common subset of cases.

Usage: bench_score.py RESULT.json [RESULT2.json ...] [--exclude-texts FILE] [--only-prefix pub-]
Prints, per result file and per source group, passes/total (and the occurrence-weighted rate for
jianpin groups) over the cases whose expected text is not in --exclude-texts. Used to compare
models fairly when one of them has seen part of an evaluation set (those items are dropped for all).
"""
import argparse
import collections
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", nargs="+", type=Path)
    parser.add_argument("--exclude-texts", type=Path)
    parser.add_argument("--only-prefix", default="")
    args = parser.parse_args()
    excluded = set()
    if args.exclude_texts:
        excluded = {line.strip() for line in args.exclude_texts.open(encoding="utf-8") if line.strip()}
    for path in args.results:
        report = json.loads(path.read_text(encoding="utf-8"))
        stats = collections.defaultdict(lambda: [0, 0, 0, 0])  # pass, total, weighted pass, weighted total
        for case in report["cases"]:
            if case.get("status") not in ("pass", "fail"):
                continue
            if args.only_prefix and not case["id"].startswith(args.only_prefix):
                continue
            if case["expect"] in excluded:
                continue
            s = stats[case["source"]]
            ok = case["status"] == "pass"
            count = case.get("count", 1)
            s[0] += ok
            s[1] += 1
            s[2] += count * ok
            s[3] += count
        print(f"== {path.name}")
        for source, (good, total, wgood, wtotal) in sorted(stats.items()):
            weighted = f"  by occurrences {100 * wgood / wtotal:5.1f}%" if "jianpin" in source and wtotal else ""
            print(f"   {source:50} {good:4}/{total:<4} {100 * good / total:5.1f}%{weighted}")


if __name__ == "__main__":
    main()
