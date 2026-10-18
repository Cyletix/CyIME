"""Side-by-side summary of host replay runs (same fixture): accuracy, per-clause changes,
per-key work and latency. Reads raw replay.jsonl through the official summarizer.

Usage: python compare_runs.py BASE_RUN CANDIDATE_RUN [MORE_RUNS...]
"""
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import summarize_daily_chat as audit  # noqa: E402

PASS = "digits:continuous"


def load(run):
    run = Path(run)
    fixture = audit.load_fixture(run / "fixture.json")
    records, _ = audit.read_evidence([run / "replay.jsonl"])
    report, snapshots = audit.summarize(fixture, records, [PASS])
    rows = [json.loads(line) for line in (run / "replay.jsonl").open(encoding="utf-8")]
    keys = [r for r in rows if r.get("stage") == "key_immediate" and r.get("mode") == "continuous"]
    return report["passes"][PASS]["metrics"], snapshots, keys


def stats(values):
    values = sorted(values)
    pick = lambda q: values[int(q * (len(values) - 1))] if values else 0
    return f"mean={statistics.fmean(values):7.2f} p50={pick(.5):6} p95={pick(.95):6} max={values[-1] if values else 0}"


def main():
    runs = [load(r) for r in sys.argv[1:]]
    names = [Path(r).name for r in sys.argv[1:]]
    for name, (metrics, _, keys) in zip(names, runs):
        ch, cl = metrics["immediate_characters"]["all"], metrics["immediate_clauses"]["all"]
        print(f"== {name}: chars top1 {ch['top1']}/{ch['count']} ({ch['top1_percent']}%) top5 {ch['top5']} top100 {ch['top100']}"
              f" | clauses top1 {cl['top1']}/{cl['count']} ({cl['top1_percent']}%) top5 {cl['top5']}")
        warm = [k for k in keys if k["id"] != "c001"]
        print(f"   process_ms {stats([k['process_ms'] for k in warm])}")
        for counter in ("beam_evaluations", "gram_lookups", "ngram_lookups", "system_entries"):
            values = [k.get("work", {}).get(counter, 0) for k in warm]
            if any(values):
                print(f"   {counter:16} {stats(values)}")
    base = {(s["id"], s["keys"]): s for s in runs[0][1] if s["stage"] == "key_immediate" and s["clause_endpoint"]}
    for name, (_, snapshots, _) in zip(names[1:], runs[1:]):
        gained, lost = [], []
        for s in snapshots:
            if s["stage"] != "key_immediate" or not s["clause_endpoint"]:
                continue
            b = base[(s["id"], s["keys"])]
            if b["rank"] != 1 and s["rank"] == 1:
                gained.append((s["target"], b["first"], b["rank"]))
            elif b["rank"] == 1 and s["rank"] != 1:
                lost.append((s["target"], s["first"], s["rank"]))
        print(f"\n## {names[0]} -> {name}: clause top-1 gained {len(gained)}, lost {len(lost)}")
        for target, first, rank in lost:
            print(f"   LOST   {target}  now {first} (target rank {rank})")
        for target, first, rank in gained[:40]:
            print(f"   gained {target}  was {first} (target rank {rank})")


if __name__ == "__main__":
    main()
