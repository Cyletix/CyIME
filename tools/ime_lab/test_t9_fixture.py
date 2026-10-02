"""Input integrity checks for reproducible arbitrary-text T9 replay fixtures."""

from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
import io
import json
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import t9_fixture as fixture


class T9FixtureTest(unittest.TestCase):
    def sample(self):
        return fixture.prepare_fixture("早上 07:12 小周：你好！\r\n", ["zao", "shang", "xiao", "zhou", "ni", "hao"])

    def test_preserves_every_source_span_and_literal_without_guessing_roles(self):
        result = self.sample()
        self.assertEqual(result["source_text"], "早上 07:12 小周：你好！\r\n")
        self.assertEqual([case["text"] for case in result["cases"]], ["早上", "小周", "你好"])
        self.assertEqual(result["counts"]["han_characters"], 6)
        self.assertEqual(result["counts"]["body_cases"], 3)
        self.assertEqual(result["pronunciation_review"]["review_status"], "provided")
        self.assertEqual(result, fixture.validate_fixture(result, require_source=True))

    def test_whole_missing_or_reordered_case_cannot_hide_behind_correct_individual_keys(self):
        original = self.sample()
        for failure in ("omit", "reorder", "duplicate", "offset", "hash", "count", "keys", "syllables"):
            broken = deepcopy(original)
            if failure == "omit":
                broken["cases"].pop(1)
            elif failure == "reorder":
                broken["cases"].reverse()
            elif failure == "duplicate":
                broken["cases"][1]["id"] = broken["cases"][0]["id"]
            elif failure == "offset":
                broken["cases"][0]["source_start"] = 1
            elif failure == "hash":
                broken["source_text"] += " "
            elif failure == "count":
                broken["counts"]["han_characters"] = 99
            elif failure == "keys":
                broken["cases"][0]["keys"] = "222"
            else:
                broken["cases"][0]["syllable_keys"] = ["2", "2"]
            with self.subTest(failure=failure), self.assertRaises(fixture.FixtureError):
                fixture.validate_fixture(broken)

    def test_unicode_offsets_are_codepoints_and_source_lines_are_preserved(self):
        result = fixture.prepare_fixture("🙂\r\n𠀀你好。〇", ["he", "ni", "hao", "ling"])
        self.assertEqual(result["cases"][0]["source_start"], 3)
        self.assertEqual(result["cases"][0]["source_end"], 6)
        self.assertEqual(result["cases"][0]["source_line"], 2)
        self.assertEqual(result["counts"]["han_characters"], 4)

    def test_normalizes_conventional_readings_to_engine_spelling(self):
        for token, expected in [("NÜ3", "nv"), ("nu:3", "nv"), ("nǚ", "nv"),
                                ("lüè", "lve"), ("hao3", "hao"), ("le0", "le"), ("SHUI4", "shui")]:
            with self.subTest(token=token):
                self.assertEqual(fixture.normalize_pinyin(token), expected)
        result = fixture.prepare_fixture("女", ["nǚ"])
        self.assertEqual(result["cases"][0]["keys"], "68")
        self.assertEqual(result["cases"][0]["pinyin"], ["nv"])

    def test_rejects_unmapped_characters_and_invalid_syllables(self):
        for token in ("", "nihao", "ni6", "n3i", "你", "nǐ!", "zzz", "a'b", None, "ni33"):
            with self.subTest(token=token), self.assertRaises(fixture.FixtureError):
                fixture.prepare_fixture("你", [token])
        with self.assertRaisesRegex(fixture.FixtureError, "length mismatch"):
            fixture.prepare_fixture("你好", ["ni"])
        with self.assertRaisesRegex(fixture.FixtureError, "no Han"):
            fixture.prepare_fixture("API 123?!", [])

    def test_overrides_are_explicit_recorded_and_aligned_to_case_ids(self):
        result = fixture.prepare_fixture("睡不着。好", ["shui", "bu", "zhe", "hao"],
                                         readings={"c001": ["shui", "bu", "zhao"]})
        self.assertEqual(result["cases"][0]["pinyin"], ["shui", "bu", "zhao"])
        self.assertEqual(result["pronunciation_review"]["overrides"][0]["before"], ["shui", "bu", "zhe"])
        self.assertEqual(result["pronunciation_review"]["review_status"], "provided")
        for corrections in ({"c000": ["ni"]}, {"c001": ["ni", "hao"]}, {"c001": "ni"}):
            with self.subTest(corrections=corrections), self.assertRaises(fixture.FixtureError):
                fixture.prepare_fixture("你", ["ni"], readings=corrections)

    def test_auto_pinyin_keeps_phrase_context_and_never_claims_review(self):
        calls = []

        def fake_pinyin(text, **unused):
            calls.append(text)
            return {"睡不着": ["shui", "bu", "zhe"], "好": ["hao"]}[text]

        with patch.dict("sys.modules", {"pypinyin": SimpleNamespace(lazy_pinyin=fake_pinyin)}), \
                patch("t9_fixture.importlib.metadata.version", return_value="test"):
            result = fixture.prepare_fixture("睡不着。好", auto_pinyin=True,
                                             readings={"c001": ["shui", "bu", "zhao"]})
        self.assertEqual(calls, ["睡不着", "好"])
        self.assertEqual(result["pronunciation_review"]["review_status"], "unreviewed")
        with patch.dict("sys.modules", {"pypinyin": None}), self.assertRaisesRegex(fixture.FixtureError, "already installed"):
            fixture.prepare_fixture("你好", auto_pinyin=True)

    def test_validator_does_not_mutate_input_and_accepts_cases_only_fixture(self):
        minimal = {"id": "short", "cases": [{"id": "c001", "text": "女", "pinyin": ["nü3"]}]}
        saved = deepcopy(minimal)
        result = fixture.validate_fixture(minimal)
        self.assertEqual(minimal, saved)
        self.assertEqual(result["cases"][0]["keys"], "68")
        with self.assertRaisesRegex(fixture.FixtureError, "source_text is required"):
            fixture.validate_fixture(minimal, require_source=True)

    def test_fixture_contract_cannot_change_language_scheme_or_keymap_silently(self):
        for key, value in (("language", "en"), ("scheme", "other"), ("layout", "qwerty"), ("keymap", {})):
            result = self.sample()
            result["input_contract"][key] = value
            with self.subTest(key=key), self.assertRaises(fixture.FixtureError):
                fixture.validate_fixture(result)

    def test_existing_daily_fixture_passes_without_modification(self):
        path = Path(__file__).parent / "fixtures" / "daily_chat_20261003.json"
        original = json.loads(path.read_text(encoding="utf-8-sig"))
        self.assertEqual(fixture.validate_fixture(original, require_source=True), original)

    def test_pinyin_inventory_matches_production_decoder(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "app/src/main/jni/librime-t9/src/t9_pinyin_map.cc").read_text(encoding="utf-8")
        declaration = source.split("kPinyinList = {", 1)[1].split("};", 1)[0]
        self.assertEqual(fixture.PINYIN_SYLLABLES, set(re.findall(r'"([a-z]+)"', declaration)))

    def test_cli_preserves_crlf_reads_json_tokens_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            source = path / "chat.txt"
            tokens = path / "pinyin.json"
            output = path / "fixture.json"
            source.write_bytes("你好！\r\n".encode("utf-8"))
            tokens.write_text('["ni3", "hao3"]', encoding="utf-8")
            arguments = ["prepare", "--text", str(source), "--pinyin", str(tokens), "--output", str(output)]
            with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(fixture.main(arguments), 0)
                content = output.read_bytes()
                self.assertEqual(fixture.main(arguments), 2)
                self.assertEqual(output.read_bytes(), content)
                self.assertEqual(fixture.main(["validate", str(output), "--require-source"]), 0)
            result = json.loads(content)
            self.assertEqual(result["source_text"], "你好！\r\n")


if __name__ == "__main__":
    unittest.main()
