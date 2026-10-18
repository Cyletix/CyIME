"""Rank-weighted candidate score shared by every candidate-quality evaluation.

A test item scores RATIO ** (rank - 1) when its target is the rank-th candidate and 0 beyond CUTOFF:
with the default 0.5 / 4 that is 1, 0.5, 0.25, 0.125 for ranks 1-4 and 0 from rank 5 or when the target
is missing. Top-1 accuracy is kept beside it, and --ratio 0.6667 is the sensitivity check.

Inputs (detected per file):
  * lexicon_regression.py --output reports ({"cases": [...]}, each case with expect/top/source/count);
  * JSONL, one item per line: id, group, target, then either rank (1-based, null = not found) or
    candidates (list, in display order); optional weight (e.g. occurrence count) and known_top1
    (bool, overrides the rank-1 decision when a human adjudicated the first candidate);
  * public-set run_eval.py results, given as RESULTS.jsonl#METHOD plus --targets SET.jsonl (the set file
    with id, text/target and count): each row's ranked[METHOD] is the candidate order.

  rank_score.py summary RUN [--ratio R] [--cutoff N] [--group-prefix P] [--json OUT]
  rank_score.py compare BASE NEW [...]   paired per-group deltas and rank transitions; --list prints the
                                         ids whose rank changed (add --show-text only for public sets).
"""

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path


SKIP_EMOJI = False  # --skip-emoji: rank among text candidates only (emoji filter output dropped)


def _is_text(candidate):
    return any(c.isalnum() for c in candidate)


def rank_of(target, candidates):
    if SKIP_EMOJI:
        candidates = [c for c in candidates if _is_text(c)]
    for i, text in enumerate(candidates, 1):
        if text == target:
            return i
    return None


def score(rank, ratio=0.5, cutoff=4):
    return ratio ** (rank - 1) if rank and rank <= cutoff else 0.0


def _report_group(case):
    s = case["source"]
    s = "user/found" if s in ("user", "found") or s.startswith("user ") else s
    return f"{case['keyboard']} {s}"


def load(path, targets=None):
    """Return {id: item} with item = dict(group, target, rank, weight, input)."""
    path = str(path)
    if "#" in path:
        path, method = path.rsplit("#", 1)
        sets = {r["id"]: r for r in map(json.loads, Path(targets).read_text(encoding="utf-8").splitlines())}
        items = {}
        for row in map(json.loads, Path(path).read_text(encoding="utf-8").splitlines()):
            it = sets[row["id"]]
            target = it.get("target", it.get("text"))
            items[row["id"]] = dict(group=row["group"], target=target, input=it.get("keys", ""),
                                    rank=rank_of(target, row["ranked"][method]), weight=it.get("count", 1))
        return items
    text = Path(path).read_text(encoding="utf-8-sig")
    items = {}
    try:
        report = json.loads(text)
    except json.JSONDecodeError:
        report = None  # JSONL
    if isinstance(report, dict) and "cases" in report:
        for case in report["cases"]:
            if case.get("status") not in ("pass", "fail") or case["expect"].startswith("!"):
                continue  # not run here (nine-key rows), or a "must not be first" case with no rank
            top = case.get("top", [])
            items[case["id"]] = dict(group=_report_group(case), target=case["expect"], input=case["input"],
                                     rank=rank_of(case["expect"], top), weight=case.get("count", 1))
        return items
    for line in text.splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        rank = row["rank"] if "rank" in row else rank_of(row["target"], row.get("candidates", []))
        if "known_top1" in row:
            if row["known_top1"]:
                rank = 1
            elif rank == 1:
                rank = None  # adjudicated wrong first candidate: the reader matched a misread
        items[row["id"]] = dict(group=row.get("group", "all"), target=row["target"], input=row.get("input", ""),
                                rank=rank, weight=row.get("weight", 1))
    return items


def stats(items, ratio, cutoff):
    """Per-group plain and occurrence-weighted top-1 and rank scores (percent)."""
    acc = defaultdict(lambda: Counter())
    for it in items.values():
        for g in (it["group"], "*all"):
            a, w, r = acc[g], it["weight"], it["rank"]
            s, s23 = score(r, ratio, cutoff), score(r, 2 / 3, cutoff)
            a["n"] += 1; a["top1"] += r == 1; a["score"] += s; a["score23"] += s23
            a["top3"] += bool(r and r <= 3); a["found"] += bool(r)
            a["w"] += w; a["w_top1"] += w * (r == 1); a["w_score"] += w * s
            a["sq"] += s * s
            for k in range(1, cutoff + 1):
                a[f"r{k}"] += r == k
    out = {}
    for g, a in sorted(acc.items()):
        n = a["n"]
        mean = a["score"] / n
        sd = math.sqrt(max(0.0, a["sq"] / n - mean * mean))
        out[g] = dict(n=n, top1=100 * a["top1"] / n, score=100 * mean, score_ci=1.96 * 100 * sd / math.sqrt(n),
                      score23=100 * a["score23"] / n, top3=100 * a["top3"] / n,
                      ranks=[a[f"r{k}"] for k in range(1, cutoff + 1)],
                      w_top1=100 * a["w_top1"] / a["w"], w_score=100 * a["w_score"] / a["w"],
                      weighted=a["w"] != n)
    return out


