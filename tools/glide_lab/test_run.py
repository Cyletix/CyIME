"""Offline protocol checks; no Java process, network, Gradle or device is used."""
from __future__ import annotations

import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("cyime_glide_runner", Path(__file__).with_name("run.py"))
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


def fixture():
    return {
        "schema": "cyime.glide.fixture.v1",
        "kind": "synthetic",
        "id": "sample",
        "language": "en",
        "scheme": "direct",
        "layout": {
            "revision": "layout-v1",
            "key_unit": 1,
            "keys": [{"symbol": "a", "x": 0, "y": 0}, {"symbol": "b", "x": 1, "y": 0}],
        },
        "lexicon": [{"id": "word-ab", "text": "ab", "code": "ab", "prior_cost": 0.25}],
        "cases": [{"id": "trace-ab", "description": "Two-key synthetic path", "expected_ids": ["word-ab"],
                   "points": [[0, 0, 0], [1, 0, 100]]}],
    }


def report(cases, digest="same-bytes"):
    return {"schema": "cyime.glide.report.v1", "fixture_sha256": {"sample": digest},
            "fixtures": [{"fixture": "sample", "cases": cases}]}


class RunnerProtocolTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="cyime-glide-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def load(self, data):
        path = self.root / "external fixture.json"
        path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        return runner.load_fixture(path)

    def test_external_fixture_preserves_unicode_and_uses_supplied_output_directory(self):
        data = fixture()
        data.update(language="zh", scheme="pinyin", id="../fixture-id-is-only-data")
        data["lexicon"][0]["text"] = "中文"
        loaded = self.load(data)
        destination = self.root / "fixture-0"
        runner.write_inputs(loaded, destination)
        self.assertEqual("word-ab\tab\t中文\t0.25\n", (destination / "lexicon.tsv").read_text(encoding="utf-8"))
        self.assertEqual({"layout.tsv", "lexicon.tsv", "traces.tsv"}, {p.name for p in destination.iterdir()})
        self.assertEqual(data, loaded)

    def test_fixture_kind_language_and_schema_require_supported_protocol(self):
        for name, value in (("schema", "cyime.glide.fixture.v0"), ("kind", "captured"),
                            ("language", "ja"), ("scheme", "pinyin")):
            with self.subTest(field=name):
                data = fixture()
                data[name] = value
                with self.assertRaises(ValueError):
                    self.load(data)

    def test_protocol_text_rejects_tab_and_every_splitlines_separator(self):
        for separator in ("\t", "\n", "\r", "\x1c", "\x85", "\u2028", "\u2029"):
            with self.subTest(separator=repr(separator)):
                data = fixture()
                data["cases"][0]["id"] = "first" + separator + "second"
                with self.assertRaises(ValueError):
                    self.load(data)

    def test_numeric_fields_reject_booleans_nonfinite_and_unrepresentable_values(self):
        for value in (True, "1", float("nan"), float("inf"), float("-inf"), 10 ** 400):
            with self.subTest(value=str(value)[:24]):
                data = fixture()
                data["layout"]["keys"][0]["x"] = value
                with self.assertRaises(ValueError):
                    self.load(data)

    def test_duplicate_identities_and_unknown_references_are_rejected(self):
        duplicates = []
        for collection in ("lexicon", "cases"):
            data = fixture()
            data[collection].append(copy.deepcopy(data[collection][0]))
            duplicates.append(data)
        data = fixture()
        data["layout"]["keys"][1]["symbol"] = "a"
        duplicates.append(data)
        data = fixture()
        data["cases"][0]["expected_ids"] = ["absent-word"]
        duplicates.append(data)
        data = fixture()
        data["lexicon"][0]["code"] = "ac"
        duplicates.append(data)
        for index, data in enumerate(duplicates):
            with self.subTest(case=index), self.assertRaises(ValueError):
                self.load(data)

    def test_timestamp_order_range_and_integer_contract(self):
        for timestamps in ((0, 0), (0, 60000)):
            data = fixture()
            for point, timestamp in zip(data["cases"][0]["points"], timestamps):
                point[2] = timestamp
            self.load(data)
        for timestamps in ((-1, 0), (2, 1), (0, 60001), (0, True), (0, 1.5)):
            with self.subTest(timestamps=timestamps):
                data = fixture()
                for point, timestamp in zip(data["cases"][0]["points"], timestamps):
                    point[2] = timestamp
                with self.assertRaises(ValueError):
                    self.load(data)

    def test_fixture_and_trace_size_limits_reject_oversize_input(self):
        path = self.root / "oversize.json"
        path.write_bytes(b" " * (4 * 1024 * 1024 + 1))
        with self.assertRaisesRegex(ValueError, "4 MiB"):
            runner.load_fixture(path)
        for points in ([], [[0, 0, 0]] * 2049):
            with self.subTest(points=len(points)):
                data = fixture()
                data["cases"][0]["points"] = points
                with self.assertRaises(ValueError):
                    self.load(data)

    def test_summary_distinguishes_rank_six_top_five_and_false_activation(self):
        data = fixture()
        data["lexicon"] = [{"id": f"word-{i}", "text": "ab", "code": "ab", "prior_cost": 0}
                           for i in range(1, 7)]
        data["cases"][0]["expected_ids"] = ["word-6"]
        data["cases"].extend([
            {"id": "negative-ok", "description": "Rejected short path", "expected_ids": [], "points": [[0, 0, 0]]},
            {"id": "negative-bad", "description": "Unexpected activation", "expected_ids": [], "points": [[0, 0, 0]]},
        ])
        lines = ["CASE\ttrace-ab\t-\t0.1"]
        lines += [f"CANDIDATE\ttrace-ab\tword-{i}\t0.1\t0.2\t0.3\t0.4" for i in range(1, 7)]
        lines += ["CASE\tnegative-ok\tTOO_SHORT\t0.2", "CASE\tnegative-bad\t-\t0.3",
                  "CANDIDATE\tnegative-bad\tword-1\t0.1\t0.2\t0.3\t0.4"]
        result = runner.summarize(data, "\n".join(lines))
        self.assertEqual(6, result["cases"][0]["rank"])
        self.assertEqual({"cases": 3, "passed": 1, "top1": 0, "top5": 0, "positive_cases": 1,
                          "false_activations": 1, "negative_cases": 2}, result["summary"])

    def test_baseline_comparison_requires_same_bytes_protocol_and_reports_changes(self):
        before = report([{"id": "trace-ab", "rank": 2, "passed": False}])
        after = report([{"id": "trace-ab", "rank": 1, "passed": True}])
        self.assertEqual([], runner.compare(before, copy.deepcopy(before)))
        changes = runner.compare(after, before)
        self.assertEqual([{"fixture": "sample", "case": "trace-ab", "before_rank": 2, "after_rank": 1,
                           "before_passed": False, "after_passed": True}], changes)
        for baseline in (report(before["fixtures"][0]["cases"], "different-bytes"),
                         {**before, "schema": "cyime.glide.report.v0"}):
            with self.subTest(baseline=baseline), self.assertRaises(ValueError):
                runner.compare(after, baseline)

    def test_cache_lookup_never_selects_missing_or_ambiguous_artifact(self):
        cache = self.root / "cache"
        with self.assertRaises(FileNotFoundError):
            runner.cached(cache, "test.group", "artifact", "1", "jar")
        for digest in ("digest-one", "digest-two"):
            path = cache / "test.group/artifact/1" / digest / "artifact-1.jar"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"local cache fixture")
            if digest == "digest-one":
                self.assertEqual(path, runner.cached(cache, "test.group", "artifact", "1", "jar"))
        with self.assertRaises(FileNotFoundError):
            runner.cached(cache, "test.group", "artifact", "1", "jar")

    def test_existing_result_directory_cannot_be_overwritten_or_compiled_into(self):
        path = self.root / "fixture.json"
        path.write_text(json.dumps(fixture()), encoding="utf-8")
        destination = self.root / "already-exists"
        destination.mkdir()
        sentinel = destination / "report.json"
        sentinel.write_bytes(b"preserve previous report")
        argv = ["run.py", "--fixture", str(path), "--output", str(destination)]
        with mock.patch.object(runner.sys, "argv", argv), mock.patch.object(runner.shutil, "which", return_value="java"), \
                mock.patch.object(runner, "compiler_paths", return_value=("test", [], path)), \
                mock.patch.object(runner, "execute") as execute:
            with self.assertRaises(FileExistsError):
                runner.main()
            execute.assert_not_called()
        with self.assertRaises(FileExistsError):
            runner.write_inputs(fixture(), destination)
        self.assertEqual(b"preserve previous report", sentinel.read_bytes())
        self.assertEqual([sentinel], list(destination.iterdir()))

    def test_main_accepts_external_fixture_path_and_emits_reproducible_hashes(self):
        path = self.root / "external fixture.json"
        original = json.dumps(fixture()).encode("utf-8")
        path.write_bytes(original)
        jar = self.root / "cached-test.jar"
        jar.write_bytes(b"not executable; subprocess is mocked")
        destination = self.root / "new output"
        argv = ["run.py", "--fixture", str(path), "--output", str(destination)]

        def execute(arguments, log):
            output = ("CASE\ttrace-ab\t-\t0.1\nCANDIDATE\ttrace-ab\tword-ab\t0\t0\t0.25\t0.1\n"
                      if "com.kingzcheung.xime.glidelab.GlideReplayKt" in arguments else "synthetic checks passed\n")
            log.write_text(output, encoding="utf-8")
            return output

        with mock.patch.object(runner.sys, "argv", argv), mock.patch.object(runner.shutil, "which", return_value="java"), \
                mock.patch.object(runner, "compiler_paths", return_value=("test-version", [jar], jar)), \
                mock.patch.object(runner, "execute", side_effect=execute), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, runner.main())
        result = json.loads((destination / "report.json").read_text(encoding="utf-8"))
        self.assertEqual("experimental-not-device-accepted", result["status"])
        self.assertEqual({"sample": runner.sha(path)}, result["fixture_sha256"])
        self.assertEqual({jar.name: runner.sha(jar)}, result["compiler_sha256"])
        self.assertEqual(1, result["fixtures"][0]["summary"]["passed"])
        self.assertEqual(original, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
