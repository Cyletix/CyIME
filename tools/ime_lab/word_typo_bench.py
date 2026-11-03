"""Common-word and short-sentence typo benchmark for 26-key full pinyin (2026-11-03).

A review said common words do not come out and one wrong letter means nothing matches. This measures, per kind of
slip, where the intended word lands while typing and after the pause (RefineOmission), with WordTypoProbe.java.

  word_typo_bench.py make OUT.tsv [BASE_DICT]   cases: id, kind, typed, target
  word_typo_bench.py score RESULT.tsv [...]     first / top 3 / top 5 / top 20, before and after the pause

Words: the 300 two-, 150 three- and 100 four-character words with the highest weight in the built
cn_dicts/base.dict.yaml (default app/build/generated/chinese-assets/rime_ice/...). Sentences: the held-out everyday
clauses fixture (3+ syllables). Each gets one seeded slip of every kind: sub (a measured touch-box neighbour of the
key, typo_bench.neighbor_map), omit (one letter left out, not the first), swap (two adjacent letters reversed),
double (a letter typed twice), extra (a neighbour inserted after a letter). omit/double/extra never touch the last
letter: there they read as the start of the next word, not as a slip.
"""
from __future__ import annotations

import collections
import json
import random
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import typo_bench  # noqa: E402

REPO = HERE.parent.parent
KINDS = ("clean", "sub", "omit", "swap", "double", "extra")


def words(base_dict):
    best, body = {}, False
    for line in Path(base_dict).read_text(encoding="utf-8").splitlines():
        if line.strip() == "...":
            body = True
            continue
        fields = line.split("\t")
        if not body or line.startswith("#") or len(fields) != 3:
            continue
        text, code, weight = fields[0], fields[1], float(fields[2])
        if 2 <= len(text) <= 4 and len(code.split()) == len(text) and (text not in best or best[text][1] < weight):
            best[text] = (code, weight)
    by_length = {n: sorted(((w, t, c) for t, (c, w) in best.items() if len(t) == n), reverse=True) for n in (2, 3, 4)}
    return [(t, c) for n, k in ((2, 300), (3, 150), (4, 100)) for _, t, c in by_length[n][:k]]


def slips(code, near, rng):
    s, out = code.replace(" ", ""), {}
    for kind in KINDS[1:]:
        for _ in range(40):
            i = rng.randrange(len(s))
            if kind == "sub":
                t = s[:i] + rng.choice(near[s[i]]) + s[i + 1:]
            elif kind in ("omit", "double", "extra") and i == len(s) - 1:
                continue
            elif kind == "omit":
                if i == 0:
                    continue
                t = s[:i] + s[i + 1:]
            elif kind == "swap":
                if i + 1 >= len(s) or s[i] == s[i + 1]:
                    continue
                t = s[:i] + s[i + 1] + s[i] + s[i + 2:]
            elif kind == "double":
                t = s[:i] + s[i] + s[i:]
            else:
                t = s[:i + 1] + rng.choice(near[s[i]]) + s[i + 1:]
            if t != s:
                out[kind] = t
                break
    return s, out


def make(path, base_dict=None):
    base_dict = base_dict or REPO / "app/build/generated/chinese-assets/rime_ice/cn_dicts/base.dict.yaml"
    near = dict(entry.split(":") for entry in typo_bench.neighbor_map().split(";"))
    fixture = json.loads((HERE / "fixtures/everyday_clauses_20261009.json").read_text(encoding="utf-8"))
    sentences = [(c["text"], " ".join(c["pinyin"])) for c in fixture["cases"] if len(c["pinyin"]) >= 3]
    rng = random.Random(20261103)
    with open(path, "w", encoding="utf-8") as out:
        for group, items in (("w", words(base_dict)), ("s", sentences)):
            for n, (text, code) in enumerate(items):
                clean, typed = slips(code, near, rng)
                out.write(f"{group}{n}\tclean\t{clean}\t{text}\n")
                for kind, letters in typed.items():
                    out.write(f"{group}{n}\t{kind}\t{letters}\t{text}\n")


def score(paths):
    for path in paths:
        rows = [line.rstrip("\n").split("\t") for line in open(path, encoding="utf-8")]
        print(f"## {path} ({len(rows)} cases)\n")
        print("| 组 | 错法 | 条数 | 首选 | 前三 | 前五 | 前20 | 停顿后首选 | 停顿后前三 |\n|---|---|---:|---:|---:|---:|---:|---:|---:|")
        groups = collections.defaultdict(list)
        for row in rows:
            groups[("词" if row[0][0] == "w" else "句", row[1])].append(row)
        for group in ("词", "句"):
            for kind in KINDS:
                items = groups[(group, kind)]
                if items:
                    rate = lambda col, m: sum(0 < int(r[col]) <= m for r in items) / len(items) * 100
                    print(f"| {group} | {kind} | {len(items)} | {rate(4, 1):.1f} | {rate(4, 3):.1f} | {rate(4, 5):.1f} |"
                          f" {rate(4, 20):.1f} | {rate(5, 1):.1f} | {rate(5, 3):.1f} |")
        per_key = [int(r[7]) / int(r[6]) for r in rows]
        slowest = sorted(int(r[8]) for r in rows)
        pause = sorted(int(r[9]) for r in rows)
        print(f"\n每键均值 {statistics.mean(per_key):.0f} us；每题最慢一键 p50 {slowest[len(slowest) // 2]}、"
              f"p95 {slowest[int(len(slowest) * .95)]} us；停顿补算 p95 {pause[int(len(pause) * .95)]} us\n")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "make":
        make(*sys.argv[2:4])
    elif len(sys.argv) >= 3 and sys.argv[1] == "score":
        score(sys.argv[2:])
    else:
        print(__doc__)
        sys.exit(2)
