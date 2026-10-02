import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import lexicon_source as lex


def entry(word="银行", pinyin="yin2 hang2", **extra):
    return {"word": word, "pinyin": pinyin, "kind": "polyphone", "sense": "示例释义",
            "example": f"这里使用{word}。", "confusions": [], **extra}


def reply(*entries):
    return json.dumps({"format": lex.FORMAT, "entries": list(entries)}, ensure_ascii=False)


class Reference:
    syllables = {"yin", "hang", "xing", "zou", "lv", "se", "chang", "zhang", "da"}

    def check(self, word, pinyin):
        if (word, pinyin) in {( "银行", "yin2 hang2"), ("行走", "xing2 zou3"), ("绿色", "lv4 se4")}:
            return "reference_match", "test phrase reference"
        return "needs_review", "not matched"


class LexiconSourceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.db = lex.connect(self.root)
        self.addCleanup(self.db.close)

    def ingest(self, *entries, collection="common"):
        run, _ = lex.create_run(self.db, self.root, collection, "test topic", "test-model", "test prompt")
        return lex.ingest(self.db, run, reply(*entries), Reference())

    def test_excludes_contextually_wrong_reading_even_when_both_syllables_exist(self):
        result = self.ingest(entry(), entry(pinyin="yin2 xing2"), entry("行走", "xing2 zou3"))
        self.assertEqual(result["reference_match"], 2)
        self.assertEqual(result["needs_review"], 1)
        exported = lex.export(self.db, self.root, "common", "常用词")
        data = json.loads((Path(exported["directory"]) / "common.cyime-dict.json").read_text(encoding="utf-8"))
        self.assertEqual({(e["word"], e["code"]) for e in data["entries"]}, {("银行", "yin hang"), ("行走", "xing zou")})
        self.assertEqual(exported["pending"], 1)

    def test_repeat_generation_deduplicates_and_preserves_every_observation_and_collection(self):
        self.assertEqual(self.ingest(entry())["new"], 1)
        self.assertEqual(self.ingest(entry(), collection="finance")["duplicates"], 1)
        self.assertEqual(self.db.execute("SELECT count(*) FROM entries").fetchone()[0], 1)
        self.assertEqual(self.db.execute("SELECT count(*) FROM observations").fetchone()[0], 2)
        self.assertEqual(self.db.execute("SELECT count(*) FROM memberships").fetchone()[0], 2)

    def test_mistakes_are_archived_but_never_become_candidates_or_aliases(self):
        confusion = [{"type": "spelling", "text": "银航", "pinyin": ""},
                     {"type": "reading", "text": "银行", "pinyin": "yin2 xing2"}]
        self.ingest(entry(confusions=confusion))
        result = lex.export(self.db, self.root, "common", "词库")
        text = (Path(result["directory"]) / "common.dict.yaml").read_text(encoding="utf-8")
        self.assertNotIn("银航", text)
        self.assertNotIn("yin xing", text)
        self.assertIn("银航", self.db.execute("SELECT payload FROM observations").fetchone()[0])

    def test_rejected_entry_is_not_revived_by_later_model_agreement(self):
        self.ingest(entry())
        output = Path(lex.export(self.db, self.root, "common", "词库")["directory"])
        unrelated = output / "review-notes.txt"
        unrelated.write_text("keep")
        self.db.execute("UPDATE entries SET status='rejected'")
        self.db.commit()
        self.ingest(entry())
        self.assertEqual(self.db.execute("SELECT status FROM entries").fetchone()[0], "rejected")
        result = lex.export(self.db, self.root, "common", "词库")
        self.assertEqual(result["entries"], 0)
        self.assertFalse((output / "common.cyime-dict.json").exists())
        self.assertFalse((output / "common.dict.yaml").exists())
        self.assertTrue(unrelated.exists())
        self.assertEqual(json.loads((output / "manifest.json").read_text())["files"], {})

    def test_invalid_rows_are_quarantined_without_discarding_valid_neighbors(self):
        result = self.ingest(entry(), entry("银\n行"), entry(pinyin="yin2 abcdef3"), entry(pinyin="yin2"),
                             entry(kind=[]), entry(extra="unexpected"))
        self.assertEqual(result["invalid"], 5)
        self.assertEqual(self.db.execute("SELECT count(*) FROM entries").fetchone()[0], 1)
        self.assertEqual(self.db.execute("SELECT count(*) FROM observations WHERE error IS NOT NULL").fetchone()[0], 5)

    def test_numbered_tones_are_required_and_v_is_retained(self):
        self.ingest(entry("绿色", "lv4 se4"))
        self.assertEqual(self.db.execute("SELECT code FROM entries").fetchone()[0], "lv se")
        for text in ("yin hang", "yín háng", "yin0 hang2", "yin2  hang2", "yin2\thang2"):
            with self.assertRaises(ValueError):
                lex.validate_entry(entry(pinyin=text), Reference.syllables)

    def test_malformed_envelope_does_not_insert_partial_words(self):
        for text in ("```json\n" + reply(entry()) + "\n```", reply(entry())[:-2], '{"format":"old","entries":[]}', reply()):
            with self.assertRaises(ValueError): lex.parse_reply(text)
        self.assertEqual(self.db.execute("SELECT count(*) FROM entries").fetchone()[0], 0)

    def test_final_event_only_does_not_duplicate_deltas_or_import_reasoning(self):
        text = reply(entry())
        events = [{"type": "agent_event", "event": e} for e in [
            {"type": "content_start", "contentType": "reasoning", "text": "private intermediate text"},
            {"type": "content_start", "contentType": "text", "text": text[:10]},
            {"type": "content_end", "contentType": "text", "text": text},
            {"type": "done", "reason": "completed", "text": text}]]
        self.assertEqual(lex.extract_cline_reply("[warn] test\n" + "\n".join(map(json.dumps, events))), text)

    def test_partial_failed_or_tool_using_cline_runs_never_enter_database(self):
        for events in [[], [{"type": "content_end", "contentType": "text", "text": reply(entry())}],
                       [{"type": "done", "reason": "aborted", "text": reply(entry())}],
                       [{"type": "content_start", "contentType": "tool"}, {"type": "done", "reason": "completed", "text": reply(entry())}]]:
            with self.assertRaises(ValueError):
                lex.extract_cline_reply("\n".join(json.dumps({"type": "agent_event", "event": e}) for e in events))

    def test_export_is_repeatable_and_scoped_to_requested_collection(self):
        self.ingest(entry(), collection="finance")
        self.ingest(entry("行走", "xing2 zou3"), collection="daily")
        path = Path(lex.export(self.db, self.root, "finance", "金融")['directory']) / "finance.cyime-dict.json"
        first = path.read_bytes()
        lex.export(self.db, self.root, "finance", "金融")
        self.assertEqual(first, path.read_bytes())
        self.assertNotIn("行走", first.decode())

    def test_topic_is_data_and_previous_words_are_sent_as_exclusions(self):
        prompt = lex.prompt_for('行业词汇，包含"引号"', 16, "mixed", ["银行"])
        self.assertIn('"exclude_words": ["银行"]', prompt)
        with self.assertRaises(ValueError): lex.slug("../../escape")

    def test_saved_cline_credential_is_scoped_to_child_environment(self):
        data = self.root / "config"
        data.mkdir()
        (data / "secrets.json").write_text('{"deepSeekApiKey":"fake-key","other":"not-copied"}')
        with patch.dict(lex.os.environ, {"DEEPSEEK_API_KEY": "old-key"}):
            env = lex.cline_environment("cline", data)
            self.assertEqual(env["DEEPSEEK_API_KEY"], "fake-key")
            self.assertEqual(lex.os.environ["DEEPSEEK_API_KEY"], "old-key")
            self.assertNotIn("other", env)

    def test_installed_reference_requires_exact_whole_phrase_and_keeps_unknowns_pending(self):
        try: reference = lex.Reference()
        except ValueError: self.skipTest("pypinyin is an optional generation-tool dependency")
        self.assertEqual(reference.check("银行", "yin2 hang2")[0], "reference_match")
        self.assertEqual(reference.check("银行", "yin2 xing2")[0], "needs_review")
        self.assertEqual(reference.check("长大", "chang2 da4")[0], "needs_review")
        self.assertEqual(reference.check("不存在的编造词", "bu4 cun2 zai4 de5 bian1 zao4 ci2")[0], "needs_review")


if __name__ == "__main__":
    unittest.main()
