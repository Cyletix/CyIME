"""Read only: inventory/hash a user-supplied ZIP backup, without extracting private messages."""
import argparse, hashlib, json, zipfile
from pathlib import Path

def inspect(path):
    p = Path(path)
    digest = hashlib.sha256()
    with p.open("rb") as f:
        for block in iter(lambda: f.read(1024*1024), b""): digest.update(block)
    result = {"bytes":p.stat().st_size, "sha256":digest.hexdigest(), "entries":[]}
    with zipfile.ZipFile(p) as z:
        for info in z.infolist():
            entry = {"name":info.filename, "bytes":info.file_size, "zip_encrypted":bool(info.flag_bits&1)}
            if info.filename.startswith("db/") and not entry["zip_encrypted"]:
                with z.open(info) as f: header = f.read(32)
                entry["header_hex"] = header.hex()
                entry["standard_sqlite_header"] = header.startswith(b"SQLite format 3\x00")
            result["entries"].append(entry)
    result["corpus_ready"] = False
    result["next_step"] = "Need a verified message decoder and sender-id mapping; inventory is not chat text."
    return result

if __name__ == "__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("backup"); p.add_argument("output")
    a=p.parse_args()
    with Path(a.output).open("x",encoding="utf-8") as f:
        json.dump(inspect(a.backup),f,ensure_ascii=False,indent=2)
