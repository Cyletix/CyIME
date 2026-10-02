"""Offline, fixed-candidate weight sensitivity study; never runs an engine/device.

No candidate is generated and no dictionary is changed. Oracle recall is a bound
for these captured candidates, not for a decoder rerun after dictionary changes.
The linear experiments rescore existing Sentence paths only, preserving every
non-Sentence slot. A sentence's components and code must match across snapshots
before the settled-minus-immediate difference is treated as neural contribution.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import itertools
import json
from pathlib import Path


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def identity(row):
    return row["text"], row.get("code"), row.get("components")


def components(row):
    result = []
    for part in row.get("components", "").split(";"):
        if part:
            text, weight, coverage = part.rsplit(":", 2)
            result.append((text, float(weight), float(coverage)))
    return result


def features(row, immediate):
    words = components(row)
    base = immediate.get(identity(row))
    return {
        "text": row["text"], "word_count": len(words),
        "character_count": len(row["text"]),
        "dictionary_log_weight": sum(word[1] for word in words),
        "settled_weight": float(row["weight"]),
        "base_weight": float(base["weight"]) if base else None,
        "neural_weighted_delta": float(row["weight"]) - float(base["weight"]) if base else None,
        "components": words,
    }


def pick(sample, parameters):
    # Keep non-Sentence candidates in their original slots; this isolates the
    # shared native Sentence ordering and does not pretend to emulate filters.
    if sample["first_type"] != "sentence" or not sample["candidates"]:
        return sample["first"]
    penalty, alpha, neural_scale, dictionary_scale = parameters
    def score(item):
        row = item[1]
        value = row["settled_weight"] - (penalty - 8.0) * row["word_count"]
        value += (dictionary_scale - 1.0) * row["dictionary_log_weight"]
        if neural_scale != 1.0:
            if row["neural_weighted_delta"] is None:
                raise ValueError("Cannot reconstruct neural score for unmatched path")
            value += (neural_scale - 1.0) * row["neural_weighted_delta"]
        value /= max(row["character_count"], 1) ** alpha
        # Preserve captured order on ties rather than inventing unavailable priors.
        return value, -item[0]
    return max(enumerate(sample["candidates"]), key=score)[1]["text"]


def evaluate(samples, parameters):
    correct = fixed = regressed = changed = 0
    details = []
    for sample in samples:
        chosen = pick(sample, parameters)
        before = sample["first"] == sample["target"]
        after = chosen == sample["target"]
        correct += after
        fixed += not before and after
        regressed += before and not after
        changed += chosen != sample["first"]
        if chosen != sample["first"]:
            details.append({"id": sample["id"], "target": sample["target"],
                            "before": sample["first"], "after": chosen,
                            "target_correct": after, "target_regressed": before and not after})
    return {"cases": len(samples), "correct": correct, "fixed": fixed,
            "regressed": regressed, "changed": changed, "changes": details}


def parameter_dict(parameters):
    return dict(zip(("word_penalty", "character_normalization_power", "neural_scale", "dictionary_log_weight_scale"), parameters))


def select_best(scored, zero_regressions=False):
    pool = [item for item in scored if not zero_regressions or not item[1]["regressed"]]
    return max(pool, key=lambda item: (item[1]["correct"], -item[1]["regressed"],
                                      -item[1]["changed"], -abs(item[0][0] - 8),
                                      -item[0][1], -abs(item[0][2] - 1), -abs(item[0][3] - 1)))


def describe(item):
    parameters, result = item
    return {"parameters": parameter_dict(parameters), **result}


def rank_detail(event):
    return {"id": event["id"], "target": event["expected_prefix"],
            "first": event["rows"][0]["text"] if event["rows"] else "",
            "rank": event["target_prefix_rank"],
            "sentence_candidates": sum(row.get("type") == "sentence" for row in event["rows"])}


def recall(events):
    return {"events": len(events), "top1": sum(row["target_prefix_rank"] == 1 for row in events),
            "top5": sum(0 < row["target_prefix_rank"] <= 5 for row in events),
            "captured_recall": sum(row["target_prefix_rank"] > 0 for row in events),
            "not_in_capture": sum(row["target_prefix_rank"] <= 0 for row in events)}


def analyze(run_dir, known_bad_path):
    fixture_path, replay_path = run_dir / "fixture.json", run_dir / "replay.jsonl"
    manifest = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))
    if manifest.get("status") != "complete":
        raise ValueError("Replay must be complete")
    for key, path in (("fixture_sha256", fixture_path), ("replay_sha256", replay_path)):
        if manifest[key] != sha(path):
            raise ValueError(f"Hash mismatch: {path}")
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    cases = {case["id"]: case for case in fixture["cases"]}
    body_ids = {key for key, value in cases.items() if value.get("is_body")}
    with replay_path.open(encoding="utf-8") as stream:
        rows = [json.loads(line) for line in stream]
    events = [row for row in rows if row.get("id") in body_ids]
    endpoints = [row for row in events if row["mode"] == "continuous" and row["stage"] == "clause_settled"]
    immediate = [row for row in events if row["mode"] == "continuous" and row["stage"] == "key_immediate" and row["clause_endpoint"]]
    prefix_now = [row for row in events if row["mode"] == "continuous" and row["stage"] == "key_immediate" and row["syllable_boundary"]]
    prefix_paused = [row for row in events if row["mode"] == "paused" and row["stage"] in ("syllable_settled", "clause_settled")]
    if len(endpoints) != len(body_ids) or len(immediate) != len(body_ids):
        raise ValueError("Incomplete body endpoint coverage")
    immediate_by_id = {row["id"]: row for row in immediate}
    samples, unmatched = [], []
    for event in endpoints:
        now = {identity(row): row for row in immediate_by_id[event["id"]]["rows"] if row.get("type") == "sentence"}
        candidates = [features(row, now) for row in event["rows"] if row.get("type") == "sentence"]
        unmatched.extend({"id": event["id"], "text": row["text"]} for row in candidates if row["base_weight"] is None)
        samples.append({"id": event["id"], "target": event["target"],
                        "first": event["rows"][0]["text"], "first_type": event["rows"][0]["type"],
                        "target_rank": event["target_prefix_rank"], "candidates": candidates})
    baseline = evaluate(samples, (8.0, 0.0, 1.0, 1.0))
    if baseline["changed"]:
        raise ValueError("Native sentence ordering not reconstructable from captured weights")
    # Coefficients are global; no text/id-specific feature is used.
    penalties = [-8, -4, 0, 2, 4, 6, 8, 10, 12, 16, 20]
    powers = [0, 0.25, 0.5, 0.75, 1]
    neural_scales = [0, 0.5, 1, 1.5, 2, 3, 4] if not unmatched else [1]
    dictionary_scales = [0, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2]
    grid = list(itertools.product(penalties, powers, neural_scales, dictionary_scales))
    scored = [(parameters, evaluate(samples, parameters)) for parameters in grid]
    word_only = [item for item in scored if item[0][1:] == (0, 1, 1)]
    neural_only = [item for item in scored if item[0][:2] == (8, 0) and item[0][3] == 1]
    norm_only = [item for item in scored if item[0][0] == 8 and item[0][2:] == (1, 1)]
    dictionary_only = [item for item in scored if item[0][:3] == (8, 0, 1)]
    # Deterministic chronology blocks: tune alternating utterance IDs, then apply
    # frozen coefficients to the other utterances. Adjacent clauses stay together.
    training = [sample for sample in samples if int(cases[sample["id"]]["utterance_id"][1:]) % 2]
    heldout = [sample for sample in samples if sample not in training]
    training_best = select_best([(p, evaluate(training, p)) for p in grid])
    known = json.loads(known_bad_path.read_text(encoding="utf-8"))
    if known["fixture_sha256"] != manifest["fixture_sha256"]:
        raise ValueError("Known-bad assertions refer to a different fixture")
    keyed = {(row["id"], row["mode"], row["stage"], row["keys"], row["variant"]): row for row in events}
    assertions = []
    for assertion in known["assertions"]:
        key = tuple(assertion[field] for field in ("id", "mode", "stage", "keys", "variant"))
        event = keyed[key]
        assertions.append({**rank_detail(event), "stage": event["stage"], "keys": event["keys"],
                           "bad_first": assertion["bad_first"], "still_bad": event["rows"][0]["text"] == assertion["bad_first"],
                           "clause_endpoint": event["clause_endpoint"]})
    missing = [rank_detail(event) for event in endpoints if event["target_prefix_rank"] <= 0]
    missing_ids = {event["id"] for event in missing}
    bad_ids = {row["id"] for row in assertions}
    best = select_best(scored)
    no_regression = select_best(scored, True)
    return {
        "method": "offline_frozen_candidate_sensitivity_only",
        "inputs": {"run_directory": str(run_dir.resolve()), "fixture_sha256": sha(fixture_path),
                   "replay_sha256": sha(replay_path), "known_bad_sha256": sha(known_bad_path)},
        "limitations": [
            "No engine rerun: changing dictionary weights or beam scoring may alter candidates, so this is not an actual decoded quality result.",
            "Oracle captured recall is achievable only by a perfect selector and is not a measured attainable tuning result; missing means absent from captured top100, not absent from the dictionary.",
            "Exact-target accuracy does not measure whether every alternative is normal language; changing a known-bad first candidate alone does not certify its replacement.",
            "No production latency benchmark. Rescoring a fixed small candidate list is bounded, but changing beam pruning can affect decoder runtime.",
            "Training and held-out subsets are from one short conversation; this is descriptive sensitivity analysis, not generalization evidence.",
            "Candidate weights are logged to six decimal places; ties preserve the original order. Non-Sentence candidate slots are frozen; filters are not rerun.",
        ],
        "formula": "(settled_weight - (word_penalty-8)*word_count + (neural_scale-1)*(settled_weight-immediate_weight) + (dictionary_scale-1)*sum(component_dictionary_log_weight)) / characters**normalization_power",
        "recall": {"body_clauses_immediate": recall(immediate), "body_clauses_settled": recall(endpoints),
                   "body_character_boundaries_immediate": recall(prefix_now), "body_character_boundaries_paused": recall(prefix_paused)},
        "sentence_candidate_count_distribution": dict(Counter(len(sample["candidates"]) for sample in samples)),
        "known_bad": {"assertions": len(assertions), "still_bad": sum(row["still_bad"] for row in assertions),
                      "case_count": len(bad_ids), "endpoint_absent_cases": sorted(bad_ids & missing_ids),
                      "endpoint_recalled_cases": sorted(bad_ids - missing_ids),
                      "target_recalled_at_syllable_boundary_assertion": sum(row["rank"] is not None and row["rank"] > 0 for row in assertions),
                      "non_syllable_boundary_assertions": sum(row["rank"] is None for row in assertions), "details": assertions},
        "missing_target_endpoints": missing,
        "matching": {"unmatched_sentence_paths": unmatched},
        "baseline": baseline,
        "grid": {"word_penalties": penalties, "normalization_powers": powers,
                 "neural_scales": neural_scales, "dictionary_log_weight_scales": dictionary_scales,
                 "combinations": len(grid)},
        "word_penalty_only": [describe(item) for item in word_only],
        "normalization_only": [describe(item) for item in norm_only],
        "neural_scale_only": [describe(item) for item in neural_only],
        "dictionary_scale_only": [describe(item) for item in dictionary_only],
        "best_joint_in_sample": describe(best),
        "best_joint_no_original_top1_regression": describe(no_regression),
        "split_study": {"split": "odd-numbered utterances tune, even-numbered utterances held out",
                        "training_baseline": evaluate(training, (8, 0, 1, 1)),
                        "training_best": describe(training_best),
                        "heldout_baseline": evaluate(heldout, (8, 0, 1, 1)),
                        "heldout_frozen_training_parameters": evaluate(heldout, training_best[0])},
        "samples": samples,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_directory", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--known-bad", type=Path, default=Path(__file__).with_name("fixtures") / "daily_chat_20261003.known-bad.json")
    args = parser.parse_args(argv)
    result = analyze(args.run_directory, args.known_bad)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "weights.json").open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: result[key] for key in ("recall", "baseline", "best_joint_in_sample", "best_joint_no_original_top1_regression", "split_study")}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
