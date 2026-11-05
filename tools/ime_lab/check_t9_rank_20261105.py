"""Check the 2026-11-05 nine-key ranking fixture against validated production replay data.

Run the normal t9_replay.py entry with fixtures/t9_rank_20261105.json, then pass its output directory here.
A group user reported that a single 3 gave 额/嗯/饿 instead of 的, and that 58333 after 精防肩撞就 gave 觉的
before 觉得. These assertions only read the replay; they never enter the engine.
"""

import argparse
import json
from pathlib import Path

from t9_replay_compare import load_run, sha256


FIXTURE = Path(__file__).parent / "fixtures" / "t9_rank_20261105.json"
# Single keys: the expected first candidate whatever the text before it.
FIRST_BY_KEY = {"3": "的", "2": "啊", "6": "哦"}


def check(directory):
    run = load_run(directory)
    if run["manifest"]["fixture_sha256"] != sha256(FIXTURE):
        raise ValueError("Use the unchanged t9_rank_20261105.json fixture")
    failures = list(run["execution_issues"])
    single = [row for row in run["snapshots"] if row["keys"] in FIRST_BY_KEY]
    whole = [row for row in run["snapshots"] if row["keys"] == "58333"]
    if not single or not whole:
        raise ValueError("Single-key or 58333 observations are missing")
    for row in single:
        identity = {key: row[key] for key in ("id", "mode", "stage", "keys")}
        expected = FIRST_BY_KEY[row["keys"]]
        if row["first"] != expected:
            failures.append({**identity, "expected_first": expected, "actual_first": row["first"]})
        # The complete vowel stays reachable right behind 的 (额 / 饿 / 呃 …, comment e).
        if row["keys"] == "3":
            comments = [candidate["comment"] for candidate in row["rows"][:3]]
            if "e" not in comments:
                failures.append({**identity, "expected_top3_comment": "e", "actual_comments": comments})
    for row in whole:
        identity = {key: row[key] for key in ("id", "mode", "stage", "keys")}
        if row["first"] != "觉得":
            failures.append({**identity, "expected_first": "觉得", "actual_first": row["first"]})
    return {"samples": len(single) + len(whole), "failures": failures, "exit_code": 1 if failures else 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        result = check(args.directory)
    except (ValueError, OSError) as error:
        print(json.dumps({"error": str(error), "exit_code": 2}, ensure_ascii=False))
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result["exit_code"]


if __name__ == "__main__":
    raise SystemExit(main())