def print_summary(st):
    print(f"{'group':52} {'n':>5} {'top1%':>6} {'score%':>11} {'r=2/3%':>7} {'top3%':>6}  ranks 1..k   by occurrences")
    for g, s in st.items():
        occ = f"top1 {s['w_top1']:.1f} / score {s['w_score']:.1f}" if s["weighted"] else ""
        print(f"{g:52} {s['n']:5} {s['top1']:6.1f} {s['score']:6.1f}±{s['score_ci']:<4.1f} {s['score23']:7.1f} "
              f"{s['top3']:6.1f}  {'/'.join(map(str, s['ranks'])):11} {occ}")


def compare(base, new, ratio, cutoff, prefix, show_list, show_text):
    shared = sorted(set(base) & set(new))
    groups = defaultdict(list)
    for i in shared:
        if base[i]["group"].startswith(prefix):
            groups[base[i]["group"]].append(i)
            groups["*all"].append(i)
    rows = {}
    print(f"{'group':52} {'n':>5} {'top1 base->new':>15} {'score base->new':>16} {'d(paired)':>13} {'r=2/3 d':>8}  top1 +/-")
    for g in sorted(groups):
        ids = groups[g]
        n = len(ids)
        d = [score(new[i]["rank"], ratio, cutoff) - score(base[i]["rank"], ratio, cutoff) for i in ids]
        d23 = sum(score(new[i]["rank"], 2 / 3, cutoff) - score(base[i]["rank"], 2 / 3, cutoff) for i in ids)
        mean = sum(d) / n
        sd = math.sqrt(max(0.0, sum(x * x for x in d) / n - mean * mean))
        t1b = sum(base[i]["rank"] == 1 for i in ids)
        t1n = sum(new[i]["rank"] == 1 for i in ids)
        fixed = sum(base[i]["rank"] != 1 and new[i]["rank"] == 1 for i in ids)
        broke = sum(base[i]["rank"] == 1 and new[i]["rank"] != 1 for i in ids)
        sb = 100 * sum(score(base[i]["rank"], ratio, cutoff) for i in ids) / n
        rows[g] = dict(n=n, top1_base=t1b, top1_new=t1n, score_base=sb, score_new=sb + 100 * mean,
                       delta=100 * mean, delta_ci=1.96 * 100 * sd / math.sqrt(n), delta23=100 * d23 / n,
                       fixed=fixed, broke=broke)
        print(f"{g:52} {n:5} {t1b:6}->{t1n:<7} {sb:7.1f}->{sb + 100 * mean:<7.1f} {100 * mean:+6.1f}±{rows[g]['delta_ci']:<5.1f}"
              f" {100 * d23 / n:+8.1f}  +{fixed}/-{broke}")
    if show_list:
        trans = Counter()
        for i in groups["*all"]:
            rb, rn = base[i]["rank"], new[i]["rank"]
            if rb != rn:
                trans[(rb, rn)] += 1
                text = f" {base[i]['input']} {base[i]['target']}" if show_text else ""
                print(f"  {i}: rank {rb or '-'} -> {rn or '-'}{text}")
        print("rank transitions (base->new: count):",
              ", ".join(f"{a or '-'}->{b or '-'}: {c}" for (a, b), c in sorted(trans.items(), key=lambda x: -x[1])))
    return rows


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("summary", "compare"):
        s = sub.add_parser(name)
        s.add_argument("runs", nargs=1 if name == "summary" else 2)
        s.add_argument("--targets", type=Path, help="set file for RESULTS.jsonl#METHOD runs")
        s.add_argument("--ratio", type=float, default=0.5)
        s.add_argument("--cutoff", type=int, default=4)
        s.add_argument("--group-prefix", default="")
        s.add_argument("--json", type=Path)
        s.add_argument("--skip-emoji", action="store_true", help="ignore emoji candidates when ranking")
        if name == "compare":
            s.add_argument("--list", action="store_true")
            s.add_argument("--show-text", action="store_true")
    a = p.parse_args()
    global SKIP_EMOJI
    SKIP_EMOJI = a.skip_emoji
    if a.cmd == "summary":
        items = {k: v for k, v in load(a.runs[0], a.targets).items() if v["group"].startswith(a.group_prefix)}
        result = stats(items, a.ratio, a.cutoff)
        print_summary(result)
    else:
        result = compare(load(a.runs[0], a.targets), load(a.runs[1], a.targets), a.ratio, a.cutoff, a.group_prefix, a.list, a.show_text)
    if a.json:
        a.json.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
