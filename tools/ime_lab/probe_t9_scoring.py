"""Prepare/analyze a fixed-batch ONNX mask-position diagnostic, without decoding."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import statistics

from analyze_t9_weights import features, identity, sha


def write_new(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        if isinstance(value, str):
            stream.write(value)
        else:
            stream.write(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def prepare(run_dir, output, vocab_path=None):
    manifest = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))
    fixture_path, replay_path = run_dir / "fixture.json", run_dir / "replay.jsonl"
    if manifest["status"] != "complete" or manifest["fixture_sha256"] != sha(fixture_path) or manifest["replay_sha256"] != sha(replay_path):
        raise ValueError("Incomplete or changed replay")
    vocab_path = vocab_path or run_dir / "native-run/user/t9_sentence.vocab"
    # Only split LF, like the native scorer: U+2028/U+2029 are vocabulary tokens.
    vocabulary = vocab_path.read_text(encoding="utf-8").split("\n")
    if vocabulary[-1] == "":
        vocabulary.pop()
    vocabulary = [token.removesuffix("\r") for token in vocabulary]
    if len(vocabulary) != 21128 or vocabulary[103] != "[MASK]":
        raise ValueError("Unexpected vocabulary")
    vocab = set(vocabulary)
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    body = {row["id"] for row in fixture["cases"] if row.get("is_body")}
    with replay_path.open(encoding="utf-8") as stream:
        endpoints = [row for line in stream if (row := json.loads(line)).get("id") in body and
                     row["mode"] == "continuous" and row["clause_endpoint"]]
    immediate = {row["id"]: row for row in endpoints if row["stage"] == "key_immediate"}
    settled = [row for row in endpoints if row["stage"] == "clause_settled"]
    if len(settled) != len(body) or len(immediate) != len(body):
        raise ValueError("Incomplete body clause coverage")
    probes, samples = {}, []
    for event in settled:
        current = immediate[event["id"]]
        before = {identity(row): row for row in current["rows"] if row["type"] == "sentence"}
        sentences = [row for row in event["rows"] if row["type"] == "sentence"]
        if not 0 < len(sentences) <= 12:
            raise ValueError("Unexpected sentence candidate count")
        # The production request rejects the entire batch if *any* candidate is
        # outside the length range; one NaN token score also prevents refinement.
        eligible = all(5 <= len(row["text"]) <= 24 and all(ch in vocab for ch in row["text"]) for row in sentences)
        candidates = []
        for row in sentences:
            candidate = features(row, before)
            if candidate["base_weight"] is None:
                raise ValueError("Missing identical immediate sentence path")
            if eligible:
                probes.setdefault(row["text"], f"p{len(probes) + 1:05}")
                candidate["probe_id"] = probes[row["text"]]
            candidates.append(candidate)
        samples.append({"id": event["id"], "target": event["target"], "first": event["rows"][0]["text"],
                        "immediate_first": current["rows"][0]["text"], "first_type": event["rows"][0]["type"],
                        "target_rank": event["target_prefix_rank"], "eligible": eligible,
                        "candidate_count": len(sentences), "candidates": candidates})
    text = "".join(f"{key}\t{value}\n" for value, key in probes.items())
    metadata = {"format_version": 1, "method": "frozen_candidates_onnx_mask_position_probe",
                "fixture_sha256": sha(fixture_path), "replay_sha256": sha(replay_path),
                "vocab_sha256": sha(vocab_path), "run_dir": str(run_dir.resolve()),
                "conditions": "Every candidate in a clause must contain 5–24 known vocabulary characters; otherwise whole clause remains native baseline. No commit or preceding-text context in this fixture.",
                "deduplication": "Exact text globally deduplicated, equivalent input to per-text native score cache; three diagnostic strategies always evaluated independently.",
                "strategies": {"tail4": "n-3,n-2,n-1,n", "head4": "1,2,3,4", "spread4": "1 + floor(k*(n-1)/3), k=0,1,2,3"},
                "unique_text_count": len(probes), "expected_onnx_calls": 3 * len(probes),
                "samples": samples}
    output.mkdir(parents=True, exist_ok=True)
    if (output / "probe-input.tsv").exists() or (output / "metadata.json").exists():
        raise FileExistsError("Probe inputs already exist")
    write_new(output / "probe-input.tsv", text)
    metadata["probe_input_sha256"] = sha(output / "probe-input.tsv")
    write_new(output / "metadata.json", metadata)
    print(json.dumps({"cases": len(samples), "eligible_cases": sum(x["eligible"] for x in samples),
                      "unique_texts": len(probes), "onnx_calls": len(probes) * 3, "output": str(output)}, ensure_ascii=False))


def choose(sample, scores, strategy, coefficient):
    if not sample["eligible"] or sample["first_type"] != "sentence":
        return sample["first"]
    return max(enumerate(sample["candidates"]),
               key=lambda item: (item[1]["base_weight"] + coefficient * scores[(item[1]["probe_id"], strategy)][0], -item[0]))[1]["text"]


def measure(samples, scores, strategy, coefficient):
    changes = []
    correct = fixed = regressed = 0
    for sample in samples:
        candidate = choose(sample, scores, strategy, coefficient)
        before, after = sample["first"] == sample["target"], candidate == sample["target"]
        correct += after
        fixed += not before and after
        regressed += before and not after
        if candidate != sample["first"]:
            changes.append({"id": sample["id"], "target": sample["target"], "before": sample["first"],
                            "after": candidate, "fixed": not before and after, "regressed": before and not after})
    return {"strategy": strategy, "coefficient": coefficient, "cases": len(samples), "correct": correct,
            "fixed": fixed, "regressed": regressed, "changed": len(changes), "changes": changes}


def timings(values):
    ordered = sorted(values)
    return {"calls": len(values), "total_ms": sum(values) / 1000,
            "median_ms": statistics.median(values) / 1000,
            "p95_ms": ordered[math.ceil(len(values) * .95) - 1] / 1000,
            "max_ms": max(values) / 1000, "min_ms": min(values) / 1000}


def analyze(probe_dir, raw_output):
    metadata = json.loads((probe_dir / "metadata.json").read_text(encoding="utf-8"))
    if metadata["probe_input_sha256"] != sha(probe_dir / "probe-input.tsv"):
        raise ValueError("Probe input changed")
    scores = {}
    for line in raw_output.read_text(encoding="utf-8").splitlines():
        probe_id, strategy, score, micros = line.split("\t")
        key = probe_id, strategy
        if key in scores or strategy not in metadata["strategies"]:
            raise ValueError("Duplicate/unknown score")
        value, duration = float(score), int(micros)
        if not math.isfinite(value) or duration < 0:
            raise ValueError("Invalid score or timing")
        scores[key] = value, duration
    expected = {(row["probe_id"], strategy) for sample in metadata["samples"] if sample["eligible"]
                for row in sample["candidates"] for strategy in metadata["strategies"]}
    if scores.keys() != expected:
        raise ValueError("Missing or extra probe outputs")
    samples = metadata["samples"]
    tail = measure(samples, scores, "tail4", .25)
    residuals = [{"id": sample["id"], "text": row["text"],
                  "error": row["base_weight"] + .25 * scores[(row["probe_id"], "tail4")][0] - row["settled_weight"]}
                 for sample in samples if sample["eligible"] for row in sample["candidates"]]
    evaluations = [measure(samples, scores, strategy, coefficient)
                   for strategy in metadata["strategies"] for coefficient in (0, .125, .25, .5, 1, 2)]
    result = {"method": metadata["method"], "metadata_sha256": sha(probe_dir / "metadata.json"),
              "probe_output_sha256": sha(raw_output), "cases": len(samples),
              "eligible_cases": sum(sample["eligible"] for sample in samples),
              "unique_texts": metadata["unique_text_count"], "total_onnx_calls": len(scores),
              "baseline_correct": sum(sample["first"] == sample["target"] for sample in samples),
              "captured_target_recall": sum(sample["target_rank"] > 0 for sample in samples),
              "tail4_validation": {"first_candidate_mismatches": tail["changed"], "details": tail["changes"],
                                   "max_abs_score_residual": max(abs(row["error"]) for row in residuals),
                                   "score_residuals_over_1e_minus_5": [row for row in residuals if abs(row["error"]) > 1e-5]},
              "evaluations": evaluations,
              "timings": {strategy: timings([duration for (_, kind), (_, duration) in scores.items() if kind == strategy])
                          for strategy in metadata["strategies"]},
              "limitations": ["Model inference is real; candidate selection changes are offline simulations on frozen survivors, not end-to-end decoding.",
                              "Timing covers uncached Session.Run only: excludes model loading, debounce, queueing, dictionary and decoder cost; host timings are not mobile benchmarks.",
                              "Three positions strategies each use batch four and same candidate text lengths. Positions depend on text length only, preserving a text-stable cache key; candidate-difference masking would require a different cache contract.",
                              "No unknown replacement has been certified natural language. Training/held-out broad-corpus validation still required."]}
    write_new(probe_dir / "analysis.json", result)
    print(json.dumps({key: value for key, value in result.items() if key not in ("evaluations", "limitations")}, ensure_ascii=False, indent=2))
    print(json.dumps([{k: v for k, v in row.items() if k != "changes"} for row in evaluations], ensure_ascii=False, indent=2))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    command = sub.add_parser("prepare")
    command.add_argument("run_directory", type=Path)
    command.add_argument("--output", type=Path, required=True)
    command.add_argument("--vocab", type=Path)
    command = sub.add_parser("analyze")
    command.add_argument("probe_directory", type=Path)
    command.add_argument("--scores", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.command == "prepare":
        prepare(args.run_directory, args.output, args.vocab)
    else:
        analyze(args.probe_directory, args.scores)


if __name__ == "__main__":
    main()
