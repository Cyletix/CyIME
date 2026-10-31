import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest


class PasteIntegrationTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("xvfb-run") and importlib.util.find_spec("Xlib"),
                         "Requires Xvfb and python-xlib")
    def test_edit_then_double_click_reaches_an_actual_external_editor(self):
        environment = dict(os.environ, QT_QPA_PLATFORM="xcb", XDG_SESSION_TYPE="x11",
                           CYIME_ISOLATED_CLIPBOARD="1")
        result = subprocess.run(["xvfb-run", "-a", sys.executable,
                                 str(Path(__file__).with_name("clipboard_smoke.py"))],
                                env=environment, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("real Ctrl+V input", result.stdout)
