import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
from dataclasses import replace
import shutil

from cyime_linux.rime import Rime


@unittest.skipUnless(os.environ.get("CYIME_RIME_SHARED"), "Set CYIME_RIME_SHARED for native Rime integration")
class RimeTests(unittest.TestCase):
    def test_isolated_ibus_preview_backspace_and_paging(self):
        self.run_ibus_smoke()

    @unittest.skipUnless(shutil.which("xvfb-run") and importlib.util.find_spec("Xlib"),
                         "Custom candidate window integration requires Xvfb and python3-xlib")
    def test_isolated_ibus_custom_candidates(self):
        self.run_ibus_smoke(custom=True)

    def run_ibus_smoke(self, custom=False):
        import setup_ibus
        from cyime_linux.ime_settings import ImeSettings, save
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            user = directory / "rime"
            setup_ibus.prepare(Path(os.environ["CYIME_RIME_SHARED"]), user)
            config = directory / "config"
            config.mkdir()
            environment = dict(os.environ, CYIME_ISOLATED_IBUS="1", CYIME_RIME_USER=str(user),
                XDG_CONFIG_HOME=str(config), XDG_DATA_HOME=str(directory / "data"),
                IBUS_ADDRESS=f"unix:path={directory / 'ibus.sock'}", XDG_SESSION_TYPE="x11")
            command = ["dbus-run-session", "--", sys.executable,
                str(Path(__file__).with_name("ibus_smoke.py"))]
            if custom:
                from PyQt6.QtGui import QImage, QColor
                image = QImage(80, 80, QImage.Format.Format_RGB32)
                image.fill(QColor("#125a44"))
                background = directory / "background.png"
                self.assertTrue(image.save(str(background)))
                save(replace(ImeSettings(), theme="custom", background=os.environ.get("CYIME_SMOKE_BACKGROUND", str(background))),
                     directory / "data/cyime-linux")
                environment["CYIME_SMOKE_CUSTOM"] = "1"
                command = ["xvfb-run", "-a"] + command
            result = subprocess.run(command, env=environment,
                capture_output=True, text=True, timeout=90)
            log = config / "ibus-test.log"
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr + (log.read_text() if log.exists() else ""))

    def test_pinyin_candidates_partial_selection_and_commit(self):
        spec = importlib.util.spec_from_file_location("setup_ibus", Path(__file__).resolve().parents[1] / "setup_ibus.py")
        setup = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(setup)
        with tempfile.TemporaryDirectory() as temporary:
            shared, user = Path(os.environ["CYIME_RIME_SHARED"]), Path(temporary) / "rime"
            setup.prepare(shared, user)
            engine = Rime(shared, user)
            session = engine.session()
            try:
                for letter in "nihao":
                    self.assertTrue(engine.key(session, ord(letter)))
                context = engine.context(session)
                self.assertTrue(context["preedit"])
                self.assertIn("你好", [text for text, _ in context["candidates"]])
                engine.key(session, ord(" "))
                self.assertEqual(engine.commit(session), "你好")
                self.assertEqual(engine.context(session)["preedit"], "")
                for letter in "jia":
                    engine.key(session, ord(letter))
                self.assertIn("加", [text for text, _ in engine.context(session)["candidates"]])
                engine.clear(session)
                for letter in "duile":
                    engine.key(session, ord(letter))
                self.assertTrue(engine.context(session)["candidates"])
            finally:
                engine.destroy(session)
                engine.close()


if __name__ == "__main__":
    unittest.main()
