"""Extract human-authored Cline messages from its CLI and VS Code histories.

The CLI transcript uses ``role=user`` for tool results as well as human input.
The older VS Code extension keeps human input in its UI history, not in the API
conversation history (which also contains generated context and tool output).
"""

import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path


_ENVIRONMENT_DETAILS = re.compile(
    r"<environment_details>.*?</environment_details>", re.DOTALL
)


def _iso_timestamp(milliseconds):
    if isinstance(milliseconds, bool) or not isinstance(milliseconds, (int, float)):
        return None
    try:
        return datetime.fromtimestamp(milliseconds / 1000, timezone.utc).isoformat()
    except (OverflowError, OSError, ValueError):
        return None


def _text_blocks(message):
    blocks = message.get("content")
    if not isinstance(blocks, list):
        return ""
    texts = []
    for block in blocks:
        if not isinstance(block, dict) or block.get("type") != "text":
            continue
        value = block.get("text")
        if not isinstance(value, str):
            continue
        # Cline appends this generated block to some VS Code user messages.
        value = _ENVIRONMENT_DETAILS.sub("", value).strip()
        if value:
            texts.append(value)
    return "\n\n".join(texts)


def _signature(record):
    digest = hashlib.sha256(record["text"].encode("utf-8")).digest()
    return record["session_id"], record["timestamp"], digest


def _new_sessions(user_home):
    sessions = user_home / ".cline" / "data" / "sessions"
    for path in sorted(sessions.glob("*/*.messages.json")):
        session_id = path.parent.name
        metadata_path = path.parent / f"{session_id}.json"
        metadata = json.loads(metadata_path.read_text(encoding="utf-8")) if metadata_path.is_file() else {}
        if metadata.get("source") not in {None, "cli", "vscode"}:
            continue
        extra = metadata.get("metadata") or {}
        if isinstance(extra, dict) and extra.get("mode") in {"automation", "subagent"}:
            continue
        transcript = json.loads(path.read_text(encoding="utf-8"))
        origin = transcript.get("origin") or {}
        source = metadata.get("source") or origin.get("source")
        if source not in {"cli", "vscode"}:
            continue
        if origin.get("mode") in {"automation", "subagent"} or origin.get("trigger") == "hub-schedule":
            continue
        messages = transcript.get("messages") or []
        if not isinstance(messages, list):
            continue
        for index, message in enumerate(messages):
            if not isinstance(message, dict) or message.get("role") != "user":
                continue
            text = _text_blocks(message)
            if not text:
                continue
            identifier = message.get("id")
            yield {
                "platform": "cline",
                "source_variant": source,
                "session_id": f"{source}:{session_id}",
                "message_id": (f"transcript:{identifier}" if identifier is not None
                               else f"transcript:index:{index}"),
                "timestamp": _iso_timestamp(message.get("ts")),
                "text": text,
                "source_path": str(path),
            }


def _extension_storages(user_home):
    roaming = user_home / "AppData" / "Roaming"
    yield from sorted(roaming.glob("*/User/globalStorage/saoudrizwan.claude-dev"))


def _old_extension_tasks(user_home):
    for storage in _extension_storages(user_home):
        for path in sorted((storage / "tasks").glob("*/ui_messages.json")):
            session_id = path.parent.name
            messages = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(messages, list):
                continue
            for index, message in enumerate(messages):
                if not isinstance(message, dict) or message.get("type") != "say":
                    continue
                kind = message.get("say")
                if kind != "user_feedback" and not (index == 0 and kind in {"task", "text"}):
                    continue
                text = message.get("text")
                if not isinstance(text, str) or not text.strip():
                    continue
                yield {
                    "platform": "cline",
                    "source_variant": "vscode",
                    "session_id": f"vscode:{session_id}",
                    "message_id": f"ui:{storage.parents[2].name}:{index}",
                    "timestamp": _iso_timestamp(message.get("ts")),
                    "text": text.strip(),
                    "source_path": str(path),
                }


def iter_cline_messages(user_home):
    """Yield Cline user messages; skip generated context and duplicate copies."""
    user_home = Path(user_home)
    seen = set()
    for source in (_new_sessions, _old_extension_tasks):
        for record in source(user_home):
            signature = _signature(record)
            if signature in seen:
                continue
            seen.add(signature)
            yield record
