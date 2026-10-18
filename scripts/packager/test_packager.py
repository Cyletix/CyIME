"""Offline tests for the packager core: python -B scripts/packager/test_packager.py"""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cyime_packager as cp  # noqa: E402

GRADLE = '''android {
    defaultConfig {
        // versionCode = 1 comment
        versionCode = 20261136
        versionName = "1.12.13"
    }
}
'''


class VersionTest(unittest.TestCase):
    def test_bump(self):
        self.assertEqual(cp.bumped("1.12.13", 20261136, "patch"), ("1.12.14", 20261137))
        self.assertEqual(cp.bumped("1.12.13", 20261136, "minor"), ("1.13.0", 20261137))

    def test_write_version_only_touches_literals(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "app").mkdir()
            (root / "app/build.gradle.kts").write_text(GRADLE, encoding="utf-8")
            cp.write_version(root, "1.12.14", 20261137)
            text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
            self.assertIn('versionName = "1.12.14"', text)
            self.assertIn("versionCode = 20261137", text)
            self.assertIn("// versionCode = 1 comment", text)

    def test_needs_bump_reads_both_tools(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "app").mkdir()
            (root / "app/build.gradle.kts").write_text(GRADLE, encoding="utf-8")
            self.assertFalse(cp.needs_bump(root))
            cp.ad.write_json(root / ".gradle/auto-deliver/state.json", {"deliveredVersions": {"20261136": "1.12.13"}})
            self.assertTrue(cp.needs_bump(root))
            cp.write_version(root, "1.12.14", 20261137)
            self.assertFalse(cp.needs_bump(root))

    def test_built_but_not_installed_package_uses_up_the_version(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "app").mkdir()
            (root / "app/build.gradle.kts").write_text(GRADLE, encoding="utf-8")
            cp.ad.write_json(root / ".gradle/packager/state.json", {"outputs": {"20261136": {"share": "aa"}}})
            self.assertFalse(cp.needs_bump(root, "share"))   # same options again: keep the version
            self.assertTrue(cp.needs_bump(root, "mine"))     # different content: new version


class StagingTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)
        (self.root / "app/src/main/assets/rime_chinese").mkdir(parents=True)
        self.model = self.root / "model.lm"
        self.model.write_bytes(b"model-a")
        self.target = self.root / cp.T9_TARGET
        self.work = self.root / ".gradle/packager"

    def tearDown(self):
        self.dir.cleanup()

    def test_stages_and_removes(self):
        with cp.Staging(self.root, self.model, self.work):
            self.assertEqual(self.target.read_bytes(), b"model-a")
        self.assertFalse(self.target.exists())
        self.assertTrue(self.model.is_file())

    def test_removes_on_error(self):
        with self.assertRaises(RuntimeError):
            with cp.Staging(self.root, self.model, self.work):
                raise RuntimeError("build failed")
        self.assertFalse(self.target.exists())

    def test_keeps_someone_elses_identical_model(self):
        self.target.write_bytes(b"model-a")
        with cp.Staging(self.root, self.model, self.work):
            pass
        self.assertTrue(self.target.exists())

    def test_refuses_foreign_model(self):
        self.target.write_bytes(b"other")
        with self.assertRaises(cp.DeliveryError):
            with cp.Staging(self.root, self.model, self.work):
                pass
        self.assertEqual(self.target.read_bytes(), b"other")
        with self.assertRaises(cp.DeliveryError):
            with cp.Staging(self.root, None, self.work):
                pass

    def test_cleans_interrupted_run(self):
        staging = cp.Staging(self.root, self.model, self.work)
        staging.__enter__()  # simulate a killed process: no __exit__
        self.assertTrue(self.target.exists())
        with cp.Staging(self.root, None, self.work):
            self.assertFalse(self.target.exists())


class BanksTest(unittest.TestCase):
    """Handwriting banks are pushed after install; problems never fail the install."""
    def fake(self, directory, fail=False):
        calls = []
        def run(args, **_):
            calls.append(args)
            if fail:
                raise cp.DeliveryError("adb: device offline")
            return ""
        me = type("P", (), {})()
        me.config = {"handwritingBanks": {"dir": str(directory), "deviceDir": "/sdcard/x"}}
        me.r = type("R", (), {"run": staticmethod(run)})()
        me.adb, me.serial, me.stage = "adb", "S1", lambda name: None
        return me, calls

    def test_missing_folder_is_reported(self):
        me, calls = self.fake(Path(tempfile.gettempdir()) / "no-such-banks-dir")
        self.assertIn("未放置", cp.Packager.push_banks(me))
        self.assertEqual(calls, [])

    def test_push_and_failure(self):
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "english.tsv").write_text("a")
            me, calls = self.fake(d)
            self.assertIn("已放到设备", cp.Packager.push_banks(me))
            self.assertEqual(calls[1][-1], "/sdcard/x/english.tsv")
            me, _ = self.fake(d, fail=True)
            self.assertIn("推送失败", cp.Packager.push_banks(me))


class UploadTest(unittest.TestCase):
    def test_rejects_private_and_bad_names(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            private = root / ".codex-artifacts/chat.txt"
            private.parent.mkdir()
            private.write_text("x")
            with self.assertRaises(cp.DeliveryError):
                cp.ResourceUploader(root, "models", [private], [], False, False, True)
            ok = root / "a.lm"
            ok.write_text("x")
            with self.assertRaises(cp.DeliveryError):
                cp.ResourceUploader(root, "models", [ok], ["九键.lm"], False, False, True)
            cp.ResourceUploader(root, "models", [ok], ["t9-a.lm"], False, False, True)


class ArgsTest(unittest.TestCase):
    def test_preset_then_overrides(self):
        config = {"presets": {"p": {"build_type": "release", "t9_model": "m.lm", "exclude_dicts": ["x"], "install": True}}}
        parser_args = dict(preset="p", build_type="debug", offline_models=None, t9_model="none", exclude_dicts=[],
                           no_install=True, serial=None, rebuild=None, bump=None, publish=None, latest=None,
                           not_latest=False, prerelease=None, notes=None, yes=None, dry_run=True, check=None)
        opts = cp.build_options_from_args(type("A", (), parser_args)(), config)
        self.assertEqual(opts.build_type, "debug")
        self.assertEqual(opts.t9_model, "none")
        self.assertEqual(opts.exclude_dicts, ("x",))
        self.assertFalse(opts.install)


if __name__ == "__main__":
    unittest.main()
