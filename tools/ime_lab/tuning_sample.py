"""Draw a tuning sample from the public corpus pools (raw_sms / raw_lccc), disjoint from the public test set.

Same shapes as the test set (jianpin.jsonl: whole 2-4 character messages typed as initials, with occurrence
counts; sentence.jsonl: 4-12 character clauses in full pinyin), groups tune_sms / tune_lccc, so
lexicon_regression.py --public-set reads it. Any text that appears in the test set is excluded. Readings come
from pypinyin (run with an interpreter that has it). The public test set itself stays score-only.

Usage: python tuning_sample.py RAW_DIR TEST_DIR OUT_DIR [--seed 20261019] [--per-group 600]
"""
import argparse
import json
import random
import re
from collections import Counter
from pathlib import Path

from pypinyin import Style, lazy_pinyin

HAN = re.compile(r"^[一-鿿]+$")


def readings(text):
    return [p.replace("ü", "v") for p in lazy_pinyin(text, style=Style.NORMAL, v_to_u=False)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("raw")
    parser.add_argument("test")
    parser.add_argument("out")
    parser.add_argument("--seed", type=int, default=20261019)
    parser.add_argument("--per-group", type=int, default=600)
    args = parser.parse_args()
    excluded = set()
    for name in ("jianpin", "sentence", "choice"):
        path = Path(args.test) / f"{name}.jsonl"
        if path.exists():
            for line in path.open(encoding="utf-8"):
                row = json.loads(line)
                excluded.add(row.get("text") or row.get("target"))
    rnd = random.Random(args.seed)
    jianpin, sentence = [], []
    for group, pool in (("tune_sms", "raw_sms.jsonl"), ("tune_lccc", "raw_lccc.jsonl")):
        messages, clauses = Counter(), set()
        for line in (Path(args.raw) / pool).open(encoding="utf-8"):
            row = json.loads(line)
            text = row["text"]
            if HAN.match(text) and 2 <= len(text) <= 4 and text not in excluded:
                messages[text] += 1
            for clause in row.get("clauses", []):
                if HAN.match(clause) and 4 <= len(clause) <= 12 and clause not in excluded:
                    clauses.add(clause)
        # Most frequent messages first, as the test set's jianpin group (common phrases, ties shuffled).
        picked = sorted(messages)
        rnd.shuffle(picked)
        picked.sort(key=lambda text: -messages[text])
        for i, text in enumerate(picked[:args.per_group]):
            py = readings(text)
            jianpin.append({"group": group, "text": text, "pinyin": py, "keys": "".join(p[0] for p in py),
                            "count": messages[text], "id": f"{group}-j{i:04d}"})
        picked = sorted(clauses)
        rnd.shuffle(picked)
        for i, text in enumerate(picked[:args.per_group]):
            sentence.append({"group": group, "text": text, "pinyin": readings(text), "id": f"{group}-s{i:04d}"})
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for name, rows in (("jianpin", jianpin), ("sentence", sentence)):
        with (out / f"{name}.jsonl").open("w", encoding="utf-8", newline="\n") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(len(jianpin), "jianpin,", len(sentence), "sentence")


if __name__ == "__main__":
    main()
