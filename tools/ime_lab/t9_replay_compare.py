"""Compare complete, isolated T9 replay runs without equating mismatch with nonsense.

Runs contain fixture.json, replay.jsonl, and a format-1 run.json manifest. Raw
evidence is validated again; summary files are never trusted as candidate data.
Engine binaries and decoding settings may change, but the input and observation
protocol must match. Exit 1 means a regression, execution issue, or a previously
reviewed bad first candidate; exit 2 means invalid/incompatible evidence.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

import summarize_daily_chat as audit


DEFAULT_ASSERTIONS = Path(__file__).parent / "fixtures" / "daily_chat_20261003.known-bad.json"
IDENTITY_FIELDS = ("id", "mode", "stage", "keys", "variant")
PROTOCOL_FIELDS = ("key_delay_ms", "row_limit", "boundary_limit", "settle_timeout_ms", "modes", "variant")
REQUIRED_PASSES = ["digits:continuous", "digits:paused"]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def event_key(row):
    audit.require(isinstance(row, dict), "Comparison event must be an object")
    for field in IDENTITY_FIELDS:
        audit.require(isinstance(row.get(field), str) and row[field],
                      f"Comparison event has missing/invalid {field}")
    return tuple(row[field] for field in IDENTITY_FIELDS)


def event_identity(row):
    return {field: row[field] for field in IDENTITY_FIELDS}


def index_snapshots(rows):
    result = {}
    for row in rows:
        key = event_key(row)
        audit.require(key not in result, f"Duplicate comparison event: {key}")
        result[key] = row
    return result


def load_run(directory):
    """Validate hashes, protocol declarations, and all expected raw snapshots."""
    try:
        return _load_run(Path(directory))
    except (KeyError, TypeError, AttributeError, IndexError) as error:
        # The legacy evidence reader predates strict object validation. Surface
        # malformed nested data through the same contract as an invalid hash.
        raise audit.EvidenceError(f"{directory}: malformed run evidence: {error}") from error


def require_digest(value, label):
    audit.require(isinstance(value, str) and re.fullmatch(r"[0-9a-fA-F]{64}", value) is not None,
                  f"{label}: missing/invalid SHA256")


def validate_fingerprints(directory, manifest, metadata, protocol):
    """Bind runner-observed inputs to provenance, retaining legacy Android logs."""
    engine = manifest.get("engine", {})
    audit.require(isinstance(engine, dict), f"{directory}: engine must be an object")
    for observed, field in (("resources_sha256", "resources_sha256"), ("apk_sha256", "apk_sha256")):
        if observed not in metadata:
            continue
        require_digest(metadata[observed], f"{directory}: metadata.{observed}")
        audit.require(metadata[observed] == engine.get(field),
                      f"{directory}: metadata.{observed} differs from engine.{field}")
    if "corpus_sha256" in metadata:
        require_digest(metadata["corpus_sha256"], f"{directory}: metadata.corpus_sha256")
        audit.require(metadata["corpus_sha256"] == manifest.get("fixture_sha256"),
                      f"{directory}: metadata.corpus_sha256 differs from fixture_sha256")
    if "library_sha256" in metadata:
        libraries = metadata["library_sha256"]
        audit.require(isinstance(libraries, dict) and libraries,
                      f"{directory}: metadata.library_sha256 must be a nonempty object")
        for name, value in libraries.items():
            audit.require(isinstance(name, str) and name, f"{directory}: invalid library name")
            require_digest(value, f"{directory}: metadata.library_sha256.{name}")
        audit.require(libraries == engine.get("libraries"),
                      f"{directory}: metadata.library_sha256 differs from engine.libraries")
    runtime = protocol.get("runtime")
    if runtime is not None:
        audit.require(isinstance(runtime, str) and runtime, f"{directory}: invalid protocol_settings.runtime")
    observed_runtime = metadata.get("runtime")
    if "runtime" in metadata:
        audit.require(isinstance(observed_runtime, str) and observed_runtime,
                      f"{directory}: invalid metadata.runtime")
        # A runner can declare the portable host family while its wrapper names
        # the host platform. That platform remains part of comparison settings.
        matches = runtime == observed_runtime or (observed_runtime == "host" and
                                                  isinstance(runtime, str) and runtime.startswith("host-"))
        audit.require(matches, f"{directory}: metadata.runtime differs from protocol_settings.runtime")
    elif isinstance(runtime, str) and (runtime == "host" or runtime.startswith("host-")):
        raise audit.EvidenceError(f"{directory}: host protocol requires runner metadata.runtime")
    # Strategies introduced by a host runner must agree when both layers record
    # them. The complete protocol object (including extra keys) is compared later.
    for field in protocol.keys() - set(PROTOCOL_FIELDS) - {"runtime"}:
        if field in metadata:
            audit.require(metadata[field] == protocol[field],
                          f"{directory}: replay metadata differs from protocol_settings.{field}")


def _load_run(directory):
    directory = Path(directory)
    manifest = audit.read_json(directory / "run.json")
    audit.require(isinstance(manifest, dict), f"{directory}: run.json must be an object")
    audit.require(manifest.get("format_version") == 1, f"{directory}: unsupported run format")
    audit.require(manifest.get("status") == "complete", f"{directory}: run is not complete")
    audit.require(manifest.get("user_state") == "fresh_empty" and manifest.get("personalization") is False,
                  f"{directory}: comparison requires an isolated empty user dictionary without personalization")
    audit.require(manifest.get("passes") == REQUIRED_PASSES,
                  f"{directory}: both standard digits passes are required in protocol order")
    for filename, field in (("fixture.json", "fixture_sha256"), ("replay.jsonl", "replay_sha256")):
        audit.require(manifest.get(field) == sha256(directory / filename),
                      f"{directory}: {filename} hash differs from run.json")
    fixture = audit.load_fixture(directory / "fixture.json")
    records, sources = audit.read_evidence([directory / "replay.jsonl"])
    metadata = sources[0]["metadata"]
    protocol = manifest.get("protocol_settings")
    audit.require(isinstance(protocol, dict), f"{directory}: missing protocol_settings")
    for field in PROTOCOL_FIELDS:
        audit.require(field in protocol and metadata.get(field) == protocol[field],
                      f"{directory}: replay metadata differs from protocol_settings.{field}")
    audit.require(protocol["variant"] == "digits" and protocol["modes"] == ["continuous", "paused"],
                  f"{directory}: standard comparison requires digits continuous,paused")
    validate_fingerprints(directory, manifest, metadata, protocol)
    report, snapshots = audit.summarize(fixture, records, REQUIRED_PASSES)
    issues = [{"pass": name, **issue} for name, result in report["passes"].items()
              for issue in result["execution_issues"]]
    return {"directory": str(directory), "manifest": manifest, "fixture": fixture,
            "metadata": metadata, "ready": sources[0]["ready"], "report": report,
            "snapshots": snapshots, "execution_issues": issues}


def load_assertions(path, fixture_sha256):
    """Load context-bound reviewed judgments; never use a global text blacklist."""
    if path is None:
        if not DEFAULT_ASSERTIONS.is_file():
            return None
        assertions = audit.read_json(DEFAULT_ASSERTIONS)
        audit.require(isinstance(assertions, dict), "Known-bad assertions must be an object")
        if assertions.get("fixture_sha256") != fixture_sha256:
            return None
    else:
        assertions = audit.read_json(Path(path))
    audit.require(isinstance(assertions, dict), "Known-bad assertions must be an object")
    audit.require(assertions.get("format_version") == 1, "Unsupported known-bad assertion format")
    audit.require(assertions.get("fixture_sha256") == fixture_sha256,
                  "Known-bad assertions belong to a different fixture hash")
    audit.require(isinstance(assertions.get("assertions"), list), "Missing known-bad assertions")
    return assertions


def check_assertions(snapshots, assertions):
    """Return exact reviewed failures. Different first text still needs review."""
    indexed = index_snapshots(snapshots)
    violations, cleared, seen = [], [], set()
    for assertion in assertions["assertions"] if assertions else []:
        key = event_key(assertion)
        audit.require(key in indexed, f"Known-bad assertion has no matching replay event: {key}")
        audit.require(isinstance(assertion.get("bad_first"), str) and assertion["bad_first"],
                      f"Invalid known-bad candidate: {key}")
        signature = (*key, assertion["bad_first"])
        audit.require(signature not in seen, f"Duplicate known-bad assertion: {signature}")
        seen.add(signature)
        row = indexed[key]
        item = {**event_identity(row), "expected_prefix": row["expected_prefix"],
                "bad_first": assertion["bad_first"], "actual_first": row["first"],
                "reason": assertion.get("reason", ""), "target_exact": row["top_exact"]}
        (violations if row["first"] == assertion["bad_first"] else cleared).append(item)
    return {"assertion_count": len(seen), "violations": violations, "cleared": cleared,
            "language_quality": "partial_review_only" if assertions else "not_assessed",
            "note": "Only the specified first candidate at the specified input event is classified. "
                    "Avoiding it does not establish natural language quality or correct direct output.",
            "exit_code": 1 if violations else 0}


def evaluate_known_bad(run_dir, assertions_path=None):
    run = load_run(run_dir)
    assertions = load_assertions(assertions_path, run["manifest"]["fixture_sha256"])
    return check_assertions(run["snapshots"], assertions)


def compare_snapshots(baseline, candidate):
    """Compare matching validated events; rank changes apply only at syllable ends."""
    before, after = index_snapshots(baseline), index_snapshots(candidate)
    audit.require(before.keys() == after.keys(), "Replay event identities differ between runs")
    regressions, improvements, top_changes, candidate_changes, pending = [], [], [], [], []
    for key, old in before.items():
        new = after[key]
        audit.require(old["expected_prefix"] == new["expected_prefix"] and
                      old["syllable_boundary"] == new["syllable_boundary"],
                      f"Replay target/boundary differs at {key}")
        detail = {**event_identity(new), "expected_prefix": new["expected_prefix"],
                  "syllable_boundary": new["syllable_boundary"], "before_first": old["first"],
                  "after_first": new["first"], "before_rank": old["rank"], "after_rank": new["rank"]}
        if old["first"] != new["first"]:
            top_changes.append(detail)
            if not new["top_exact"]:
                pending.append({**detail, "language_quality": "review_required"})
        old_texts = [row["text"] for row in old["rows"]]
        new_texts = [row["text"] for row in new["rows"]]
        if old_texts != new_texts:
            candidate_changes.append({**detail, "before_candidates": old_texts, "after_candidates": new_texts})
        if not new["syllable_boundary"]:
            continue
        old_rank, new_rank = old["rank"], new["rank"]
        worse, better = [], []
        for limit in (1, 5, 100):
            was_hit, now_hit = 0 < old_rank <= limit, 0 < new_rank <= limit
            if was_hit and not now_hit:
                worse.append(f"top{limit}_lost")
            elif now_hit and not was_hit:
                better.append(f"top{limit}_gained")
        if old_rank > 0 and new_rank == -1:
            worse.append("target_recall_lost")
        elif old_rank == -1 and new_rank > 0:
            better.append("target_recall_gained")
        elif old_rank > 0 and new_rank > 0:
            if new_rank > old_rank:
                worse.append("target_rank_worsened")
            elif new_rank < old_rank:
                better.append("target_rank_improved")
        if worse:
            regressions.append({**detail, "reasons": worse})
        if better:
            improvements.append({**detail, "reasons": better})
    return {"event_count": len(after), "regressions": regressions, "improvements": improvements,
            "top_changes": top_changes, "candidate_list_changes": candidate_changes,
            "changed_first_review_pending": pending}


def compare_runs(baseline_dir, candidate_dir, assertions_path=None):
    baseline, candidate = load_run(baseline_dir), load_run(candidate_dir)
    for field in ("fixture_sha256", "protocol_settings", "passes", "user_state", "personalization"):
        audit.require(baseline["manifest"][field] == candidate["manifest"][field],
                      f"Incompatible comparison: {field} differs")
    result = compare_snapshots(baseline["snapshots"], candidate["snapshots"])
    assertions = load_assertions(assertions_path, candidate["manifest"]["fixture_sha256"])
    known_bad = check_assertions(candidate["snapshots"], assertions)
    baseline_known_bad = check_assertions(baseline["snapshots"], assertions)
    result.update(format_version=1, status="complete", baseline=str(baseline_dir), candidate=str(candidate_dir),
                  fixture_sha256=candidate["manifest"]["fixture_sha256"], known_bad=known_bad,
                  baseline_known_bad_count=len(baseline_known_bad["violations"]),
                  execution_issues={"baseline": baseline["execution_issues"], "candidate": candidate["execution_issues"]},
                  provenance={name: {"engine": run["manifest"].get("engine"), "ready": run["ready"],
                                     "replay_sha256": run["manifest"]["replay_sha256"]}
                              for name, run in (("baseline", baseline), ("candidate", candidate))},
                  physical_acceptance="未验收",
                  language_quality="Known reviewed assertions only; other outputs are not automatically classified.",
                  baseline_definition="A measured comparison reference, not a claim that the baseline meets quality requirements.")
    result["counts"] = {field: len(result[field]) for field in
                        ("regressions", "improvements", "top_changes", "candidate_list_changes", "changed_first_review_pending")}
    result["counts"]["known_bad_violations"] = len(known_bad["violations"])
    result["exit_code"] = int(bool(result["regressions"] or known_bad["violations"] or
                                   baseline["execution_issues"] or candidate["execution_issues"]))
    result["gate"] = "failed" if result["exit_code"] else "no_detected_regression"
    return result


def write_comparison(output_dir, result):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "comparison.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if result.get("status") != "complete":
        lines = ["# 九键回放比较无效", "", str(result.get("error", "Evidence validation failed"))]
    else:
        counts = result["counts"]
        lines = ["# 九键回放回归比较", "", f"结果：{result['gate']}；真机未验收。", "",
                 f"比较 {result['event_count']} 个逐键/等待后事件；退步 {counts['regressions']}，"
                 f"改善 {counts['improvements']}，已审定异常首选 {counts['known_bad_violations']}。", "",
                 f"首选变化 {counts['top_changes']} 项，其中 {counts['changed_first_review_pending']} 项需语言质量审阅。", "",
                 "基线只是对照记录，不代表质量合格。未出现已知坏首选，也不代表新候选自然或原文直出。", "",
                 "完整差异、原始候选和判定原因见 comparison.json。"]
    (output_dir / "comparison.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--assertions", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = compare_runs(args.baseline, args.candidate, args.assertions)
    except (audit.EvidenceError, OSError, KeyError, TypeError, ValueError) as error:
        result = {"status": "invalid", "exit_code": 2, "error": str(error)}
    write_comparison(args.output_dir, result)
    print(json.dumps({key: result[key] for key in ("status", "exit_code", "gate", "counts", "error") if key in result},
                     ensure_ascii=False, indent=2))
    return result["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
