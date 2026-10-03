"""Check the single-key recall fixture against validated production replay data.

Run the normal t9_replay.py entry with fixtures/single_key_recall_20261003.json,
then pass its output directory here. These assertions never enter the engine.
"""

import argparse
import json
from pathlib import Path

from t9_replay_compare import load_run, sha256


FIXTURE = Path(__file__).parent / "fixtures" / "single_key_recall_20261003.json"


def check(directory):
    run = load_run(directory)
    if run["manifest"]["fixture_sha256"] != sha256(FIXTURE):
        raise ValueError("Use the unchanged single_key_recall_20261003.json fixture")
    failures = list(run["execution_issues"])
    first_by_key = {"2": "啊", "3": "的", "6": "哦"}
    samples = [row for row in run["snapshots"] if row["keys"] in first_by_key]
    if not samples:
        raise ValueError("Single-key 3 observations are missing")
    for row in samples:
        identity = {key: row[key] for key in ("id", "mode", "stage", "keys")}
        expected_first = first_by_key[row["keys"]]
        if row["first"] != expected_first:
            failures.append({**identity, "expected_first": expected_first, "actual_first": row["first"]})
        # Full e cases record up to 100 candidates. Keep all three letter
        # branches reachable even though the initial-group default is 的.
        if row["keys"] == "3" and row["syllable_boundary"]:
            texts = [candidate["text"] for candidate in row["rows"]]
            if "额" not in texts[:10]:
                failures.append({**identity, "expected_top10": "额",
                                 "actual_rank": texts.index("额") + 1 if "额" in texts else -1})
            comments = [candidate["comment"] for candidate in row["rows"]]
            for branch in ("d", "e", "f"):
                recalled = ("e" in comments if branch == "e" else
                            any(comment.startswith(branch) for comment in comments))
                if not recalled:
                    failures.append({**identity, "missing_letter_branch": branch})
    return {"samples": len(samples), "failures": failures, "exit_code": 1 if failures else 0}


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
