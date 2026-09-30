import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

import chat_corpus


class ChatCorpusTest(unittest.TestCase):
    def test_private_sqlite_and_source_frequencies(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / "profile"
            home.mkdir()
            output = Path(directory) / "corpus.sqlite3"
            codex = {"platform": "codex", "source_variant": "session", "session_id": "a",
                     "message_id": "1", "timestamp": None, "text": "你好世界你好世界",
                     "source_path": str(home / "a.jsonl")}
            cline = {"platform": "cline", "source_variant": "cli", "session_id": "b",
                     "message_id": "1", "timestamp": None, "text": "你好世界",
                     "source_path": str(home / "b.json")}
            with patch.object(chat_corpus, "iter_codex_messages", return_value=iter((codex, codex))), \
                 patch.object(chat_corpus, "iter_cline_messages", return_value=iter((cline,))):
                result = chat_corpus.build_corpus(home, output, minimum=1)
            self.assertEqual(result["codex_messages"], 1)
            self.assertEqual(result["cline_messages"], 1)
            self.assertEqual(result["duplicate_records"], 1)
            with closing(sqlite3.connect(output)) as connection:
                self.assertEqual(connection.execute("PRAGMA integrity_check").fetchone()[0], "ok")
                self.assertEqual(connection.execute("SELECT COUNT(*) FROM messages").fetchone()[0], 2)
                self.assertEqual(connection.execute(
                    "SELECT occurrences, message_count FROM word_frequency WHERE platform='all' AND term='世界'"
                ).fetchone(), (3, 2))
                self.assertEqual(connection.execute(
                    "SELECT raw_text FROM codex_user_messages"
                ).fetchone()[0], codex["text"])
            self.assertTrue(result["word_frequency"].is_file())
            self.assertTrue(result["learning_jsonl"].is_file())
            with self.assertRaises(FileExistsError):
                chat_corpus.build_corpus(home, output)

    def test_rejects_output_inside_checkout_or_onedrive(self):
        with self.assertRaises(ValueError):
            chat_corpus.check_output_location(Path(__file__).parent / "private.sqlite3")
        with self.assertRaises(ValueError):
            chat_corpus.check_output_location(Path("D:/OneDrive/private.sqlite3"))


if __name__ == "__main__":
    unittest.main()
