"""Per-category pass rates of clause endpoints (immediate top-1) for generated test sets.

Usage: python category_report.py CLAUSES_TSV OLD_RUN NEW_RUN
CLAUSES_TSV lines: category, clause text, pinyin, original sentence (gen_tests.py build).
"""
import json
import sys
from collections import OrderedDict
from pathlib import Path


def endpoints(run):
    """clause text -> (first candidate, rank of the target) at the clause endpoint."""
    result = {}
    for line in (Path(run) / "replay.jsonl").open(encoding="utf-8"):
        row = json.loads(line)
        if row.get("stage") != "key_immediate" or row.get("mode") != "continuous" or not row.get("clause_endpoint"):
            continue
        texts = [candidate["text"] for candidate in row["rows"]]
        rank = texts.index(row["target"]) + 1 if row["target"] in texts else -1
        result[row["target"]] = (texts[0] if texts else "", rank)
    return result


def main():
    clauses = [line.rstrip("\n").split("\t") for line in open(sys.argv[1], encoding="utf-8") if line.strip()]
    old, new = endpoints(sys.argv[2]), endpoints(sys.argv[3])
    groups = OrderedDict()
    for category, text, _pinyin, sentence in clauses:
        groups.setdefault(category, []).append((text, sentence))
    total = [0, 0, 0]
    print(f"{'类别':<24} 旧 → 新（整句首选正确 / 总数）")
    for category, items in groups.items():
        o = sum(1 for t, _ in items if old.get(t, ("", -1))[1] == 1)
        n = sum(1 for t, _ in items if new.get(t, ("", -1))[1] == 1)
        total[0] += o; total[1] += n; total[2] += len(items)
        print(f"{category:<24} {o:>3} → {n:>3} / {len(items)}")
    print(f"{'合计':<24} {total[0]:>3} → {total[1]:>3} / {total[2]}  "
          f"({100 * total[0] / total[2]:.1f}% → {100 * total[1] / total[2]:.1f}%)")
    print("\n新算法未通过：")
    for category, items in groups.items():
        for text, sentence in items:
            first, rank = new.get(text, ("", -1))
            if rank != 1:
                was = "旧对" if old.get(text, ("", -1))[1] == 1 else "旧也错"
                print(f"  [{category}] {text}  →  {first}（目标第 {rank}，{was}）")


if __name__ == "__main__":
    main()
