"""Summarize CyIME key-to-frame latency probe lines (logcat tag CyIMELatency).

Usage: python tools/ime_lab/key_latency_report.py <logcat.txt> [--json out.json]
Lines are produced only when `adb shell setprop log.tag.CyIMELatency DEBUG` is set.
Groups by layout and composition length: short = 1-3, mid = 4-7, long = 8+.
All values are microseconds in the log; the report prints milliseconds.
"""
import argparse
import json
import re
import sys

STAGES = ["dispatch", "main", "queue", "bg", "engine", "handoff", "apply",
          "vsyncwait", "recompose", "layoutdraw", "render", "gpu", "total"]
LINE = re.compile(r"CyIMELatency.*?(layout=\S+.*)$")


def bucket(length):
    return "short" if length <= 3 else "mid" if length <= 7 else "long"


def percentile(values, p):
    ordered = sorted(values)
    index = max(0, -(-len(ordered) * p // 100) - 1)
    return ordered[index]


def parse(path):
    rows = []
    with open(path, encoding="utf-8", errors="replace") as source:
        for raw in source:
            match = LINE.search(raw.rstrip())
            if not match:
                continue
            fields = dict(part.split("=", 1) for part in match.group(1).split() if "=" in part)
            row = {"layout": fields["layout"], "len": int(fields["len"]), "trigger": fields.get("trigger", "")}
            for stage in STAGES:
                if stage in fields:
                    row[stage] = int(fields[stage])
            row["dropped"] = int(fields.get("dropped", 0))
            rows.append(row)
    return rows


def summarize(rows):
    groups = {}
    for row in rows:
        groups.setdefault((row["layout"], bucket(row["len"])), []).append(row)
    result = {}
    for key in sorted(groups):
        members = groups[key]
        stats = {"n": len(members)}
        for stage in STAGES:
            values = [m[stage] for m in members if stage in m and m[stage] >= 0]
            if values:
                stats[stage] = {"p50": percentile(values, 50) / 1000, "p95": percentile(values, 95) / 1000}
        result[f"{key[0]}/{key[1]}"] = stats
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log")
    parser.add_argument("--json")
    args = parser.parse_args()
    rows = parse(args.log)
    if not rows:
        sys.exit("no CyIMELatency lines")
    summary = summarize(rows)
    print("group".ljust(16) + "n".rjust(5) + "".join(s.rjust(14) for s in STAGES))
    for group, stats in summary.items():
        cells = "".join((f"{stats[s]['p50']:.1f}/{stats[s]['p95']:.1f}" if s in stats else "-").rjust(14) for s in STAGES)
        print(group.ljust(16) + str(stats["n"]).rjust(5) + cells)
    print(f"rows={len(rows)} last_dropped={rows[-1]['dropped']} (p50/p95 ms)")
    if args.json:
        with open(args.json, "w", encoding="utf-8") as out:
            json.dump(summary, out, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
