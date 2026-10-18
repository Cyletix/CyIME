"""Speed gate (per-key work counters, interleaved latency sessions) and preset user-dictionary comparison rules."""

from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import unittest

import summarize_daily_chat as audit
import t9_replay_compare as compare
import t9_replay_speed as speed
from test_t9_replay_compare import write_manifest, write_run

NAMES = ["joint_decodes", "joint_cache_hits", "dict_table_queries", "beam_evaluations", "gram_lookups"]
START = datetime(2026, 10, 10, 1, 0, tzinfo=timezone.utc)
SCHEMA = "__build_info:\n  rime_version: 1.17.0\n  timestamps:\n    default: {stamp}\n    t9_pinyin.schema: {stamp}\n" \
         "cyime:\n  candidate_policy: true\n"


def instrument(path, work=None, latency_us=4000, user_dict=None, settle=None, inspect=None, tag=None):
    """Add host work counters, microsecond latency and optional preset user state to a stored run."""
    rows = [json.loads(line) for line in (path / "replay.jsonl").read_text(encoding="utf-8").splitlines()]
    for row in rows:
        if row["stage"] == "ready":
            row["work_counters"] = NAMES
            row["user_dict"] = user_dict
            row["run_tag"] = tag or path.name  # separate executions never share a replay hash
        elif row["stage"] == "metadata":
            row["user_dict_sha256"] = user_dict["sha256"] if user_dict else None
        elif row["stage"] == "key_immediate":
            values = dict(zip(NAMES, (1, 0, 2, 30, 90)))
            if work:
                work(row, values)
            row["work"] = values
            row["inspect_work"] = dict.fromkeys(NAMES, 0)
            if inspect:
                inspect(row, row["inspect_work"])
            row["process_us"] = latency_us(row) if callable(latency_us) else latency_us
        elif row["stage"] in ("syllable_settled", "clause_settled"):
            row["work"] = dict.fromkeys(NAMES, 0)
            row["inspect_work"] = dict.fromkeys(NAMES, 0)
            if settle:
                settle(row, row["work"])
    (path / "replay.jsonl").write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows), encoding="utf-8")
    changes = {"replay_sha256": compare.sha256(path / "replay.jsonl")}
    if user_dict:
        changes.update(user_state="preset_user_dict",
                       user_dict_snapshot={"sha256": user_dict["sha256"], "db_name": "rime_ice",
                                           "entries": user_dict["entries"], "file": "user-dict.userdb.txt"})
    write_manifest(path, **changes)


def timed(path, minute, duration=4, filesystem="ext4", stamp=1791557926, build_extra=None):
    """Record a sequential host timing slot, the replay root, and a build manifest/deployed schema."""
    started = START + timedelta(minutes=minute)
    build = {"status": "complete", "library_sha256": "b" * 64, "started_utc": started.isoformat(),
             "finished_utc": started.isoformat(), **(build_extra or {})}
    (path / "engine-build.json").write_text(json.dumps(build), encoding="utf-8")
    (path / "deployed-schema.yaml").write_text(SCHEMA.format(stamp=stamp + minute), encoding="utf-8")
    manifest = audit.read_json(path / "run.json")
    manifest["engine"]["build_manifest_sha256"] = compare.sha256(path / "engine-build.json")
    write_manifest(path, engine=manifest["engine"], started_utc=started.isoformat(),
                   finished_utc=(started + timedelta(minutes=duration)).isoformat(),
                   deployed_schema_sha256=compare.sha256(path / "deployed-schema.yaml"),
                   host={"system": "Linux", "architecture": "x86_64", "release": "test"},
                   compiled_runner_sources={"ChatReplay.java": "c" * 64}, json_jar_sha256="d" * 64,
                   replay_root={"path": "/home/test/.cache/replay-roots/" + path.name, "filesystem": filesystem,
                                "kept_as": "native-run"})


class SpeedGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def run_dir(self, name, minute=None, engine="engine-one", filesystem="ext4", build_extra=None, **options):
        path = self.root / name
        write_run(path, engine_hash=engine)
        instrument(path, **options)
        if minute is not None:
            timed(path, minute, filesystem=filesystem, build_extra=build_extra)
        return path

    def session(self, order, base_us=(4000, 4080, 3960, 4020, 4050), cand_us=(4040, 3990, 4060, 4010), cand_engine="engine-one",
                prefix="", **candidate_options):
        """Runs in time order, e.g. "BCBCBC"; returns (baseline dirs, candidate dirs)."""
        baselines, candidates = [], []
        for slot, label in enumerate(order):
            group = baselines if label == "B" else candidates
            name = f"{prefix}{label}{len(group) + 1}"
            if label == "B":
                group.append(self.run_dir(name, slot * 5, latency_us=base_us[len(group)]))
            else:
                options = {"latency_us": cand_us[len(group)], **candidate_options}
                group.append(self.run_dir(name, slot * 5, engine=cand_engine, **options))
        return baselines, candidates

    def compare_session(self, baselines, candidates, **options):
        return compare.compare_runs(baselines[0], candidates[0], noise_dir=baselines[1:],
                                    candidate_repeats=candidates[1:], **options)

    def test_distribution_and_percentiles(self):
        values = [1, 2, 3, 4, 100]
        result = speed.distribution(values)
        self.assertEqual((result["total"], result["max"], result["p50"]), (110, 100, 3))
        self.assertAlmostEqual(result["p95"], 80.8)
        self.assertEqual(speed.distribution([]), {"count": 0})

    def test_identical_work_without_timing_session_is_incomplete_not_passed(self):
        result = compare.compare_runs(self.run_dir("base"), self.run_dir("cand"))
        self.assertEqual((result["speed_gate"], result["gate"], result["exit_code"]), ("incomplete", "incomplete", 3))
        work = result["speed"]["passes"]["digits:paused"]["work"]["counters"]["beam_evaluations"]
        self.assertEqual((work["baseline"]["total"], work["candidate"]["total"], work["verdict"]), (150, 150, "within_tolerance"))
        latency = result["speed"]["passes"]["digits:paused"]["latency"]
        self.assertEqual(latency["field"], "process_us")
        self.assertEqual(latency["gate"]["mean"]["verdict"], "unassessed_no_timing_session")

    def test_same_engine_runs_in_an_interleaved_session_pass(self):
        # Three baseline-engine runs and three runs of the same engine as candidate, with run-level jitter.
        baselines, candidates = self.session("BCCBCB")
        self.assertEqual((len(baselines), len(candidates)), (3, 3))
        result = self.compare_session(baselines, candidates)
        self.assertEqual((result["speed_gate"], result["gate"], result["exit_code"]), ("passed", "no_detected_regression", 0))
        session = result["speed"]["timing_session"]
        self.assertEqual((session["order"], session["assessable"]), ("BCCBCB", True))
        # A single candidate run between baseline runs is also judged (wider band for m = 1).
        result = compare.compare_runs(baselines[0], candidates[0], noise_dir=baselines[1:])
        self.assertEqual(result["speed_gate"], "passed")
        mean = result["speed"]["passes"]["digits:continuous"]["latency"]["gate"]["mean"]
        self.assertEqual(mean["verdict"], "within_noise")
        self.assertEqual(len(mean["baseline_runs"]), 3)

    def test_band_is_a_one_sided_t_bound_on_pooled_run_spread(self):
        base, cand = [6.077, 6.411, 6.331], [7.112, 6.793]
        band, freedom = speed.latency_band(base, cand)
        self.assertEqual(freedom, 3)
        pooled = ((sum((v - sum(base) / 3) ** 2 for v in base) + sum((v - sum(cand) / 2) ** 2 for v in cand)) / 3) ** 0.5
        self.assertAlmostEqual(band, 4.541 * pooled * (1 / 3 + 1 / 2) ** 0.5 + 0.01 * sum(base) / 3, places=6)
        # This real same-engine session (d, 10-10) exceeded the baseline spread but stays inside the band.
        self.assertGreater(sum(cand) / 2 - sum(base) / 3, max(base) - min(base))
        self.assertLess(sum(cand) / 2 - sum(base) / 3, band)
        single, freedom = speed.latency_band(base, [6.3])
        self.assertEqual(freedom, 2)
        more, _ = speed.latency_band(base + [6.2, 6.4], cand + [6.9, 6.7, 6.8])
        self.assertLess(more, band)

    def test_slowdown_beyond_baseline_spread_but_inside_the_band_is_a_review_warning(self):
        baselines, candidates = self.session("BCCBCB", base_us=(4000, 4010, 4005), cand_us=(4100, 4060, 4140))
        result = self.compare_session(baselines, candidates)
        self.assertEqual(result["speed_gate"], "passed")
        mean = result["speed"]["passes"]["digits:paused"]["latency"]["gate"]["mean"]
        self.assertEqual((mean["verdict"], mean["above_baseline_spread"]), ("within_noise", True))
        self.assertIn("digits:paused: latency mean", " ".join(result["speed"]["warnings"]))
        self.assertIn("需复核", "\n".join(speed.markdown(result["speed"])))

    def test_latency_beyond_the_session_band_fails(self):
        baselines, candidates = self.session("BCBCB", cand_us=(4600, 4650))
        result = self.compare_session(baselines, candidates)
        self.assertEqual((result["speed_gate"], result["exit_code"]), ("failed", 1))
        self.assertEqual(result["speed"]["passes"]["digits:paused"]["latency"]["gate"]["mean"]["verdict"], "slower_than_noise")
        self.assertEqual(result["counts"]["regressions"], 0)

    def test_session_rules_leave_latency_unassessed(self):
        for name, order in (("two_baseline_runs", "BCB"), ("not_bracketed", "BBBC")):
            with self.subTest(case=name):
                baselines, candidates = self.session(order, prefix=name)
                result = self.compare_session(baselines, candidates)
                self.assertEqual((result["speed_gate"], result["exit_code"]), ("incomplete", 3))
                self.assertFalse(result["speed"]["timing_session"]["assessable"])
        baselines, candidates = self.session("BCBCB", prefix="gap")
        write_manifest(baselines[-1], started_utc=(START + timedelta(hours=3)).isoformat(),
                       finished_utc=(START + timedelta(hours=3, minutes=4)).isoformat())
        result = self.compare_session(baselines, candidates)
        self.assertEqual(result["speed_gate"], "incomplete")
        self.assertIn("gap", " ".join(result["speed"]["timing_session"]["reasons"]))
        baselines, candidates = self.session("BCBCB", prefix="overlap")
        write_manifest(candidates[0], finished_utc=(START + timedelta(minutes=12)).isoformat())
        self.assertIn("overlap", " ".join(self.compare_session(baselines, candidates)["speed"]["timing_session"]["reasons"]))

    def test_mounted_windows_drive_or_unrecorded_root_leaves_latency_unassessed(self):
        baselines, candidates = self.session("BCBCB")
        for path in baselines + candidates:
            manifest = audit.read_json(path / "run.json")
            write_manifest(path, replay_root=dict(manifest["replay_root"], filesystem="9p"))
        result = self.compare_session(baselines, candidates)
        self.assertEqual(result["speed_gate"], "incomplete")
        self.assertIn("non-native", " ".join(result["speed"]["timing_session"]["reasons"]))
        for path in baselines + candidates:
            write_manifest(path, replay_root=None)
        result = self.compare_session(baselines, candidates)
        self.assertIn("not recorded", " ".join(result["speed"]["timing_session"]["reasons"]))

    def test_noise_run_of_another_engine_is_invalid(self):
        base, other = self.run_dir("base", 0), self.run_dir("other", 10, engine="engine-two")
        cand = self.run_dir("cand", 5, engine="engine-two", latency_us=9000)
        with self.assertRaisesRegex(audit.EvidenceError, "not a run of the engine"):
            compare.compare_runs(base, cand, noise_dir=[other])
        rebuilt = self.run_dir("rebuilt", 15, build_extra={"source_sha256": "e" * 64})
        with self.assertRaisesRegex(audit.EvidenceError, "build differs"):
            compare.compare_runs(base, cand, noise_dir=[rebuilt])
        # Deployment timestamps and build times differ between repeats and are ignored.
        same = self.run_dir("same", 20)
        self.assertNotEqual(compare.sha256(base / "deployed-schema.yaml"), compare.sha256(same / "deployed-schema.yaml"))
        self.assertEqual(compare.engine_identity(compare.load_run(base)), compare.engine_identity(compare.load_run(same)))
        (same / "deployed-schema.yaml").write_text(SCHEMA.format(stamp=1).replace("true", "false"), encoding="utf-8")
        write_manifest(same, deployed_schema_sha256=compare.sha256(same / "deployed-schema.yaml"))
        with self.assertRaisesRegex(audit.EvidenceError, "deployed_schema differs"):
            compare.compare_runs(base, cand, noise_dir=[same])

    def test_candidate_cannot_be_its_own_noise_baseline(self):
        def more(row, values):
            values["beam_evaluations"] *= 3
        base = self.run_dir("base", 0, latency_us=4000)
        cand = self.run_dir("cand", 5, engine="engine-two", work=more, latency_us=9000)
        self.assertEqual(compare.compare_runs(base, cand)["speed_gate"], "failed")
        with self.assertRaisesRegex(audit.EvidenceError, "not a run of the engine"):
            compare.compare_runs(base, cand, noise_dir=[cand])
        output = self.root / "report"
        args = ["--baseline", str(base), "--candidate", str(cand), "--noise-baseline", str(cand), "--output-dir", str(output)]
        self.assertEqual(compare.main(args), 2)
        same = self.run_dir("same", 10)
        with self.assertRaisesRegex(audit.EvidenceError, "more than once"):
            compare.compare_runs(base, same, noise_dir=[same, base])

    def test_cost_counter_growth_fails_even_with_quality_unchanged(self):
        def more(row, values):
            values["beam_evaluations"] += 1
        result = compare.compare_runs(self.run_dir("base"), self.run_dir("cand", work=more))
        self.assertEqual((result["speed_gate"], result["exit_code"]), ("failed", 1))
        self.assertEqual(result["counts"]["regressions"], 0)
        self.assertIn("digits:paused: work beam_evaluations increased (total, p50, p95, p99, max)",
                      result["speed"]["failures"])
        tolerant = compare.compare_runs(self.root / "base", self.root / "cand", work_tolerance_percent=50)
        self.assertNotEqual(tolerant["speed_gate"], "failed")

    def test_per_key_growth_fails_and_is_listed_per_case_even_when_the_total_is_unchanged(self):
        def redistribute(row, values):
            values["beam_evaluations"] = 110 if row["keys"] == "6" else 10
        result = compare.compare_runs(self.run_dir("base"), self.run_dir("cand", work=redistribute))
        work = result["speed"]["passes"]["digits:continuous"]["work"]
        entry = work["counters"]["beam_evaluations"]
        self.assertEqual((entry["baseline"]["total"], entry["candidate"]["total"]), (150, 150))
        self.assertEqual(entry["verdict"], "increased")
        self.assertIn("max", entry["grown_statistics"])
        self.assertEqual(result["speed_gate"], "failed")
        self.assertEqual(work["increased_key_count"], 1)
        case = work["increased_keys"][0]
        self.assertEqual((case["id"], case["keys"]), ("c001", ["6"]))
        self.assertEqual(case["counters"]["beam_evaluations"]["keys"], [{"keys": "6", "baseline": 30, "candidate": 110}])
        self.assertEqual(case["counters"]["beam_evaluations"]["case_total"], {"baseline": 150, "candidate": 150})
        self.assertIn("c001：beam_evaluations 150→150（1 键）", "\n".join(speed.markdown(result["speed"])))

    def test_any_key_whose_work_grows_fails_even_when_every_statistic_is_unchanged(self):
        # The same per-key values moved to another key: total, p50, p95, p99 and max are unchanged.
        def heavy_first(row, values):
            values["beam_evaluations"] = 40 if row["keys"] == "6" else 30

        def heavy_third(row, values):
            values["beam_evaluations"] = 40 if row["keys"] == "644" else 30
        result = compare.compare_runs(self.run_dir("base", work=heavy_first), self.run_dir("cand", work=heavy_third))
        work = result["speed"]["passes"]["digits:continuous"]["work"]
        self.assertEqual(work["counters"]["beam_evaluations"]["grown_statistics"], [])
        self.assertEqual(work["increased"], [])
        self.assertEqual((result["speed_gate"], result["exit_code"]), ("failed", 1))
        self.assertEqual([(case["id"], case["keys"]) for case in work["increased_keys"]], [("c001", ["644"])])
        self.assertIn("digits:continuous: c001 work grew on key(s) 644 (beam_evaluations)", result["speed"]["failures"])
        # Identical per-key work does not fail.
        same = compare.compare_runs(self.root / "base", self.run_dir("same", work=heavy_first))
        self.assertEqual(same["speed"]["failures"], [])

    def test_inspection_and_settle_work_are_gated(self):
        def inspect(row, values):
            values["gram_lookups"] = 5
        result = compare.compare_runs(self.run_dir("base"), self.run_dir("inspect", inspect=inspect))
        self.assertIn("gram_lookups", result["speed"]["passes"]["digits:paused"]["work"]["increased"])

        def settle(row, values):
            values["dict_table_queries"] = 4
        result = compare.compare_runs(self.root / "base", self.run_dir("settle", settle=settle))
        entry = result["speed"]["passes"]["digits:paused"]["work"]["counters"]["dict_table_queries"]
        self.assertEqual((entry["grown_statistics"], entry["verdict"]), (["settle"], "increased"))

    def test_informational_counter_growth_does_not_fail(self):
        def more(row, values):
            values["joint_decodes"] += 1
        baselines, candidates = self.session("BCB" + "B", work=more)
        self.assertEqual(self.compare_session(baselines, candidates)["speed_gate"], "passed")

    def test_repeat_that_does_not_reproduce_work_fails(self):
        baselines, candidates = self.session("BCBB")
        instrument(baselines[1], work=lambda row, values: values.update(dict_table_queries=3), tag="B2-again")
        timed(baselines[1], 10)
        result = self.compare_session(baselines, candidates)
        self.assertEqual(result["speed_gate"], "failed")
        self.assertIn(f"work not reproduced by {baselines[1]}", " ".join(result["speed"]["failures"]))

    def test_pass_served_from_the_decode_cache_is_not_latency_evidence(self):
        def cached(row, values):
            if row["mode"] == "paused":
                values["joint_cache_hits"] = values["joint_decodes"]
        baselines, candidates = self.session("BCBB", work=None)
        for path in baselines + candidates:
            instrument(path, work=cached, latency_us=4000, tag=path.name + "-cached")
        for slot, path in enumerate([baselines[0], candidates[0], baselines[1], baselines[2]]):
            timed(path, slot * 5)
        result = self.compare_session(baselines, candidates)
        paused = result["speed"]["passes"]["digits:paused"]["latency"]["gate"]["mean"]
        self.assertEqual(paused["verdict"], "not_applicable_decode_cache_only")
        self.assertEqual(result["speed"]["passes"]["digits:continuous"]["latency"]["gate"]["mean"]["verdict"], "within_noise")
        self.assertEqual(result["speed_gate"], "passed")

    def test_warm_latency_excludes_only_the_first_case(self):
        def rows(case, values):
            return [{"pass": "digits:paused", "stage": "key_immediate", "id": case, "keys": str(index),
                     "process_us": value, "process_ms": value // 1000} for index, value in enumerate(values, 1)]
        run = {"directory": "r", "fixture": {"cases": [{"id": "a"}, {"id": "b"}]},
               "snapshots": rows("a", [500000, 400000]) + rows("b", [3000, 5000])}
        session = {"assessable": False}
        section = speed.latency_section([run], [run], "digits:paused", session)
        self.assertEqual((section["per_run"]["baseline"][0]["count"], section["baseline"]["max"]), (2, 5.0))
        single = {"directory": "s", "fixture": {"cases": [{"id": "a"}]}, "snapshots": rows("a", [7000])}
        self.assertEqual(speed.latency_section([single], [single], "digits:paused", session)["per_run"]["baseline"][0]["count"], 1)

    def test_runs_without_counters_report_unavailable_and_do_not_pass(self):
        write_run(self.root / "old")
        result = compare.compare_runs(self.root / "old", self.run_dir("cand"))
        self.assertEqual((result["speed_gate"], result["gate"], result["exit_code"]), ("unavailable", "incomplete", 3))
        self.assertIn("速度门禁", "\n".join(speed.markdown(result["speed"])))

    def test_combined_exit_codes_rank_invalid_over_failure_over_incomplete(self):
        self.assertEqual(compare.combine_exit_codes(0, 3), 3)
        self.assertEqual(compare.combine_exit_codes(3, 1), 1)
        self.assertEqual(compare.combine_exit_codes(1, 2, 3), 2)
        self.assertEqual(compare.combine_exit_codes(0, 0), 0)

    def test_preset_user_dict_runs_never_mix_with_empty_or_other_snapshots(self):
        seed = {"sha256": "a" * 64, "db_name": "rime_ice", "entries": 3}
        preset = self.run_dir("preset", user_dict=seed)
        self.assertEqual(compare.load_run(preset)["manifest"]["user_state"], "preset_user_dict")
        self.assertEqual(compare.compare_runs(preset, self.run_dir("preset2", user_dict=seed))["status"], "complete")
        with self.assertRaisesRegex(audit.EvidenceError, "user_state differs"):
            compare.compare_runs(self.run_dir("empty"), preset)
        other = self.run_dir("other", user_dict=dict(seed, sha256="b" * 64))
        with self.assertRaisesRegex(audit.EvidenceError, "user_dict_snapshot differs"):
            compare.compare_runs(preset, other)

    def test_preset_state_must_match_what_the_runner_restored(self):
        seed = {"sha256": "a" * 64, "db_name": "rime_ice", "entries": 3}
        preset = self.run_dir("preset", user_dict=seed)
        manifest = audit.read_json(preset / "run.json")
        manifest["user_dict_snapshot"]["entries"] = 4
        write_manifest(preset, **manifest)
        with self.assertRaisesRegex(audit.EvidenceError, "entry count differs"):
            compare.load_run(preset)
        write_manifest(preset, user_dict_snapshot=None)
        with self.assertRaisesRegex(audit.EvidenceError, "requires user_dict_snapshot"):
            compare.load_run(preset)
        empty = self.run_dir("empty")
        write_manifest(empty, user_dict_snapshot={"sha256": "a" * 64})
        with self.assertRaisesRegex(audit.EvidenceError, "declares a user dictionary"):
            compare.load_run(empty)


if __name__ == "__main__":
    unittest.main()
