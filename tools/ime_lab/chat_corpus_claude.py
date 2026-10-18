"""Read human-typed prompts from local Claude Code transcripts.

Claude Code keeps one JSONL per session under ``~/.claude/projects/<project>/``.
Only ``type == "user"`` records carry human input, and most of those are tool
results or harness injections (system reminders, task notifications, continued
session summaries, relayed coordinator notes). A Projects session wraps the
person's words in a ``<wake>`` envelope whose ``<message from="human">`` body is
the typed text. Everything else is skipped; nothing here prints message text.
"""
import html
import json
import re
from pathlib import Path

# Text that starts with one of these was written by the harness, not the person.
_HARNESS_PREFIXES = (
    "<system-reminder", "<task-notification", "This session is being continued",
    "[Request interrupted", "<command-name", "<local-command", "<project_claude_message",
    "<cross-session-message", "<ci-monitor-event", "A message from this project",
)
_HUMAN_IN_WAKE = re.compile(
    r"<message\b(?=[^>]*\bfrom=\"human\")[^>]*>(.*?)</message>", re.DOTALL)
_SYSTEM_REMINDER = re.compile(r"<system-reminder>.*?</system-reminder>", re.DOTALL)
_IDE_TAG = re.compile(r"<ide_[a-z_]+>.*?</ide_[a-z_]+>", re.DOTALL)


def _text_of(content):
    if isinstance(content, str):
        return [content]
    if not isinstance(content, list):
        return []
    return [block["text"] for block in content
            if isinstance(block, dict) and block.get("type") == "text"
            and isinstance(block.get("text"), str)]


def human_text(text):
    """The person's own words in one user record, or '' when the harness wrote it."""
    if not isinstance(text, str):
        return ""
    stripped = text.lstrip()
    if stripped.startswith("<wake"):
        parts = [html.unescape(m.group(1)).strip() for m in _HUMAN_IN_WAKE.finditer(text)]
        return "\n".join(part for part in parts if part)
    if any(stripped.startswith(prefix) for prefix in _HARNESS_PREFIXES):
        return ""
    cleaned = _SYSTEM_REMINDER.sub("", text)
    cleaned = _IDE_TAG.sub("", cleaned)
    return cleaned.strip()


def iter_claude_messages(user_home):
    """Yield human-authored Claude Code prompts as chat_corpus records."""
    projects = Path(user_home) / ".claude" / "projects"
    if not projects.is_dir():
        return
    for path in sorted(projects.glob("*/*.jsonl")):
        with path.open(encoding="utf-8", errors="replace") as transcript:
            for number, line in enumerate(transcript, 1):
                try:
                    record = json.loads(line)
                except ValueError:
                    continue
                if not isinstance(record, dict) or record.get("type") != "user":
                    continue
                if record.get("userType") != "external" or record.get("isSidechain"):
                    continue
                message = record.get("message")
                if not isinstance(message, dict) or message.get("role") != "user":
                    continue
                text = "\n".join(filter(None, (human_text(t) for t in _text_of(message.get("content")))))
                if not text.strip():
                    continue
                session = record.get("sessionId") or path.stem
                identifier = record.get("uuid") or f"line:{number}"
                timestamp = record.get("timestamp")
                yield {
                    "platform": "claude",
                    "source_variant": "code",
                    "session_id": str(session),
                    "message_id": str(identifier),
                    "timestamp": timestamp if isinstance(timestamp, str) else None,
                    "text": text,
                    "source_path": str(path),
                }
