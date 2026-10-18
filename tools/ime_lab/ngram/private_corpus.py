"""Turn the private chat corpus (chat_corpus.py learning JSONL) into n-gram training text.

Input rows are the person's own cleaned messages. Output is the same shape as the
other corpora of ngram_build: Han runs, one per line. Sessions are split, not
messages, so the held-out part never shares a conversation with the training part.
Only counts are printed; no message text ever reaches stdout.

Usage: private_corpus.py learning.jsonl OUT_DIR [--heldout 0.1] [--seed 20261013]
Writes OUT_DIR/personal-train.txt, personal-heldout.txt, personal-stats.json.
OUT_DIR must be outside the repository and OneDrive (same rule as chat_corpus.py).
"""
import argparse
import collections
import hashlib
import json
import re
from pathlib import Path

HAN_RUN = re.compile(r"[㐀-䶿一-鿿〇]+")


def check_output_location(path):
    """Same rule as chat_corpus.py (not imported: that module needs jieba)."""
    resolved = path.resolve()
    repo = Path(__file__).resolve().parents[3]
    if resolved == repo or repo in resolved.parents:
        raise ValueError("private corpus output must be outside the repository")
    if any(part.casefold().startswith("onedrive") for part in resolved.parts):
        raise ValueError("private corpus output must be outside OneDrive")


def runs_of(text, min_chars, max_chars):
    for run in HAN_RUN.findall(text):
        for k in range(0, len(run), max_chars):
            piece = run[k:k + max_chars]
            if len(piece) >= min_chars:
                yield piece


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("learning_jsonl", type=Path)
    parser.add_argument("out_dir", type=Path)
    parser.add_argument("--heldout", type=float, default=0.1, help="share of sessions held out")
    parser.add_argument("--seed", default="20261013", help="salt of the session split hash")
    parser.add_argument("--min-chars", type=int, default=2)
    parser.add_argument("--max-chars", type=int, default=60)
    parser.add_argument("--platforms", default="codex,cline,claude")
    args = parser.parse_args()
    out = args.out_dir.resolve()
    check_output_location(out)
    out.mkdir(parents=True, exist_ok=True)
    platforms = set(args.platforms.split(","))

    stats = {"messages": collections.Counter(), "runs": collections.Counter(),
             "chars": collections.Counter(), "sessions": collections.Counter()}
    seen = set()
    duplicates = 0
    sessions = {}
    files = {"train": (out / "personal-train.txt").open("w", encoding="utf-8", newline="\n"),
             "heldout": (out / "personal-heldout.txt").open("w", encoding="utf-8", newline="\n")}
    try:
        with args.learning_jsonl.open(encoding="utf-8") as rows:
            for line in rows:
                row = json.loads(line)
                if row.get("platform") not in platforms:
                    continue
                key = (row["platform"], row["session_id"])
                if key not in sessions:
                    digest = hashlib.sha256((args.seed + ":" + ":".join(key)).encode("utf-8")).digest()
                    sessions[key] = "heldout" if digest[0] / 256 < args.heldout else "train"
                part = sessions[key]
                stats["messages"][part] += 1
                stats["messages"][row["platform"]] += 1
                for run in runs_of(row.get("text", ""), args.min_chars, args.max_chars):
                    if run in seen:
                        duplicates += 1
                        continue
                    seen.add(run)
                    files[part].write(run + "\n")
                    stats["runs"][part] += 1
                    stats["runs"][row["platform"]] += 1
                    stats["chars"][part] += len(run)
                    stats["chars"][row["platform"]] += len(run)
    finally:
        for handle in files.values():
            handle.close()
    for key, part in sessions.items():
        stats["sessions"][part] += 1
        stats["sessions"][key[0]] += 1
    summary = {"source": str(args.learning_jsonl), "heldout_share": args.heldout, "seed": args.seed,
               "duplicate_runs_dropped": duplicates,
               **{name: dict(counter) for name, counter in stats.items()}}
    (out / "personal-stats.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
