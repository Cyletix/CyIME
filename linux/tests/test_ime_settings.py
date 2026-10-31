from dataclasses import replace
from pathlib import Path
import tempfile
import unittest

from cyime_linux.ime_settings import ImeSettings, load, presentation, save


class ImeSettingsTests(unittest.TestCase):
    def test_defaults_are_six_candidates_and_system_theme(self):
        self.assertEqual(ImeSettings().page_size, 6)
        self.assertEqual(ImeSettings().theme, "system")
        self.assertEqual(ImeSettings().background, "")

    def test_round_trip_and_invalid_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            settings = replace(ImeSettings(), page_size=8, theme="dark", inline_preview=False)
            save(settings, directory)
            self.assertEqual(load(directory), settings)
            self.assertEqual((directory / "ime.json").stat().st_mode & 0o777, 0o600)
            (directory / "ime.json").write_text("invalid")
            self.assertEqual(load(directory), ImeSettings())
        safe = ImeSettings.from_dict({"page_size": 99, "font_size": -1, "theme": "bad",
                                      "foreground": "red;", "inline_preview": "false"})
        self.assertEqual((safe.page_size, safe.font_size, safe.theme), (9, 12, "system"))
        self.assertEqual(safe.foreground, ImeSettings().foreground)
        self.assertTrue(safe.inline_preview)

    def test_inline_preview_and_reading_remain_separate(self):
        context = dict(preedit="ni hao", preview="你好", candidates=[("你好", "")])
        self.assertEqual(presentation(context, ImeSettings()), ("你好", "ni hao"))
        self.assertEqual(presentation(context, replace(ImeSettings(), inline_preview=False)), ("ni hao", "ni hao"))
        self.assertEqual(presentation(dict(preedit="", candidates=[]), ImeSettings()), ("", ""))
