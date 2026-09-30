"""Read user-authored Codex messages from local history and rollout files."""

from __future__ import annotations

import hashlib
import json
import os
import sqlite3
from collections import defaultdict
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator


def _connect_read_only(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)


def _is_subagent(source: str | None, thread_source: str | None, agent_path: str | None) -> bool:
    if agent_path or thread_source in {"subagent", "guardian_review"}:
        return True
    if source and source.startswith("{"):
        try:
            return "subagent" in json.loads(source)
        except (TypeError, ValueError):
            # Unknown structured sources cannot establish direct authorship.
            return True
    return False


def _thread_paths(codex_home: Path) -> tuple[dict[str, str], set[str]] | None:
    state = codex_home / "state_5.sqlite"
    if not state.is_file():
        return None
    with closing(_connect_read_only(state)) as connection:
        rows = list(connection.execute(
            "SELECT id, rollout_path, source, thread_source, agent_path FROM threads"
        ))
        roots = {
            thread_id: rollout_path or ""
            for thread_id, rollout_path, source, thread_source, agent_path in rows
            if not _is_subagent(source, thread_source, agent_path)
        }
        return roots, {row[0] for row in rows}


def _iso_from_epoch(value: int | float | None, *, milliseconds: bool) -> str | None:
    if value is None:
        return None
    seconds = value / 1000 if milliseconds else value
    return datetime.fromtimestamp(seconds, timezone.utc).isoformat(timespec="milliseconds").replace(
        "+00:00", "Z"
    )


def _text_hash(text: str) -> bytes:
    return hashlib.sha256(text.encode("utf-8")).digest()


def _message_text(item_json: str) -> tuple[str, list[str]]:
    item = json.loads(item_json)
    if not isinstance(item, dict) or item.get("type") != "userMessage":
        return "", []
    return _content_text(item, "text")


def _content_text(item: dict, text_type: str) -> tuple[str, list[str]]:
    parts = [
        part["text"]
        for part in item.get("content") or []
        if isinstance(part, dict)
        and part.get("type") == text_type
        and isinstance(part.get("text"), str)
    ]
    return "\n".join(parts), parts


def _rollout_events(
    home: Path,
    root_paths: dict[str, str],
    projected_ids: set[tuple[str, str]],
    projected_max_ordinal: dict[str, int],
    projected_offsets: dict[str, int],
) -> tuple[dict[tuple[str, str], str], list[dict]]:
    """Find exact source files and user turns past a stalled projection.

    A resumed thread can have multiple rollout files with overlapping ordinals.
    Only the current file named by the thread catalog can supply new turns.
    Its projection must lag the file, and each candidate must belong to a
    started turn beyond the last projected user turn.
    """
    files = []
    for folder in (home / "sessions", home / "archived_sessions"):
        if not folder.is_dir():
            continue
        for path in folder.rglob("*.jsonl"):
            with path.open("r", encoding="utf-8-sig") as stream:
                first = stream.readline()
            try:
                header = json.loads(first)
            except (TypeError, ValueError):
                continue
            if header.get("type") != "session_meta":
                continue
            # payload.id is the thread ID. payload.session_id can be a shared
            # parent ID for many subagent files.
            session_id = (header.get("payload") or {}).get("id")
            if session_id in root_paths:
                files.append((session_id, header.get("ordinal") or 0, path))
    files.sort(key=lambda entry: (entry[0], entry[1], str(entry[2])))

    sources: dict[tuple[str, str], str] = {}
    fallback: list[dict] = []
    for session_id, _, path in files:
        current_path = root_paths[session_id]
        try:
            # Windows may spell the same file with long and 8.3 path segments.
            current_file = bool(current_path) and os.path.samefile(path, current_path)
        except OSError:
            current_file = False
        cursor = projected_offsets.get(session_id)
        projection_lags = current_file and (cursor is None or path.stat().st_size > cursor)
        started_turns: set[str] = set()
        candidates = []
        with path.open("r", encoding="utf-8-sig") as stream:
            for line in stream:
                try:
                    record = json.loads(line)
                except ValueError:
                    # The currently written final line may be incomplete.
                    continue
                if record.get("type") != "event_msg":
                    continue
                payload = record.get("payload") or {}
                if payload.get("type") == "task_started":
                    turn_id = payload.get("turn_id")
                    if isinstance(turn_id, str):
                        started_turns.add(turn_id)
                if payload.get("type") != "item_completed":
                    continue
                item = payload.get("item") or {}
                if item.get("type") != "UserMessage":
                    continue
                message_id = item.get("id")
                if not isinstance(message_id, str):
                    continue
                key = (session_id, message_id)
                sources.setdefault(key, str(path))
                if not projection_lags or key in projected_ids:
                    continue
                ordinal = record.get("ordinal")
                if not isinstance(ordinal, int) or ordinal <= projected_max_ordinal.get(session_id, -1):
                    continue
                text, _ = _content_text(item, "text")
                if text.strip():
                    candidates.append((key, payload.get("turn_id"), record.get("timestamp"), text))
        for key, turn_id, timestamp, text in candidates:
            if turn_id not in started_turns:
                continue
            fallback.append({
                "platform": "codex",
                "source_variant": "rollout_user_event",
                "session_id": session_id,
                "message_id": key[1],
                "timestamp": timestamp,
                "text": text,
                "source_path": str(path),
            })
    return sources, fallback


