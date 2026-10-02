"""Tests of regression gates, protocol compatibility, and semantic assertion scope."""

from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

import summarize_daily_chat as audit
import t9_replay_compare as compare
from test_summarize_daily_chat import FIXTURE, sample


def set_candidates(row, texts):
    row["rows"] = [{"text": text} for text in texts]
    row["target_prefix_rank"] = next((i for i, text in enumerate(texts, 1)
                                     if text == row["expected_prefix"]), -1) if row["syllable_boundary"] else None
    row["top_exact"] = row["syllable_boundary"] and row["target_prefix_rank"] == 1


def write_run(path, mutate=None, protocol_change=None, engine_hash="engine-one"):
    path.mkdir()
    (path / "fixture.json").write_text(json.dumps(FIXTURE, ensure_ascii=False), encoding="utf-8")
    protocol = {"key_delay_ms": 35, "row_limit": 20, "boundary_limit": 100, "settle_timeout_ms": 5000,
                "modes": ["continuous", "paused"], "variant": "digits"}
    if protocol_change:
        protocol.update(protocol_change)
    continuous = [dict(row, mode="continuous") for row in sample() if row["stage"] != "syllable_settled"]
    rows = continuous + sample()
    if mutate:
        mutate(rows)
    data = [{"stage": "metadata", "protocol": 1, **protocol},
            {"stage": "ready", "schema": "t9_pinyin", "settings": {}}, *rows]
    data.append({"stage": "complete", "records_before_complete": len(data)})
    (path / "replay.jsonl").write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in data), encoding="utf-8")
    manifest = {"format_version": 1, "status": "complete", "user_state": "fresh_empty", "personalization": False,
                "passes": compare.REQUIRED_PASSES, "protocol_settings": protocol,
                "fixture_sha256": compare.sha256(path / "fixture.json"),
                "replay_sha256": compare.sha256(path / "replay.jsonl"), "engine": {"apk_sha256": engine_hash}}
    (path / "run.json").write_text(json.dumps(manifest), encoding="utf-8")
    return manifest


def write_manifest(path, **changes):
    manifest = audit.read_json(path / "run.json")
    manifest.update(changes)
    (path / "run.json").write_text(json.dumps(manifest), encoding="utf-8")


def add_host_fingerprints(path, runtime="host", **protocol_extra):
    manifest = audit.read_json(path / "run.json")
    manifest["engine"] = {"source": "host-source-build", "resources_sha256": "a" * 64,
                          "apk_sha256": "a" * 64, "resource_type": "resource_zip",
                          "libraries": {"librime_jni.so": "b" * 64, "libonnxruntime.so": "c" * 64}}
    manifest["protocol_settings"].update(runtime=runtime, **protocol_extra)
    rows = [json.loads(line) for line in (path / "replay.jsonl").read_text(encoding="utf-8").splitlines()]
    rows[0].update(runtime="host", resources_sha256="a" * 64, apk_sha256="a" * 64,
                   corpus_sha256=manifest["fixture_sha256"], library_sha256=manifest["engine"]["libraries"],
                   **protocol_extra)
    (path / "replay.jsonl").write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows), encoding="utf-8")
    manifest["replay_sha256"] = compare.sha256(path / "replay.jsonl")
    write_manifest(path, **manifest)
    return manifest


class ReplayComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.baseline = self.root / "baseline"
        self.candidate = self.root / "candidate"
        write_run(self.baseline)

    def test_same_evidence_has_no_regression_but_is_not_quality_acceptance(self):
        write_run(self.candidate)
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["exit_code"], 0)
        self.assertEqual(result["gate"], "no_detected_regression")
        self.assertEqual(result["physical_acceptance"], "未验收")
        self.assertIn("not a claim", result["baseline_definition"])

    def test_top1_loss_and_missing_target_are_regressions(self):
        def change(rows):
            set_candidates(rows[-1], ["拟好"])
        write_run(self.candidate, change)
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["exit_code"], 1)
        reasons = result["regressions"][0]["reasons"]
        self.assertIn("top1_lost", reasons)
        self.assertIn("target_recall_lost", reasons)
        self.assertEqual(result["counts"]["changed_first_review_pending"], 1)
        self.assertEqual(result["known_bad"]["violations"], [])

    def test_rank_worse_fails_even_when_top5_and_top100_totals_stay_equal(self):
        write_run(self.candidate, lambda rows: set_candidates(rows[1], ["泥", "尼", "你"]))
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["regressions"][0]["reasons"], ["target_rank_worsened"])
        self.assertEqual(result["exit_code"], 1)

    def test_improvement_does_not_cancel_regression_at_another_event(self):
        def change(rows):
            set_candidates(rows[1], ["你", "泥"])
            set_candidates(rows[-1], ["拟好", "你好"])
        write_run(self.candidate, change)
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["counts"]["improvements"], 1)
        self.assertEqual(result["counts"]["regressions"], 1)
        self.assertEqual(result["exit_code"], 1)

    def test_unfinished_syllable_change_is_pending_review_not_rank_regression(self):
        write_run(self.candidate, lambda rows: set_candidates(rows[0], ["不一样"]))
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["exit_code"], 0)
        self.assertEqual(result["counts"]["regressions"], 0)
        self.assertEqual(result["counts"]["changed_first_review_pending"], 1)

    def test_engine_changes_are_allowed_but_timing_changes_are_rejected(self):
        write_run(self.candidate, engine_hash="engine-two")
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertNotEqual(result["provenance"]["baseline"]["engine"], result["provenance"]["candidate"]["engine"])
        other = self.root / "other"
        write_run(other, protocol_change={"key_delay_ms": 50})
        with self.assertRaisesRegex(audit.EvidenceError, "protocol_settings differs"):
            compare.compare_runs(self.baseline, other)

    def test_hash_mismatch_and_personalized_run_are_rejected(self):
        write_run(self.candidate)
        write_manifest(self.candidate, replay_sha256="wrong")
        with self.assertRaisesRegex(audit.EvidenceError, "hash differs"):
            compare.load_run(self.candidate)
        write_manifest(self.candidate, replay_sha256=compare.sha256(self.candidate / "replay.jsonl"), personalization=True)
        with self.assertRaisesRegex(audit.EvidenceError, "without personalization"):
            compare.load_run(self.candidate)

    def test_manifest_limits_must_match_raw_metadata(self):
        manifest = write_run(self.candidate)
        manifest["protocol_settings"]["row_limit"] = 21
        write_manifest(self.candidate, **manifest)
        with self.assertRaisesRegex(audit.EvidenceError, "protocol_settings.row_limit"):
            compare.load_run(self.candidate)

    def test_host_fingerprints_match_and_legacy_android_remains_readable(self):
        write_run(self.candidate)
        add_host_fingerprints(self.candidate)
        self.assertEqual(compare.load_run(self.candidate)["metadata"]["runtime"], "host")
        self.assertNotIn("runtime", compare.load_run(self.baseline)["metadata"])
        with self.assertRaisesRegex(audit.EvidenceError, "protocol_settings differs"):
            compare.compare_runs(self.baseline, self.candidate)

    def test_observed_host_fingerprints_cannot_disagree_with_manifest(self):
        write_run(self.candidate)
        good = add_host_fingerprints(self.candidate)
        cases = [("resources_sha256", "d" * 64), ("apk_sha256", "d" * 64),
                 ("libraries", {"librime_jni.so": "d" * 64}), ("libraries", {})]
        for field, value in cases:
            with self.subTest(field=field, value=value):
                manifest = deepcopy(good)
                manifest["engine"][field] = value
                write_manifest(self.candidate, **manifest)
                with self.assertRaisesRegex(audit.EvidenceError, "differs from engine"):
                    compare.load_run(self.candidate)
        manifest = deepcopy(good)
        del manifest["engine"]["resources_sha256"]
        write_manifest(self.candidate, **manifest)
        with self.assertRaisesRegex(audit.EvidenceError, "resources_sha256"):
            compare.load_run(self.candidate)

    def test_observed_corpus_hash_must_identify_actual_fixture(self):
        write_run(self.candidate)
        add_host_fingerprints(self.candidate)
        rows = [json.loads(line) for line in (self.candidate / "replay.jsonl").read_text(encoding="utf-8").splitlines()]
        rows[0]["corpus_sha256"] = "f" * 64
        (self.candidate / "replay.jsonl").write_text("\n".join(json.dumps(row) for row in rows), encoding="utf-8")
        write_manifest(self.candidate, replay_sha256=compare.sha256(self.candidate / "replay.jsonl"))
        with self.assertRaisesRegex(audit.EvidenceError, "corpus_sha256 differs"):
            compare.load_run(self.candidate)

    def test_runtime_and_host_strategy_are_comparison_dimensions(self):
        write_run(self.candidate)
        add_host_fingerprints(self.baseline, execution_strategy="asynchronous")
        add_host_fingerprints(self.candidate, execution_strategy="barrier_per_key")
        with self.assertRaisesRegex(audit.EvidenceError, "protocol_settings differs"):
            compare.compare_runs(self.baseline, self.candidate)
        manifest = add_host_fingerprints(self.candidate, runtime="host-linux", execution_strategy="asynchronous")
        self.assertEqual(compare.load_run(self.candidate)["manifest"]["protocol_settings"]["runtime"], "host-linux")
        with self.assertRaisesRegex(audit.EvidenceError, "protocol_settings differs"):
            compare.compare_runs(self.baseline, self.candidate)
        manifest["protocol_settings"]["runtime"] = "android"
        write_manifest(self.candidate, **manifest)
        with self.assertRaisesRegex(audit.EvidenceError, "metadata.runtime differs"):
            compare.load_run(self.candidate)

    def test_protocol_cannot_silently_label_legacy_evidence_as_host(self):
        manifest = write_run(self.candidate)
        manifest["protocol_settings"]["runtime"] = "host"
        write_manifest(self.candidate, **manifest)
        with self.assertRaisesRegex(audit.EvidenceError, "requires runner metadata.runtime"):
            compare.load_run(self.candidate)

    def test_malformed_manifests_and_fixture_raise_evidence_errors(self):
        good = write_run(self.candidate)
        malformed = [[], None, {}, {**good, "protocol_settings": []}, {**good, "engine": []},
                     {key: value for key, value in good.items() if key != "fixture_sha256"}]
        for value in malformed:
            with self.subTest(value=value):
                (self.candidate / "run.json").write_text(json.dumps(value), encoding="utf-8")
                with self.assertRaises(audit.EvidenceError):
                    compare.load_run(self.candidate)
        for value in [{"cases": []}, {"cases": [None]}, {"cases": [{"id": "bad"}]}, []]:
            with self.subTest(fixture=value):
                (self.candidate / "fixture.json").write_text(json.dumps(value), encoding="utf-8")
                good["fixture_sha256"] = compare.sha256(self.candidate / "fixture.json")
                (self.candidate / "run.json").write_text(json.dumps(good), encoding="utf-8")
                with self.assertRaises(audit.EvidenceError):
                    compare.load_run(self.candidate)

    def test_complete_marker_cannot_hide_missing_event(self):
        write_run(self.candidate, lambda rows: rows.pop(0))
        with self.assertRaisesRegex(audit.EvidenceError, "coverage"):
            compare.load_run(self.candidate)

    def test_settlement_timeout_fails_comparison(self):
        def change(rows):
            rows[-1]["settle"]["timeout"] = True
        write_run(self.candidate, change)
        result = compare.compare_runs(self.baseline, self.candidate)
        self.assertEqual(result["exit_code"], 1)
        self.assertEqual(len(result["execution_issues"]["candidate"]), 1)

    def make_assertion(self, first="泥"):
        manifest = audit.read_json(self.baseline / "run.json")
        assertion = {"format_version": 1, "fixture_sha256": manifest["fixture_sha256"], "assertions": [
            {"id": "c001", "mode": "continuous", "stage": "key_immediate", "keys": "64", "variant": "digits",
             "bad_first": first, "reason": "Synthetic test assertion, not a real linguistic judgment."}]}
        path = self.root / "known.json"
        path.write_text(json.dumps(assertion, ensure_ascii=False), encoding="utf-8")
        return path

    def test_known_bad_still_fails_when_baseline_has_the_same_bad_output(self):
        write_run(self.candidate)
        result = compare.compare_runs(self.baseline, self.candidate, self.make_assertion())
        self.assertEqual(result["counts"]["regressions"], 0)
        self.assertEqual(result["counts"]["known_bad_violations"], 1)
        self.assertEqual(result["baseline_known_bad_count"], 1)
        self.assertEqual(result["exit_code"], 1)

    def test_known_bad_is_bound_to_input_event_not_a_global_blacklist(self):
        write_run(self.candidate, lambda rows: set_candidates(rows[1], ["你", "泥"]))
        result = compare.evaluate_known_bad(self.candidate, self.make_assertion())
        self.assertEqual(result["violations"], [])
        self.assertEqual(len(result["cleared"]), 1)
        self.assertEqual(result["language_quality"], "partial_review_only")

    def test_stale_assertion_fixture_or_unknown_event_is_rejected(self):
        write_run(self.candidate)
        path = self.make_assertion()
        assertion = audit.read_json(path)
        assertion["fixture_sha256"] = "wrong"
        path.write_text(json.dumps(assertion), encoding="utf-8")
        with self.assertRaisesRegex(audit.EvidenceError, "different fixture"):
            compare.evaluate_known_bad(self.candidate, path)
        path = self.make_assertion()
        assertion = audit.read_json(path)
        assertion["assertions"][0]["keys"] = "999"
        path.write_text(json.dumps(assertion), encoding="utf-8")
        with self.assertRaisesRegex(audit.EvidenceError, "no matching replay event"):
            compare.evaluate_known_bad(self.candidate, path)

    def test_cli_replaces_previous_success_with_invalid_report(self):
        write_run(self.candidate)
        output = self.root / "report"
        args = ["--baseline", str(self.baseline), "--candidate", str(self.candidate), "--output-dir", str(output)]
        self.assertEqual(compare.main(args), 0)
        write_manifest(self.candidate, status="running")
        self.assertEqual(compare.main(args), 2)
        self.assertEqual(audit.read_json(output / "comparison.json")["status"], "invalid")
        self.assertIn("比较无效", (output / "comparison.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
