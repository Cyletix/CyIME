import json
import tempfile
import unittest
from pathlib import Path

from chat_corpus_cline import iter_cline_messages


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")


class ClineCorpusTest(unittest.TestCase):
    def test_new_transcript_keeps_human_text_only(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            session = home / ".cline/data/sessions/session-a"
            write_json(session / "session-a.json", {"source": "cli"})
            write_json(session / "session-a.messages.json", {
                "messages": [
                    {"id": "one", "role": "user", "ts": 1000, "content": [
                        {"type": "text", "text": "请修复输入法"},
                        {"type": "text", "text": "<environment_details>机器信息</environment_details>"},
                        {"type": "image", "data": "image-data"},
                        {"type": "text", "text": "保留用户补充"},
                    ]},
                    {"id": "tool", "role": "user", "ts": 1001, "content": [
                        {"type": "tool_result", "content": "工具输出"},
                    ]},
                    {"id": "assistant", "role": "assistant", "ts": 1002, "content": [
                        {"type": "text", "text": "模型回答"},
                    ]},
                ],
            })
            records = list(iter_cline_messages(home))
            self.assertEqual(len(records), 1)
            self.assertEqual(records[0]["text"], "请修复输入法\n\n保留用户补充")
            self.assertEqual(records[0]["timestamp"], "1970-01-01T00:00:01+00:00")
            self.assertEqual(records[0]["source_variant"], "cli")
            self.assertEqual(records[0]["session_id"], "cli:session-a")
            self.assertEqual(records[0]["message_id"], "transcript:one")

    def test_excludes_automations_subagents_and_core(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            for name, source, origin in (
                ("automated", "vscode", {"mode": "automation", "trigger": "hub-schedule"}),
                ("subagent", "cli", {"mode": "subagent"}),
                ("core", "core", {"mode": "user"}),
            ):
                session = home / ".cline/data/sessions" / name
                write_json(session / f"{name}.json", {"source": source})
                write_json(session / f"{name}.messages.json", {
                    "origin": origin,
                    "messages": [{"role": "user", "id": "x", "ts": 1,
                                  "content": [{"type": "text", "text": "排除内容"}]}],
                })
            self.assertEqual(list(iter_cline_messages(home)), [])

    def test_old_ui_history_and_cross_store_overlap(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            session = home / ".cline/data/sessions/123"
            write_json(session / "123.json", {"source": "vscode"})
            write_json(session / "123.messages.json", {
                "origin": {"source": "vscode", "mode": "user"},
                "messages": [{"role": "user", "id": "new", "ts": 1000,
                              "content": [{"type": "text", "text": "初始问题"}]}],
            })
            ui = home / "AppData/Roaming/Code/User/globalStorage/saoudrizwan.claude-dev/tasks/123/ui_messages.json"
            write_json(ui, [
                {"type": "say", "say": "task", "ts": 1000, "text": "初始问题"},
                {"type": "say", "say": "text", "ts": 1001, "text": "助手回答"},
                {"type": "ask", "ask": "followup", "ts": 1002, "text": "系统询问"},
                {"type": "say", "say": "user_feedback", "ts": 1003, "text": "继续修改"},
                {"type": "say", "say": "task", "ts": 1004, "text": "非首条任务"},
            ])
            older = ui.parents[1] / "456" / "ui_messages.json"
            write_json(older, [
                {"type": "say", "say": "text", "ts": 2000, "text": "旧版初始问题"},
                {"type": "say", "say": "text", "ts": 2001, "text": "助手回答"},
            ])
            records = list(iter_cline_messages(home))
            self.assertEqual([r["text"] for r in records],
                             ["初始问题", "继续修改", "旧版初始问题"])
            self.assertEqual([r["message_id"] for r in records],
                             ["transcript:new", "ui:Code:3", "ui:Code:0"])
            self.assertEqual(records[1]["source_variant"], "vscode")
            self.assertEqual(records[1]["source_path"], str(ui))


if __name__ == "__main__":
    unittest.main()
