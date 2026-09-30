"""Build a private SQLite learning corpus from local Codex and Cline chats.

Only human-authored user messages are accepted by the source-specific parsers.
Raw text stays in SQLite; the JSONL export contains cleaned learning text.
"""

import argparse
import csv
import hashlib
import json
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from chat_corpus_cline import iter_cline_messages
from chat_corpus_codex import iter_codex_messages
from chat_corpus_terms import clean_learning_text, count_words


SCHEMA = """
PRAGMA foreign_keys = ON;
PRAGMA user_version = 1;
CREATE TABLE messages (
    id INTEGER PRIMARY KEY,
    platform TEXT NOT NULL CHECK (platform IN ('codex', 'cline')),
    source_variant TEXT NOT NULL,
    session_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    timestamp TEXT,
    raw_text TEXT NOT NULL,
    learning_text TEXT NOT NULL,
    raw_sha256 TEXT NOT NULL,
    source_path TEXT NOT NULL,
    UNIQUE (platform, session_id, message_id)
);
CREATE INDEX messages_platform_time ON messages (platform, timestamp);
CREATE VIEW codex_user_messages AS SELECT * FROM messages WHERE platform = 'codex';
CREATE VIEW cline_user_messages AS SELECT * FROM messages WHERE platform = 'cline';
CREATE TABLE message_terms (
    message_id INTEGER NOT NULL REFERENCES messages(id),
    kind TEXT NOT NULL CHECK (kind IN ('han', 'latin')),
    term TEXT NOT NULL,
    occurrences INTEGER NOT NULL CHECK (occurrences > 0),
    PRIMARY KEY (message_id, kind, term)
);
CREATE TABLE word_frequency (
    platform TEXT NOT NULL CHECK (platform IN ('all', 'codex', 'cline')),
    kind TEXT NOT NULL CHECK (kind IN ('han', 'latin')),
    term TEXT NOT NULL,
    occurrences INTEGER NOT NULL,
    message_count INTEGER NOT NULL,
    PRIMARY KEY (platform, kind, term)
);
CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""


def check_output_location(path):
    """Keep private chat data outside the checkout and OneDrive."""
    resolved = path.resolve()
    repo = Path(__file__).resolve().parents[2]
    if resolved == repo or repo in resolved.parents:
        raise ValueError("private corpus output must be outside the repository")
    if any(part.casefold().startswith("onedrive") for part in resolved.parts):
        raise ValueError("private corpus output must be outside OneDrive")


def checked_message(record):
    required = ("platform", "source_variant", "session_id", "message_id", "text", "source_path")
    if any(not isinstance(record.get(key), str) or not record[key] for key in required):
        raise ValueError("extractor returned an incomplete message")
    if record["platform"] not in {"codex", "cline"}:
        raise ValueError("extractor returned an unknown platform")
    timestamp = record.get("timestamp")
    if timestamp is not None and not isinstance(timestamp, str):
        raise ValueError("extractor returned a non-text timestamp")
    return record


def insert_messages(connection, records, stats):
    for item in records:
        record = checked_message(item)
        raw = record["text"]
        if not raw.strip():
            stats["empty_records"] += 1
            continue
        learning = clean_learning_text(raw).strip()
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        cursor = connection.execute(
            """INSERT INTO messages
               (platform, source_variant, session_id, message_id, timestamp,
                raw_text, learning_text, raw_sha256, source_path)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(platform, session_id, message_id) DO NOTHING""",
            (record["platform"], record["source_variant"], record["session_id"],
             record["message_id"], record.get("timestamp"), raw, learning,
             digest, record["source_path"]),
        )
        if cursor.rowcount == 0:
            stats["duplicate_records"] += 1
            continue
        stats[record["platform"] + "_messages"] += 1
        words = count_words(learning)
        if not words:
            stats["messages_without_terms"] += 1
        connection.executemany(
            "INSERT INTO message_terms (message_id, kind, term, occurrences) VALUES (?, ?, ?, ?)",
            ((cursor.lastrowid, kind, term, count)
             for (kind, term), count in words.items()),
        )


def aggregate_frequencies(connection):
    connection.execute(
        """INSERT INTO word_frequency
           SELECT m.platform, t.kind, t.term, SUM(t.occurrences), COUNT(*)
           FROM message_terms AS t JOIN messages AS m ON m.id = t.message_id
           GROUP BY m.platform, t.kind, t.term"""
    )
    connection.execute(
        """INSERT INTO word_frequency
           SELECT 'all', kind, term, SUM(occurrences), SUM(message_count)
           FROM word_frequency WHERE platform IN ('codex', 'cline')
           GROUP BY kind, term"""
    )


def export_learning_jsonl(connection, path):
    with path.open("x", encoding="utf-8", newline="\n") as output:
        rows = connection.execute(
            """SELECT platform, session_id, message_id, timestamp, learning_text
               FROM messages WHERE learning_text != '' ORDER BY platform, id"""
        )
        for platform, session_id, message_id, timestamp, learning_text in rows:
            output.write(json.dumps({"platform": platform, "session_id": session_id,
                                     "message_id": message_id, "timestamp": timestamp,
                                     "text": learning_text}, ensure_ascii=False) + "\n")


def export_word_frequency(connection, path, minimum):
    with path.open("x", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t", lineterminator="\n")
        writer.writerow(("platform", "kind", "term", "occurrences", "message_count"))
        writer.writerows(connection.execute(
            """SELECT platform, kind, term, occurrences, message_count
               FROM word_frequency WHERE occurrences >= ?
               ORDER BY CASE platform WHEN 'all' THEN 0 WHEN 'codex' THEN 1 ELSE 2 END,
                        occurrences DESC, term""", (minimum,)
        ))


def build_corpus(user_home, output, minimum=2, force=False):
    user_home = Path(user_home).resolve()
    output = Path(output).resolve()
    check_output_location(output)
    if not user_home.is_dir():
        raise FileNotFoundError(f"user home not found: {user_home}")
    if minimum < 1:
        raise ValueError("minimum export count must be positive")
    frequency_path = output.with_name(output.stem + ".word_frequency.tsv")
    learning_path = output.with_name(output.stem + ".learning.jsonl")
    targets = (output, frequency_path, learning_path)
    if not force and any(path.exists() for path in targets):
        raise FileExistsError("corpus output exists; choose another path or pass --force")
    output.parent.mkdir(parents=True, exist_ok=True)
    temp_paths = tuple(path.with_name(f".{path.name}.{os.getpid()}.tmp") for path in targets)
    if any(path.exists() for path in temp_paths):
        raise FileExistsError("temporary corpus output already exists")

    stats = {"codex_messages": 0, "cline_messages": 0,
             "duplicate_records": 0, "empty_records": 0,
             "messages_without_terms": 0}
    connection = None
    try:
        connection = sqlite3.connect(temp_paths[0])
        connection.executescript(SCHEMA)
        with connection:
            insert_messages(connection, iter_codex_messages(user_home / ".codex"), stats)
            insert_messages(connection, iter_cline_messages(user_home), stats)
            if stats["codex_messages"] == 0 or stats["cline_messages"] == 0:
                raise ValueError("both Codex and Cline human user messages are required")
            aggregate_frequencies(connection)
            meta = {
                "generated_at_utc": datetime.now(timezone.utc).isoformat(),
                "user_home": str(user_home),
                "tokenizer": "jieba",
                "export_minimum_occurrences": str(minimum),
                **{key: str(value) for key, value in stats.items()},
            }
            connection.executemany("INSERT INTO metadata VALUES (?, ?)", meta.items())
        if connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("SQLite integrity check failed")
        export_word_frequency(connection, temp_paths[1], minimum)
        export_learning_jsonl(connection, temp_paths[2])
        connection.close()
        connection = None
        for temporary, target in zip(temp_paths, targets):
            os.replace(temporary, target)
    except BaseException:
        if connection is not None:
            connection.close()
        for temporary in temp_paths:
            temporary.unlink(missing_ok=True)
        raise
    return {"sqlite": output, "word_frequency": frequency_path,
            "learning_jsonl": learning_path, **stats}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--user-home", type=Path, default=Path.home(),
                        help="Windows user profile containing .codex and .cline")
    parser.add_argument("--output", type=Path, required=True,
                        help="SQLite output outside the repository and OneDrive")
    parser.add_argument("--min-export-count", type=int, default=2)
    parser.add_argument("--force", action="store_true", help="replace existing outputs")
    args = parser.parse_args()
    result = build_corpus(args.user_home, args.output, args.min_export_count, args.force)
    print(json.dumps({key: str(value) for key, value in result.items()}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
