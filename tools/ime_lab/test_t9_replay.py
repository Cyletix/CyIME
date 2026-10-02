"""Runner safety and failure evidence checks; no SDK process or device is used."""

from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import shlex
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

import t9_replay as replay


class ReplayRunnerTest(unittest.TestCase):
    def test_multiple_ready_devices_require_explicit_selection_and_offline_is_never_chosen(self):
        devices = "List of devices attached\nfirst\tdevice\nsecond\tdevice\nlocked\tunauthorized\nold\toffline\n"
        with self.assertRaisesRegex(ValueError, "found 2"):
            replay.choose_device(devices)
        self.assertEqual(replay.choose_device(devices, "second"), "second")
        for requested in ("locked", "old", "missing"):
            with self.subTest(requested=requested), self.assertRaisesRegex(ValueError, "not ready"):
                replay.choose_device(devices, requested)
        self.assertEqual(replay.choose_device("List of devices attached\nready\tdevice\nold\toffline\n"), "ready")
        with self.assertRaisesRegex(ValueError, "found 0"):
            replay.choose_device("List of devices attached\nlocked\tunauthorized\n")

    def test_split_or_missing_installed_apk_is_rejected_before_snapshot(self):
        base = "/data/app/~~id/com.cyime-id/base.apk"
        self.assertEqual(replay.installed_apk("package:" + base + "\n"), base)
        for invalid in ("", "package:" + base + "\npackage:/data/app/id/split_config.arm64.apk\n",
                        "package:/system/app/CyIME/base.apk\n", "package:/data/app/id/other.apk\n"):
            with self.subTest(output=invalid), self.assertRaisesRegex(ValueError, "monolithic"):
                replay.installed_apk(invalid)
        for path in ("/data/app/../../outside/base.apk", "/data/app/./id/base.apk", "/data/app//id/base.apk"):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "noncanonical"):
                replay.installed_apk("package:" + path)

    def test_remote_arguments_with_spaces_quotes_and_shell_metacharacters_remain_single_words(self):
        arguments = ["env", "NAME=a value", "$(touch /tmp/never)", "semi;colon", "a'b", 'double"quote',
                     "back`tick", "first\nsecond", "*", "", "/data/app/~~id/base.apk"]
        with tempfile.TemporaryDirectory() as directory:
            command = replay.Commands("adb", Path(directory) / "commands.log", serial="device")
            process = SimpleNamespace(returncode=0, stdout=b"ok\n")
            with patch("t9_replay.subprocess.run", return_value=process) as run:
                self.assertEqual(command.shell(*arguments), "ok\n")
            positional, options = run.call_args
            self.assertEqual(positional[0][:4], ["adb", "-s", "device", "shell"])
            self.assertEqual(len(positional[0]), 5)
            self.assertEqual(shlex.split(positional[0][4]), arguments)
            self.assertFalse(options.get("shell", False))

    def apk(self, path, entries):
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def test_apk_must_have_manifest_and_matching_device_abi(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "input.apk"
            self.apk(apk, {"assets/rime-bundled-manifest.tsv": "manifest",
                           "lib/x86_64/librime_jni.so": b"other ABI"})
            with self.assertRaisesRegex(ValueError, "device ABI arm64-v8a"):
                replay.extract_libraries(apk, "arm64-v8a", root / "wrong-abi")
            self.apk(apk, {"lib/arm64-v8a/librime_jni.so": b"no manifest"})
            with self.assertRaisesRegex(ValueError, "no bundled Rime manifest"):
                replay.extract_libraries(apk, "arm64-v8a", root / "missing-manifest")
            self.apk(apk, {"assets/rime-bundled-manifest.tsv": "manifest",
                           "lib/arm64-v8a/librime_jni.so": b"engine",
                           "lib/arm64-v8a/libc++_shared.so": b"dependency",
                           "lib/x86_64/librime_jni.so": b"wrong",
                           "assets/irrelevant.bin": b"not a library"})
            target = root / "valid"
            replay.extract_libraries(apk, "arm64-v8a", target)
            self.assertEqual({p.name for p in target.iterdir()}, {"librime_jni.so", "libc++_shared.so"})
            self.assertEqual((target / "librime_jni.so").read_bytes(), b"engine")

    def test_native_library_zip_traversal_and_nested_paths_never_escape_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index, leaf in enumerate(("../escaped.so", "../../escaped.so", "libsub/../escaped.so", "lib\\escaped.so")):
                apk = root / f"bad-{index}.apk"
                self.apk(apk, {"assets/rime-bundled-manifest.tsv": "manifest",
                               "lib/arm64-v8a/" + leaf: b"payload"})
                with self.subTest(leaf=leaf), self.assertRaisesRegex(ValueError, "Invalid native-library ZIP entry"):
                    replay.extract_libraries(apk, "arm64-v8a", root / f"out-{index}")
                self.assertFalse((root / "escaped.so").exists())
                self.assertEqual(list((root / f"out-{index}").iterdir()), [])

    def test_apk_replaced_during_library_extraction_is_rejected_before_transfer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "input.apk"
            self.apk(apk, {"assets/rime-bundled-manifest.tsv": "manifest",
                           "lib/arm64-v8a/librime_jni.so": b"original engine"})
            original_extract = replay.extract_libraries

            def concurrent_change(path, abi, target):
                original_extract(path, abi, target)
                self.apk(path, {"assets/rime-bundled-manifest.tsv": "changed",
                                "lib/arm64-v8a/librime_jni.so": b"replacement engine"})

            command = SimpleNamespace(shell=Mock(return_value=""), adb_run=Mock())
            with patch("t9_replay.extract_libraries", side_effect=concurrent_change), \
                    self.assertRaisesRegex(ValueError, "changed while extracting"):
                replay.snapshot_engine(command, SimpleNamespace(apk=apk), "/data/local/tmp/test", root, "arm64-v8a")
            command.adb_run.assert_not_called()

    def test_sdk_selects_numeric_versions_and_rejects_incomplete_jdk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk, jdk = root / "sdk", root / "jdk"
            suffix = ".exe" if os.name == "nt" else ""
            for name in ("java", "javac", "jar"):
                path = jdk / "bin" / (name + suffix)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"placeholder")
            for name in ("android-9", "android-35"):
                path = sdk / "platforms" / name / "android.jar"
                path.parent.mkdir(parents=True)
                path.write_bytes(b"placeholder")
            for name in ("9.0.0", "35.0.0"):
                path = sdk / "build-tools" / name / "lib/d8.jar"
                path.parent.mkdir(parents=True)
                path.write_bytes(b"placeholder")
            selected = replay.sdk_tools(sdk, jdk)
            self.assertEqual(selected["android"].parent.name, "android-35")
            self.assertEqual(selected["d8"].parent.parent.name, "35.0.0")
            (jdk / "bin" / ("jar" + suffix)).unlink()
            with self.assertRaisesRegex(ValueError, "incomplete"):
                replay.sdk_tools(sdk, jdk)

    def test_existing_result_file_or_directory_is_untouched_before_sdk_or_device_use(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for as_directory in (True, False):
                output = root / ("existing-directory" if as_directory else "existing-file")
                sentinel = output / "result.json" if as_directory else output
                if as_directory:
                    output.mkdir()
                sentinel.write_bytes(b"previous result")
                args = SimpleNamespace(fixture=replay.DEFAULT_FIXTURE, output=output)
                with self.subTest(as_directory=as_directory), \
                        patch("t9_replay.sdk_path", side_effect=AssertionError("SDK must not be accessed")), \
                        self.assertRaisesRegex(ValueError, "Output already exists"):
                    replay.run_replay(args)
                self.assertEqual(sentinel.read_bytes(), b"previous result")
                if as_directory:
                    self.assertEqual(list(output.iterdir()), [sentinel])

    def test_cleanup_root_pattern_rejects_parent_paths_suffixes_and_control_characters(self):
        valid = "/data/local/tmp/cyime-chat-audit-20261003T010203-deadbeef"
        self.assertIsNotNone(replay.REMOTE_PATTERN.fullmatch(valid))
        for path in ("/data/local/tmp", valid + "/child", valid + "/../outside", valid + "\n",
                     valid + ";echo", valid.replace("deadbeef", "DEADBEEF"),
                     valid.replace("20261003T010203", "20261003"),
                     "/data/local/tmp/cyime-chat-audit-"):
            with self.subTest(path=path):
                self.assertIsNone(replay.REMOTE_PATTERN.fullmatch(path))

    def test_snapshot_failure_retains_first_error_and_failed_manifest_when_cleanup_also_fails(self):
        class FailingDevice:
            serial = None

            def __init__(self):
                self.cleanup = []

            def adb_run(self, *args):
                if args == ("devices",):
                    return "List of devices attached\nserial\tdevice\n"
                raise RuntimeError("salvage pull failed")

            def shell(self, *args):
                if args[0] == "getprop":
                    return {"ro.product.model": "test", "ro.build.version.release": "15",
                            "ro.product.cpu.abi": "arm64-v8a"}[args[1]]
                if args[0] == "mkdir":
                    return ""
                if args[0] == "rm":
                    self.cleanup.append(args)
                    raise RuntimeError("cleanup unavailable")
                raise AssertionError(args)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "result"
            (root / "runner.dex").write_bytes(b"standalone diagnostic runner")
            args = SimpleNamespace(fixture=replay.DEFAULT_FIXTURE, output=output, sdk=None, java_home=None,
                                   adb=None, key_delay_ms=35, serial=None, keep_device=False)
            device = FailingDevice()
            first = RuntimeError("first transfer failure")
            with patch("t9_replay.sdk_path", return_value=root / "sdk"), \
                    patch("t9_replay.sdk_tools", return_value={}), \
                    patch("t9_replay.Commands", return_value=device), \
                    patch("t9_replay.compile_runner", return_value=root / "runner.dex"), \
                    patch("t9_replay.snapshot_engine", side_effect=first), \
                    redirect_stdout(io.StringIO()), self.assertRaises(RuntimeError) as caught:
                replay.run_replay(args)
            self.assertIs(caught.exception, first)
            manifest = json.loads((output / "run.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["status"], "failed")
            self.assertEqual(manifest["error"], "first transfer failure")
            self.assertIn("retained: cleanup unavailable", manifest["device_temporary_files"])
            self.assertEqual(manifest["fixture_sha256"], replay.digest(output / "fixture.json"))
            self.assertEqual(len(device.cleanup), 1)
            self.assertEqual(device.cleanup[0][:3], ("rm", "-rf", "--"))
            self.assertIsNotNone(replay.REMOTE_PATTERN.fullmatch(device.cleanup[0][3]))


if __name__ == "__main__":
    unittest.main()