def iter_codex_messages(codex_home: str | Path) -> Iterator[dict]:
    """Yield Codex user text with stable IDs, timestamps, and source paths.

    Images and other non-text attachments are omitted. Each stored user turn is
    emitted once by ``(session_id, message_id)``. Rollout fallback requires a
    known main thread and a task start past its stale history projection.
    """
    home = Path(codex_home)
    catalog = _thread_paths(home)
    root_paths, known_threads = catalog if catalog is not None else (None, set())
    history_db = home / "thread_history_1.sqlite"
    seen_ids: set[tuple[str, str]] = set()
    seen_texts: dict[str, set[bytes]] = defaultdict(set)
    projected_rows = []
    projected_max_ordinal: dict[str, int] = {}
    projected_offsets: dict[str, int] = {}

    if history_db.is_file():
        with closing(_connect_read_only(history_db)) as connection:
            rows = connection.execute(
                "SELECT thread_id, item_id, item_json, created_at_ms, rollout_ordinal "
                "FROM thread_items WHERE item_type = 'userMessage' "
                "ORDER BY created_at_ms, thread_id, rollout_ordinal"
            )
            for session_id, message_id, item_json, created_at_ms, ordinal in rows:
                if root_paths is None or session_id not in root_paths:
                    continue
                if not isinstance(session_id, str) or not isinstance(message_id, str):
                    continue
                key = (session_id, message_id)
                if key in seen_ids:
                    continue
                text, parts = _message_text(item_json)
                if not text.strip():
                    continue
                seen_ids.add(key)
                seen_texts[session_id].add(_text_hash(text))
                seen_texts[session_id].update(_text_hash(part) for part in parts)
                if isinstance(ordinal, int):
                    projected_max_ordinal[session_id] = max(
                        projected_max_ordinal.get(session_id, -1), ordinal
                    )
                projected_rows.append({
                    "platform": "codex",
                    "source_variant": "thread_history",
                    "session_id": session_id,
                    "message_id": message_id,
                    "timestamp": _iso_from_epoch(created_at_ms, milliseconds=True),
                    "text": text,
                    "source_path": (root_paths or {}).get(session_id) or str(history_db),
                })
            try:
                projected_offsets = {
                    thread_id: offset
                    for thread_id, offset in connection.execute(
                        "SELECT thread_id, next_rollout_byte_offset "
                        "FROM thread_history_projection_state"
                    )
                    if isinstance(offset, int)
                }
            except sqlite3.OperationalError:
                # Older projections may not have a cursor table.
                pass

    sources, fallback = ({}, [])
    if root_paths is not None:
        sources, fallback = _rollout_events(
            home, root_paths, seen_ids, projected_max_ordinal, projected_offsets
        )
    for row in projected_rows:
        key = (row["session_id"], row["message_id"])
        row["source_path"] = sources.get(key, row["source_path"])
        yield row
    for row in fallback:
        key = (row["session_id"], row["message_id"])
        if key in seen_ids:
            continue
        seen_ids.add(key)
        seen_texts[row["session_id"]].add(_text_hash(row["text"]))
        yield row

    prompt_history = home / "history.jsonl"
    if not prompt_history.is_file():
        return
    with prompt_history.open("r", encoding="utf-8-sig") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            row = json.loads(line)
            if not isinstance(row, dict):
                continue
            session_id, text = row.get("session_id"), row.get("text")
            if not isinstance(session_id, str) or not isinstance(text, str) or not text.strip():
                continue
            if session_id in known_threads and session_id not in root_paths:
                # Older CLI history can precede the thread catalog. Only a
                # known subagent ID is excluded; absent IDs are retained.
                continue
            digest = _text_hash(text)
            if digest in seen_texts[session_id]:
                continue
            yield {
                "platform": "codex",
                "source_variant": "history_jsonl",
                "session_id": session_id,
                "message_id": f"history:{line_number}",
                "timestamp": _iso_from_epoch(row.get("ts"), milliseconds=False),
                "text": text,
                "source_path": str(prompt_history),
            }
