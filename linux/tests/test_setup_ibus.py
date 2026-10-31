import os
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch, Mock

import setup_ibus


class SetupTests(unittest.TestCase):
    def test_search_path_keeps_system_and_custom_components(self):
        with patch.dict(os.environ, {"XDG_DATA_HOME": "/tmp/local-data", "XDG_DATA_DIRS": "/usr/share:/opt/share",
                                    "IBUS_COMPONENT_PATH": "/custom:/usr/share/ibus/component"}):
            self.assertEqual(setup_ibus.component_search_path().split(":"),
                             ["/tmp/local-data/ibus/component", "/custom", "/usr/share/ibus/component", "/opt/share/ibus/component"])

    def test_login_environment_is_idempotent_and_preserves_edits(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": directory}):
                setup_ibus.install_search_path()
                setup_ibus.install_search_path()
                target = Path(directory) / "environment.d/60-cyime-ibus.conf"
                self.assertEqual(target.stat().st_mode & 0o777, 0o600)
                target.write_text("# custom configuration\n")
                with self.assertRaisesRegex(RuntimeError, "preserved"):
                    setup_ibus.install_search_path()
                self.assertEqual(target.read_text(), "# custom configuration\n")

    def test_gnome_sources_append_once_without_changing_original_order(self):
        from gi.repository import Gio
        original = [("xkb", "cn"), ("ibus", "libpinyin")]
        settings = Mock()
        settings.get_value.return_value.unpack.return_value = original
        with patch.object(Gio.Settings, "new", return_value=settings), patch.object(Gio.Settings, "sync"):
            setup_ibus.enable_gnome_source()
            value = settings.set_value.call_args.args[1].unpack()
            self.assertEqual(value, original + [("ibus", "cyime-rime")])
            settings.get_value.return_value.unpack.return_value = value
            settings.set_value.reset_mock()
            setup_ibus.enable_gnome_source()
            settings.set_value.assert_not_called()

    def test_refresh_retries_until_the_previous_engine_is_restored(self):
        import gi
        gi.require_version("IBus", "1.0")
        from gi.repository import IBus
        bus = Mock()
        bus.get_global_engine.return_value.get_name.return_value = "libpinyin"
        def result(code=0, output=""):
            return subprocess.CompletedProcess([], code, stdout=output, stderr="")
        answers = [result(), result(), result(), result(output="cyime-rime\nlibpinyin\n"), result(output="xkb:us::eng\n"), result(1),
                   result(output="cyime-rime\nlibpinyin\n"), result(output="libpinyin\n")]
        with patch.object(IBus, "Bus", return_value=bus), patch.object(IBus, "init"), \
                patch.object(setup_ibus.subprocess, "run", side_effect=answers) as run, \
                patch.object(setup_ibus.time, "sleep"):
            setup_ibus.refresh_ibus("/tmp/components:/usr/share/ibus/component")
            self.assertEqual(run.call_count, 8)
            self.assertEqual(run.call_args.args[0], ["ibus", "engine"])


if __name__ == "__main__":
    unittest.main()
