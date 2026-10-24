"""Collect the messages the user wrote in a Claude Projects project from local Claude Code transcripts.

A Projects thread session receives each human message in a ``<wake>`` envelope
(``<message from="human" id="cmsg_..." sent-at=...>``), and the coordinator relays project-chat
messages as ``<cited id="cmsg_..." author="user" at=... where=...>``. Both carry the project's own
message id, so the same message seen by several sessions is kept once. Escaped tags (quoted by a
session) are not matched. Nothing here prints message text; the output belongs outside the repository.

  project_messages.py --project chan_... --output D:/CyIME-Data/private/project-messages/messages.jsonl
                      [--home ~] [--author-id user_...] [--name cyletix] [--merge FILE ...]

--merge reads extra JSONL rows of the same shape (id, sent_at, text, ...), e.g. messages fetched
through the project API for threads that never ran on this computer; local rows win only when they
are longer and not truncated.
"""
import argparse
import html
import json
import re
from pathlib import Path

WAKE_THREAD = re.compile(r'<thread (?:id|ts)="(cmsg_[A-Za-z0-9]+)"')
HUMAN = re.compile(r'<message\b(?=[^>]*\bfrom="human")([^>]*)>(.*?)</message>', re.DOTALL)
CITED = re.compile(r'<cited\b(?=[^>]*\bauthor="user")([^>]*)>(.*?)</cited>', re.DOTALL)
ATTR = re.compile(r'([a-z-]+)="([^"]*)"')
API_LIST = re.compile(r'\{"messages":\[')
CHILD = re.compile(r"<(attachment|previous-body)\b.*?(?:/>|</\1>)", re.DOTALL)


def _texts(content):
    if isinstance(content, str):
        return [content]
    out = []
    for block in content if isinstance(content, list) else []:
        if isinstance(block, dict) and isinstance(block.get("text"), str):
            out.append(block["text"])
        elif isinstance(block, dict) and block.get("type") == "tool_result":
            out.extend(_texts(block.get("content")))  # project API listings read by any session
    return out


def _body(raw):
    text = CHILD.sub("", raw)
    return "\n".join(line.strip() for line in html.unescape(text).strip().splitlines()).strip()


def rows_from_text(text, author_id, name, source, prefix):
    thread = WAKE_THREAD.search(text)
    for match in HUMAN.finditer(text):
        attrs = dict(ATTR.findall(match.group(1)))
        if author_id and attrs.get("author-id") not in (None, author_id):
            continue
        if attrs.get("id", "").startswith(prefix):
            yield {"id": attrs["id"], "sent_at": attrs.get("sent-at"), "thread": thread.group(1) if thread else None,
                   "where": "thread", "edited": attrs.get("edited") == "true", "truncated": False,
                   "text": _body(match.group(2)), "source": source}
    for match in CITED.finditer(text):
        attrs = dict(ATTR.findall(match.group(1)))
        if name and attrs.get("name") not in (None, name):
            continue
        if attrs.get("id", "").startswith(prefix):
            yield {"id": attrs["id"], "sent_at": attrs.get("at"), "thread": None,
                   "where": attrs.get("where", "relay"), "edited": False,
                   "truncated": attrs.get("truncated") == "true", "text": _body(match.group(2)), "source": source}


def rows_from_api(text, author_id, prefix, source):
    """Messages in a fetch_project_timeline / fetch_thread / fetch_messages result."""
    decoder = json.JSONDecoder()
    for start in (m.start() for m in API_LIST.finditer(text)):
        try:
            listing, _ = decoder.raw_decode(text, start)
        except ValueError:
            continue
        for m in listing.get("messages", []):
            if not isinstance(m, dict) or m.get("author") != "user" or not str(m.get("id", "")).startswith(prefix):
                continue
            if author_id and m.get("author_id") not in (None, author_id):
                continue
            body = m.get("body") or ""
            root = m.get("thread_id")
            yield {"id": m["id"], "sent_at": m.get("created_at"), "thread": root,
                   "where": "timeline" if root in (None, m["id"]) else "thread",
                   "edited": bool(m.get("edited_at")), "truncated": "bytes truncated" in body,
                   "text": body.strip(), "source": source}


def better(old, new):
    if old is None:
        return True
    if old["truncated"] != new["truncated"]:
        return old["truncated"]
    if new["edited"] and not old["edited"]:
        return True  # the edited body is what the user finally wrote
    return len(new["text"]) > len(old["text"]) and not old["edited"]


def collect(home, author_id, name, project):
    # A project's message ids share its channel id: chan_X -> cmsg_X...
    prefix = "cmsg_" + project.removeprefix("chan_")
    found = {}
    for path in sorted((Path(home) / ".claude" / "projects").glob("*/*.jsonl")):
        with path.open(encoding="utf-8", errors="replace") as transcript:
            for line in transcript:
                if '"user"' not in line or "cmsg_" not in line:
                    continue
                try:
                    record = json.loads(line)
                except ValueError:
                    continue
                message = record.get("message") if isinstance(record, dict) else None
                if record.get("type") != "user" or not isinstance(message, dict):
                    continue
                for text in _texts(message.get("content")):
                    rows = list(rows_from_text(text, author_id, name, path.parent.name, prefix))
                    rows += rows_from_api(text, author_id, prefix, path.parent.name + ":api")
                    for row in rows:
                        old = found.get(row["id"])
                        if old and not row["thread"]:
                            row["thread"] = old["thread"]
                        if better(old, row):
                            found[row["id"]] = row
                        elif old and row["thread"] and not old["thread"]:
                            old["thread"] = row["thread"]
    # Listings too large to show inline are saved next to the transcript instead.
    for path in sorted((Path(home) / ".claude" / "projects").glob("*/*/tool-results/*.txt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for row in rows_from_api(text, author_id, prefix, path.parts[-4] + ":api"):
            if better(found.get(row["id"]), row):
                found[row["id"]] = row
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--home", default=str(Path.home()))
    parser.add_argument("--author-id", default="", help="keep only this account (default: every human)")
    parser.add_argument("--name", default="", help="keep only cited messages under this display name")
    parser.add_argument("--project", required=True, help="the project channel id, chan_...")
    parser.add_argument("--merge", nargs="*", default=[], type=Path)
    args = parser.parse_args()
    found = collect(args.home, args.author_id, args.name, args.project)
    for path in args.merge:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                row = {"thread": None, "where": "api", "edited": False, "truncated": False, "source": "api",
                       **json.loads(line)}
                if better(found.get(row["id"]), row):
                    found[row["id"]] = row
    rows = sorted(found.values(), key=lambda r: (r["sent_at"] or "", r["id"]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    threads = {r["thread"] for r in rows if r["thread"]}
    print(json.dumps({"messages": len(rows), "threads": len(threads),
                      "truncated": sum(r["truncated"] for r in rows),
                      "first": rows[0]["sent_at"] if rows else None, "last": rows[-1]["sent_at"] if rows else None}))


if __name__ == "__main__":
    main()
