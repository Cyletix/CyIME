import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


class DesktopTests(unittest.TestCase):
    def test_launchers_keep_virtual_environment_interpreter_path(self):
        import install_desktop
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            interpreter = directory / "venv/bin/python"
            interpreter.parent.mkdir(parents=True)
            interpreter.symlink_to(sys.executable)
            with patch.dict(os.environ, XDG_DATA_HOME=str(directory / "data"), XDG_CONFIG_HOME=str(directory / "config")), \
                    patch.object(sys, "executable", str(interpreter)), patch.object(sys, "argv", ["install_desktop.py", "--install"]):
                install_desktop.main()
            entry = (directory / "data/applications/cyime-linux.desktop").read_text()
            self.assertIn(f"Exec={interpreter} ", entry)
            service = (directory / "config/systemd/user/cyime-linux.service").read_text()
            self.assertIn(f"ExecStart={interpreter} ", service)

    def test_launchers_install_idempotently_without_overwriting_custom_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            environment = dict(os.environ, XDG_DATA_HOME=str(directory / "data"), XDG_CONFIG_HOME=str(directory / "config"))
            command = [sys.executable, str(Path(__file__).resolve().parents[1] / "install_desktop.py"), "--install"]
            for _ in range(2):
                subprocess.run(command, env=environment, check=True, capture_output=True)
            target = directory / "data/applications/cyime-settings.desktop"
            self.assertIn("settings", target.read_text())
            self.assertIn("Keywords=", target.read_text())
            self.assertIn("NoDisplay=true", target.read_text())
            self.assertIn("Name[zh_CN]=CyIME 剪贴簿", (directory / "data/applications/cyime-linux.desktop").read_text())
            target.write_text("user customization")
            self.assertNotEqual(subprocess.run(command, env=environment, capture_output=True).returncode, 0)
            self.assertEqual(target.read_text(), "user customization")
