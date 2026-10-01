"""Private, local-only corpus review and CyIME personal-learning conversion."""
import argparse
from contextlib import closing
from collections import Counter, defaultdict
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import sqlite3
from urllib.parse import parse_qs, urlparse

import jieba
from chat_corpus import check_output_location
from chat_corpus_terms import clean_learning_text

HAN = re.compile(r"[\u3400-\u9fff]+")
SKIP = {".git", ".obsidian", ".trash", ".claudian", ".agents", ".github", ".vscode", "@assets", "tmp", "node_modules"}


def clean_note(text):
    text = re.sub(r"\A---\s*\n.*?\n---\s*(?:\n|$)", "", text, flags=re.S)
    text = re.sub(r"%%.*?%%|<!--.*?-->", " ", text, flags=re.S)
    text = re.sub(r"!\[\[.*?\]\]|!\[[^\]]*\]\([^)]*\)", " ", text)
    text = re.sub(r"\[\[([^\]|]+)\|([^\]]+)\]\]", r"\2", text)
    text = re.sub(r"\[\[([^\]]+)\]\]", r"\1", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = clean_learning_text(text)
    text = re.sub(r"`[^`\n]*`", " ", text)
    return clean_learning_text(text)


def continuations(text):
    """Whole next words, within a single prose run. Never join documents or redactions."""
    rows = Counter()
    for match in HAN.finditer(text):
        words = list(jieba.cut(match.group(), cut_all=False))
        previous = ""
        for word in words:
            if previous and 1 <= len(word) <= 12:
                # Specific context first, with a word-sized fallback for generalization.
                for prefix in {previous[-4:], previous[-2:]}:
                    rows[prefix, word] += 1
            previous += word
    return rows


def build(output, phone, chats, notebook, minimum=3):
    output = Path(output).resolve()
    check_output_location(output)
    if output.exists():
        raise FileExistsError("Review database already exists; choose a new output to preserve edits")
    output.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(output)
    db.executescript("""
      CREATE TABLE entries(id INTEGER PRIMARY KEY, kind TEXT NOT NULL, prefix TEXT NOT NULL,
        text TEXT NOT NULL, count INTEGER NOT NULL, original_count INTEGER NOT NULL,
        sources TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1);
      CREATE INDEX entry_order ON entries(count DESC);
      CREATE TABLE documents(source TEXT, path TEXT, hash TEXT UNIQUE, characters INTEGER);
      CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT);
    """)
    observed = json.loads(Path(phone).read_text(encoding="utf-8"))
    for kind in ("bigrams", "trigrams"):
        for row in observed[kind]:
            db.execute("INSERT INTO entries(kind,prefix,text,count,original_count,sources) VALUES(?,?,?,?,?,?)",
                       (kind, "".join(row["tokens"][:-1]), row["tokens"][-1], row["count"], row["count"], '{"phone":1}'))
    groups = defaultdict(Counter)
    document_support = Counter()
    seen = set()
    stats = Counter()

    def ingest(source, path, text):
        cleaned = clean_note(text).strip()
        digest = hashlib.sha256(cleaned.encode()).hexdigest()
        if not cleaned or digest in seen:
            stats["duplicate_or_empty"] += 1
            return
        seen.add(digest)
        rows = continuations(cleaned)
        # A long pasted transcript/template must not outweigh hundreds of genuine turns.
        groups[source].update({key: min(count, 3) for key, count in rows.items()})
        document_support.update(rows.keys())
        stats[source + "_documents"] += 1
        stats[source + "_characters"] += len(cleaned)
        db.execute("INSERT INTO documents VALUES(?,?,?,?)", (source, str(path), digest, len(cleaned)))

    with Path(chats).open(encoding="utf-8") as stream:
        for index, line in enumerate(stream):
            record = json.loads(line)
            source = record.get("platform", "chat")
            ingest(source, f"{Path(chats).name}:{index+1}", record["text"])
    for path in sorted(Path(notebook).rglob("*.md")):
        relative = path.relative_to(notebook)
        if any(part in SKIP or part.startswith('.') for part in relative.parts):
            continue
        # A notebook can contain external linked trees; keep this import in the named vault.
        if not path.resolve().is_relative_to(Path(notebook).resolve()):
            continue
        if path.stat().st_size > 4 * 1024 * 1024:
            stats["oversized_notes_skipped"] += 1
            continue
        try:
            text = path.read_text(encoding="utf-8-sig")
        except UnicodeError:
            stats["non_utf8_notes_skipped"] += 1
            continue
        ingest("notebook", relative, text)
    total = Counter()
    for rows in groups.values():
        total.update(rows)
    per_prefix = Counter()
    kept = 0
    for (prefix, word), count in total.most_common():
        if count < minimum or document_support[prefix, word] < 2 or per_prefix[prefix] >= 12 or kept >= 100_000:
            continue
        sources = {source: rows[prefix, word] for source, rows in groups.items() if rows[prefix, word]}
        db.execute("INSERT INTO entries(kind,prefix,text,count,original_count,sources) VALUES('profile',?,?,?,?,?)",
                   (prefix, word, min(count, 1_000_000), min(count, 1_000_000), json.dumps(sources)))
        per_prefix[prefix] += 1
        kept += 1
    stats.update(profile_entries=kept, raw_distinct_continuations=len(total), phone_combinations=sum(len(observed[k]) for k in ('bigrams','trigrams')),
                 phone_observations=sum(r['count'] for k in ('bigrams','trigrams') for r in observed[k]))
    db.executemany("INSERT INTO metadata VALUES(?,?)", [("stats", json.dumps(stats)), ("phone_source", str(phone)),
        ("profile_name", "个人语料方案"), ("recent_inputs", json.dumps(observed.get("recentInputs", []))),
        ("minimum_count", str(minimum)), ("notebook", str(notebook))])
    db.commit()
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    export = output.with_suffix(".learning.json")
    export.write_text(json.dumps(export_data(db), ensure_ascii=False), encoding="utf-8")
    db.close()
    return {**stats, "database": str(output), "learning_file": str(export)}


def export_data(db):
    meta = dict(db.execute("SELECT key,value FROM metadata"))
    data = {"format": "cyime-personal-learning", "version": 1, "bigrams": [], "trigrams": [],
            "recentInputs": json.loads(meta.get("recent_inputs", "[]")), "profile": {"name": meta.get("profile_name", "个人语料方案"), "entries": []}}
    for kind, prefix, text, count in db.execute("SELECT kind,prefix,text,count FROM entries WHERE enabled=1 ORDER BY id"):
        if kind == "profile":
            data["profile"]["entries"].append({"context": prefix, "text": text, "count": count})
        else:
            data[kind].append({"tokens": list(prefix) + [text], "count": count})
    return data


def serve(database, port):
    database = Path(database).resolve()
    check_output_location(database)
    if not database.is_file(): raise FileNotFoundError(database)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args): pass  # Query strings may contain private search terms.

        def reply(self, value, mime="application/json; charset=utf-8", status=200, download=False):
            body = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'")
            if download: self.send_header("Content-Disposition", 'attachment; filename="CyIME-personal-learning.json"')
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.headers.get('Host') != f'127.0.0.1:{port}':
                return self.reply({'error':'Host rejected'},status=403)
            route = urlparse(self.path)
            if route.path in ("/", "/workbench.js", "/workbench.css"):
                name = {"/": "workbench.html", "/workbench.js": "workbench.js", "/workbench.css": "workbench.css"}[route.path]
                mime = {".html": "text/html", ".js": "text/javascript", ".css": "text/css"}[Path(name).suffix]
                return self.reply((Path(__file__).parent / name).read_bytes(), mime + "; charset=utf-8")
            query = parse_qs(route.query)
            with closing(sqlite3.connect(database)) as db:
                if route.path == "/api/stats":
                    stats = json.loads(db.execute("SELECT value FROM metadata WHERE key='stats'").fetchone()[0])
                    stats['enabled_entries'] = db.execute("SELECT COUNT(*) FROM entries WHERE enabled=1").fetchone()[0]
                    return self.reply(stats)
                if route.path == "/api/export": return self.reply(export_data(db), download=True)
                if route.path == "/api/rows":
                    search = query.get('q', [''])[0]
                    kind = query.get('kind', [''])[0]
                    source = query.get('source', [''])[0]
                    where, args = ['(prefix LIKE ? OR text LIKE ?)'], ['%'+search+'%', '%'+search+'%']
                    if kind: where += ['kind=?']; args += [kind]
                    if source in ('phone', 'codex', 'cline', 'notebook'):
                        where += ["json_extract(sources, ?) IS NOT NULL"]; args += ['$.'+source]
                    clause = ' AND '.join(where)
                    try: offset = max(0, int(query.get('offset', ['0'])[0]))
                    except ValueError: return self.reply({'error':'Invalid offset'},status=400)
                    db.row_factory = sqlite3.Row
                    rows = [dict(row) for row in db.execute('SELECT * FROM entries WHERE '+clause+' ORDER BY count DESC,id LIMIT 100 OFFSET ?', args+[offset])]
                    total = db.execute('SELECT COUNT(*) FROM entries WHERE '+clause, args).fetchone()[0]
                    return self.reply({'rows':rows,'total':total})
            self.reply({'error':'Not found'},status=404)

        def do_POST(self):
            # No cross-origin writes, even from another website on the same computer.
            if self.headers.get('Host') != f'127.0.0.1:{port}' or self.headers.get('Origin') not in (None, f'http://127.0.0.1:{port}') or self.headers.get('Content-Type') != 'application/json':
                return self.reply({'error':'Origin rejected'},status=403)
            if self.path != '/api/update': return self.reply({'error':'Not found'},status=404)
            try:
                size = int(self.headers.get('Content-Length','0'))
                if not 1 <= size <= 4096: raise ValueError('Invalid request size')
                value = json.loads(self.rfile.read(size))
                if type(value['count']) is not int or not 1 <= value['count'] <= 1_000_000 or type(value['enabled']) is not bool:
                    raise ValueError('次数必须是 1～1000000 的整数')
                with closing(sqlite3.connect(database)) as db, db:
                    cursor = db.execute('UPDATE entries SET count=?,enabled=? WHERE id=?',(value['count'],int(value['enabled']),value['id']))
                    if cursor.rowcount != 1: raise ValueError('条目不存在')
                self.reply({'ok':True})
            except (ValueError, KeyError, TypeError) as error: self.reply({'error':str(error)},status=400)

    print(f'http://127.0.0.1:{port}/', flush=True)
    ThreadingHTTPServer(('127.0.0.1',port),Handler).serve_forever()


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    b=sub.add_parser('build')
    for name in ('output','phone','chats','notebook'): b.add_argument('--'+name, required=True, type=Path)
    b.add_argument('--minimum',type=int,default=3)
    s=sub.add_parser('serve'); s.add_argument('--database',type=Path,required=True); s.add_argument('--port',type=int,default=4321)
    args=vars(parser.parse_args()); command=args.pop('command')
    if command=='build': print(json.dumps(build(**args),ensure_ascii=False,indent=2))
    else: serve(**args)
