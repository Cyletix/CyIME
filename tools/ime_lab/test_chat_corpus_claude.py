import json
import tempfile
import unittest
from pathlib import Path

import chat_corpus_claude


def _user(content, **extra):
    record = {"type": "user", "userType": "external", "isSidechain": False,
              "sessionId": "s1", "uuid": extra.pop("uuid", "u1"), "timestamp": "2026-10-13T00:00:00Z",
              "message": {"role": "user", "content": content}}
    record.update(extra)
    return record


class ClaudeTranscriptTest(unittest.TestCase):
    def test_only_human_prompts_are_kept(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            project = home / ".claude" / "projects" / "D--Example"
            project.mkdir(parents=True)
            records = [
                _user("今天天气不错", uuid="plain"),
                _user("打字测试<system-reminder>harness note</system-reminder>", uuid="reminder-tail"),
                _user("<system-reminder>whole record is harness</system-reminder>", uuid="reminder-only"),
                _user("<task-notification><task-id>x</task-id></task-notification>", uuid="notification"),
                _user("This session is being continued from a previous conversation", uuid="summary"),
                _user('<wake reason="mention"><thread><message trigger="true" from="human" id="m1">继续 &amp; 加油</message>'
                      '<message from="system">not a person</message></thread></wake>', uuid="wake"),
                _user([{"type": "tool_result", "content": "tool output"}], uuid="tool"),
                _user([{"type": "text", "text": "带附件的问题"}, {"type": "image"}], uuid="blocks"),
                _user("旁线不算", uuid="side", isSidechain=True),
                _user("别的类型", uuid="internal", userType="internal"),
                {"type": "assistant", "message": {"role": "assistant", "content": "回答"}},
                {"type": "last-prompt", "lastPrompt": "提示缓存"},
            ]
            with (project / "session.jsonl").open("w", encoding="utf-8") as out:
                for record in records:
                    out.write(json.dumps(record, ensure_ascii=False) + "\n")
                out.write("not json\n")
            messages = list(chat_corpus_claude.iter_claude_messages(home))
        texts = {m["message_id"]: m["text"] for m in messages}
        self.assertEqual(texts, {"plain": "今天天气不错", "reminder-tail": "打字测试",
                                 "wake": "继续 & 加油", "blocks": "带附件的问题"})
        for message in messages:
            self.assertEqual(message["platform"], "claude")
            self.assertEqual(message["source_variant"], "code")
            self.assertEqual(message["session_id"], "s1")
            self.assertTrue(message["source_path"].endswith("session.jsonl"))

    def test_missing_home_yields_nothing(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(list(chat_corpus_claude.iter_claude_messages(Path(directory))), [])


if __name__ == "__main__":
    unittest.main()
