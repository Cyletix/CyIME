"""Host replay routing and evidence tests; subprocesses are always mocked."""

from contextlib import ExitStack, redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path, PureWindowsPath
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

import t9_host as host
import t9_replay as replay
from test_summarize_daily_chat import FIXTURE, sample


class HostReplayTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.fixture = self.root / "fixture.json"
        self.fixture.write_text(json.dumps(FIXTURE, ensure_ascii=False), encoding="utf-8")
        self.jar = self.root / "json.jar"
        self.jar.write_bytes(b"test library")
        self.output = self.root / "result"
        self.args = SimpleNamespace(fixture=self.fixture, output=self.output, json_jar=self.jar,
                                    key_delay_ms=35, baseline=None, assertions=None,
                                    host_cache=None, wsl_distro="Ubuntu-test")
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(redirect_stdout(io.StringIO()))
        self.stack.enter_context(redirect_stderr(io.StringIO()))
        # Even a regression in host routing cannot accidentally touch a device.
        self.adb = self.stack.enter_context(patch("t9_replay.Commands", side_effect=AssertionError("Device access forbidden")))

    def local(self, build_output=None):
        self.stack.enter_context(patch("t9_host.os", SimpleNamespace(name="posix", pathsep=":")))
        self.stack.enter_context(patch("t9_host.platform.system", return_value="Linux"))
        self.library_dir = self.root / "lib"
        self.library_dir.mkdir()
        (self.library_dir / "librime_jni.so").write_bytes(b"fake host engine")
        (self.library_dir / "libonnxruntime.so").write_bytes(b"fake inference library")
        self.source_inventory = self.root / "source-files.json"
        self.source_inventory.write_text('{"source":"test"}', encoding="utf-8")
        self.build_manifest = self.root / "engine-build.json"
        self.build_manifest.write_text(json.dumps({"status": "complete",
            "library_sha256": host.file_digest(self.library_dir / "librime_jni.so"),
            "source_files_sha256": host.file_digest(self.source_inventory)}), encoding="utf-8")
        description = {"library_dir": str(self.library_dir), "manifest": str(self.build_manifest),
                       "java": "fake-java", "javac": "fake-javac"}
        process = SimpleNamespace(returncode=0, stdout=json.dumps(description).encode() if build_output is None else build_output)
        self.build_process = self.stack.enter_context(patch("t9_host.subprocess.run", return_value=process))
        self.snapshot = self.stack.enter_context(patch("t9_host.snapshot_resources", side_effect=self.resources))
        self.command = self.stack.enter_context(patch("t9_host.HostCommands.run", side_effect=self.execute))
        self.metadata_mutation = None

    def resources(self, repo, output):
        output.write_bytes(b"resource snapshot")
        return {"sha256": host.file_digest(output)}

    def execute(self, argv, **kwargs):
        if argv[0] == "fake-javac":
            return
        self.assertEqual(argv[0], "fake-java")
        manifest = json.loads((self.output / "run.json").read_text(encoding="utf-8"))
        metadata = {"stage": "metadata", "protocol": 1, **manifest["protocol_settings"],
                    "resources_sha256": manifest["engine"]["resources_sha256"],
                    "apk_sha256": manifest["engine"]["apk_sha256"],
                    "library_sha256": manifest["engine"]["libraries"],
                    "corpus_sha256": manifest["fixture_sha256"]}
        if self.metadata_mutation:
            self.metadata_mutation(metadata)
        continuous = [dict(row, mode="continuous") for row in sample() if row["stage"] != "syllable_settled"]
        rows = [metadata, {"stage": "ready"}, *continuous, *sample()]
        rows.append({"stage": "complete", "records_before_complete": len(rows)})
        (self.output / "replay.jsonl").write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows), encoding="utf-8")
        schema = self.output / "native-run/user/build/t9_pinyin.schema.yaml"
        schema.parent.mkdir(parents=True)
        schema.write_text("schema: t9_pinyin", encoding="utf-8")

    def test_default_run_routes_only_to_host(self):
        with patch("t9_host.run_host", return_value=7) as local, \
                patch("t9_replay.run_replay", side_effect=AssertionError("Android route forbidden")) as android:
            self.assertEqual(replay.main(["run", "--fixture", str(self.fixture), "--output", str(self.output)]), 7)
        self.assertEqual(local.call_args.args[0].fixture, self.fixture)
        android.assert_not_called()
        self.adb.assert_not_called()

    def test_host_failure_never_falls_back_to_android(self):
        with patch("t9_host.run_host", side_effect=RuntimeError("host dependency missing")), \
                patch("t9_replay.run_replay", side_effect=AssertionError("Android fallback forbidden")) as android:
            self.assertEqual(replay.main(["run", "--fixture", str(self.fixture), "--output", str(self.output)]), 2)
        android.assert_not_called()
        self.adb.assert_not_called()

    def test_windows_drive_translation_rejects_unc_and_keeps_arguments_literal(self):
        paths = [(r"D:\project folder\fixture;$(literal).json", "/mnt/d/project folder/fixture;$(literal).json"),
                 (r"C:\Users\名称\test.json", "/mnt/c/Users/名称/test.json")]
        for windows, expected in paths:
            with self.subTest(path=windows), patch("t9_host.Path", return_value=SimpleNamespace(resolve=lambda: PureWindowsPath(windows))):
                self.assertEqual(host.linux_path(windows), expected)
        for windows in (r"\\server\share\fixture.json", r"\\?\C:\fixture.json", "/tmp/fixture.json"):
            with self.subTest(path=windows), patch("t9_host.Path", return_value=SimpleNamespace(resolve=lambda: windows)):
                with self.assertRaisesRegex(ValueError, "local Windows drive path"):
                    host.linux_path(windows)

    def test_windows_dispatch_uses_argv_and_translates_each_windows_path(self):
        self.args.baseline = self.root / "base with spaces"
        self.args.assertions = self.root / "checks;literal.json"
        self.args.host_cache = "/tmp/cache with spaces;literal"
        mapped = {str(path): "/mnt/d/" + path.name for path in
                  (host.HERE / "t9_host.py", self.fixture, self.output, self.jar, self.args.baseline, self.args.assertions)}
        with patch("t9_host.os", SimpleNamespace(name="nt")), \
                patch("t9_host.linux_path", side_effect=lambda path: mapped[str(path)]) as translate, \
                patch("t9_host.subprocess.run", return_value=SimpleNamespace(returncode=3)) as run:
            self.assertEqual(host.run_host(self.args), 3)
        argv = run.call_args.args[0]
        self.assertEqual(argv[:6], ["wsl.exe", "-d", "Ubuntu-test", "--", "python3", "/mnt/d/t9_host.py"])
        for flag, expected in (("--fixture", "/mnt/d/fixture.json"), ("--baseline", "/mnt/d/base with spaces"),
                               ("--assertions", "/mnt/d/checks;literal.json"), ("--host-cache", self.args.host_cache)):
            self.assertEqual(argv[argv.index(flag) + 1], expected)
        self.assertEqual(translate.call_count, 6)
        self.assertFalse(run.call_args.kwargs.get("shell", False))
        self.assertFalse(self.output.exists())

    def test_existing_output_is_never_modified_or_used(self):
        for is_directory in (True, False):
            self.args.output = self.root / ("existing-dir" if is_directory else "existing-file")
            if is_directory:
                self.args.output.mkdir()
            marker = self.args.output / "keep" if is_directory else self.args.output
            marker.write_bytes(b"original")
            with patch("t9_host.subprocess.run", side_effect=AssertionError("No process may start")), \
                    self.assertRaisesRegex(ValueError, "Output already exists"):
                host.run_host(self.args)
            self.assertEqual(marker.read_bytes(), b"original")

    def test_invalid_fixture_fails_before_wsl_or_local_build(self):
        self.fixture.write_text('{"id":"bad","cases":[]}', encoding="utf-8")
        for platform_name in ("nt", "posix"):
            with self.subTest(platform=platform_name), patch("t9_host.os", SimpleNamespace(name=platform_name)), \
                    patch("t9_host.subprocess.run", side_effect=AssertionError("No process may start")), \
                    self.assertRaisesRegex(ValueError, "no cases"):
                host.run_host(self.args)
        self.assertFalse(self.output.exists())

    def test_local_success_produces_validated_evidence_without_device_route(self):
        self.local()
        self.assertEqual(host.run_host(self.args), 0)
        self.assertEqual(json.loads((self.output / "run.json").read_text())["status"], "complete")
        self.assertEqual(host.load_run(self.output)["metadata"]["runtime"], "host")
        self.assertEqual(self.command.call_count, 2)
        self.adb.assert_not_called()

    def test_missing_baseline_writes_invalid_comparison_but_preserves_completed_replay(self):
        self.local()
        self.args.baseline = self.root / "missing-baseline"
        self.assertEqual(host.run_host(self.args), 2)
        comparison = json.loads((self.output / "comparison/comparison.json").read_text())
        self.assertEqual(comparison["status"], "invalid")
        self.assertIn("比较无效", (self.output / "comparison/comparison.md").read_text(encoding="utf-8"))
        self.assertEqual(json.loads((self.output / "run.json").read_text())["status"], "complete")
        self.assertEqual(json.loads((self.output / "quality.json").read_text())["exit_code"], 2)

    def test_compare_cli_writes_invalid_report_when_run_directories_are_missing(self):
        output = self.root / "comparison"
        self.assertEqual(replay.main(["compare", str(self.root / "missing"),
                                     str(self.output), "--output", str(output)]), 2)
        self.assertEqual(json.loads((output / "comparison.json").read_text())["status"], "invalid")

    def test_empty_build_result_is_a_recorded_failure(self):
        self.local(build_output=b"\n")
        with self.assertRaisesRegex(ValueError, "no artifact description"):
            host.run_host(self.args)
        self.assertEqual(json.loads((self.output / "run.json").read_text())["status"], "failed")
        self.command.assert_not_called()

    def test_malformed_build_manifest_is_rejected_before_engine_execution(self):
        self.local()
        self.build_manifest.write_text("[]", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "completed build manifest"):
            host.run_host(self.args)
        self.command.assert_not_called()
        self.snapshot.assert_not_called()

    def test_build_source_inventory_change_is_rejected(self):
        self.local()
        self.source_inventory.write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "source inventory differs"):
            host.run_host(self.args)
        self.command.assert_not_called()

    def test_executed_fixture_fingerprint_mismatch_cannot_succeed(self):
        self.local()
        self.metadata_mutation = lambda metadata: metadata.update(corpus_sha256="f" * 64)
        with self.assertRaisesRegex(ValueError, "corpus_sha256 differs"):
            host.run_host(self.args)
        self.assertEqual(json.loads((self.output / "run.json").read_text())["status"], "failed")
        self.assertFalse((self.output / "quality.json").exists())

    def test_executed_engine_fingerprint_mismatch_cannot_succeed(self):
        self.local()
        self.metadata_mutation = lambda metadata: metadata.update(library_sha256={"librime_jni.so": "e" * 64})
        with self.assertRaisesRegex(ValueError, "library_sha256 differs"):
            host.run_host(self.args)
        self.assertEqual(json.loads((self.output / "run.json").read_text())["status"], "failed")
        self.assertFalse((self.output / "quality.json").exists())


if __name__ == "__main__":
    unittest.main()
