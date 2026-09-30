import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from chat_corpus_codex import iter_codex_messages


class CodexCorpusTest(unittest.TestCase):
    def test_projection_covers_active_and_archived_without_subagents(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            active = home / "sessions" / "active.jsonl"
            archived = home / "archived_sessions" / "archived.jsonl"
            active.parent.mkdir()
            archived.parent.mkdir()
            active.touch()
            archived.touch()
            with closing(sqlite3.connect(home / "state_5.sqlite")) as db:
                db.execute(
                    "CREATE TABLE threads (id TEXT, rollout_path TEXT, source TEXT, "
                    "thread_source TEXT, agent_path TEXT)"
                )
                db.executemany(
                    "INSERT INTO threads VALUES (?, ?, ?, ?, ?)",
                    [
                        ("active", str(active), "vscode", "user", None),
                        ("archived", str(archived), "cli", "user", None),
                        ("agent", str(active), '{"subagent":{}}', "subagent", "/root/a"),
                    ],
                )
                db.commit()
            with closing(sqlite3.connect(home / "thread_history_1.sqlite")) as db:
                db.execute(
                    "CREATE TABLE thread_items (thread_id TEXT, item_id TEXT, "
                    "item_json TEXT, created_at_ms INTEGER, item_type TEXT, rollout_ordinal INTEGER)"
                )
                def item(text, kind="userMessage"):
                    return json.dumps({"type": kind, "content": [{"type": "text", "text": text}]})
                db.executemany(
                    "INSERT INTO thread_items VALUES (?, ?, ?, ?, ?, ?)",
                    [
                        ("active", "u1", item("你好"), 1000, "userMessage", 1),
                        ("active", "u1", item("你好"), 1000, "userMessage", 2),
                        ("archived", "u2", item("归档输入"), 2000, "userMessage", 1),
                        ("agent", "u3", item("内部委派"), 3000, "userMessage", 1),
                        ("active", "a1", item("助手", "agentMessage"), 4000, "agentMessage", 3),
                    ],
                )
                db.commit()
            (home / "history.jsonl").write_text(
                "\n".join(
                    json.dumps(row, ensure_ascii=False)
                    for row in [
                        {"session_id": "active", "ts": 1, "text": "你好"},
                        {"session_id": "archived", "ts": 2, "text": "归档输入"},
                        {"session_id": "active", "ts": 3, "text": "补录输入"},
                        {"session_id": "agent", "ts": 4, "text": "内部委派"},
                    ]
                ) + "\n",
                encoding="utf-8",
            )

            rows = list(iter_codex_messages(home))
            self.assertEqual([row["text"] for row in rows], ["你好", "归档输入", "补录输入"])
            self.assertEqual([row["source_variant"] for row in rows],
                             ["thread_history", "thread_history", "history_jsonl"])
            self.assertEqual(rows[0]["source_path"], str(active))
            self.assertEqual(rows[1]["source_path"], str(archived))
            self.assertEqual(rows[0]["timestamp"], "1970-01-01T00:00:01.000Z")
            self.assertTrue(all(row["platform"] == "codex" for row in rows))

    def test_cli_history_without_projection(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home / "history.jsonl").write_text(
                json.dumps({"session_id": "old", "ts": 10, "text": "旧版输入"}) + "\n",
                encoding="utf-8",
            )
            rows = list(iter_codex_messages(home))
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["message_id"], "history:1")
            self.assertEqual(rows[0]["timestamp"], "1970-01-01T00:00:10.000Z")

    def test_repeated_cli_prompt_is_two_user_messages(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            entry = json.dumps({"session_id": "old", "ts": 10, "text": "继续"})
            (home / "history.jsonl").write_text(entry + "\n" + entry + "\n", encoding="utf-8")
            rows = list(iter_codex_messages(home))
            self.assertEqual([row["message_id"] for row in rows], ["history:1", "history:2"])
            self.assertEqual([row["text"] for row in rows], ["继续", "继续"])

    def test_resumed_rollout_supplies_unprojected_user_turn(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            folder = home / "sessions"
            folder.mkdir()
            first = folder / "first.jsonl"
            continuation = folder / "continuation.jsonl"
            def write_rows(path, rows):
                path.write_text(
                    "\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8"
                )
            def meta(ordinal):
                return {"ordinal": ordinal, "type": "session_meta", "payload": {
                    "id": "main", "session_id": "shared-parent", "source": "vscode"
                }}
            def user_event(ordinal, item_id, turn_id, text):
                return {"ordinal": ordinal, "timestamp": "2026-09-01T00:00:00Z",
                        "type": "event_msg", "payload": {"type": "item_completed",
                        "turn_id": turn_id, "item": {"type": "UserMessage", "id": item_id,
                        "content": [{"type": "text", "text": text}]}}}
            write_rows(first, [meta(0), user_event(1, "old", "t1", "旧输入")])
            write_rows(continuation, [
                meta(2),
                {"ordinal": 3, "type": "event_msg", "payload": {
                    "type": "task_started", "turn_id": "t2"}},
                user_event(4, "new", "t2", "新输入"),
                user_event(5, "replay", "not-started", "内部重放"),
            ])
            with closing(sqlite3.connect(home / "state_5.sqlite")) as db:
                db.execute("CREATE TABLE threads (id TEXT, rollout_path TEXT, source TEXT, "
                           "thread_source TEXT, agent_path TEXT)")
                db.execute("INSERT INTO threads VALUES (?, ?, ?, ?, ?)",
                           ("main", str(continuation), "vscode", "user", None))
                db.commit()
            with closing(sqlite3.connect(home / "thread_history_1.sqlite")) as db:
                db.execute("CREATE TABLE thread_items (thread_id TEXT, item_id TEXT, "
                           "item_json TEXT, created_at_ms INTEGER, item_type TEXT, rollout_ordinal INTEGER)")
                db.execute("CREATE TABLE thread_history_projection_state "
                           "(thread_id TEXT, next_rollout_byte_offset INTEGER)")
                db.execute("INSERT INTO thread_items VALUES (?, ?, ?, ?, ?, ?)",
                           ("main", "old", json.dumps({"type": "userMessage",
                            "content": [{"type": "text", "text": "旧输入"}]}), 1000, "userMessage", 1))
                db.execute("INSERT INTO thread_history_projection_state VALUES (?, ?)",
                           ("main", first.stat().st_size))
                db.commit()
            rows = list(iter_codex_messages(home))
            self.assertEqual([row["text"] for row in rows], ["旧输入", "新输入"])
            self.assertEqual([row["source_path"] for row in rows],
                             [str(first), str(continuation)])
            self.assertEqual([row["source_variant"] for row in rows],
                             ["thread_history", "rollout_user_event"])


if __name__ == "__main__":
    unittest.main()
