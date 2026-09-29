"""Lexical audit inventory, not a bug count. Excludes comments and quoted strings.
Usage: python tools/ime_lab/audit_contract_patterns.py [--ref 1.2.8]
"""
import argparse
import collections
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--ref")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
if args.ref:
    paths = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", args.ref, "app/src/main/java"], cwd=root, text=True).splitlines()
    read = lambda path: subprocess.check_output(["git", "show", f"{args.ref}:{path}"], cwd=root).decode("utf-8-sig")
else:
    paths = [p.relative_to(root).as_posix() for p in (root / "app/src/main/java").rglob("*.kt")]
    read = lambda path: (root / path).read_text(encoding="utf-8-sig")
quoted = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"', re.S)
patterns = {
    "tryLocked": r"\btryLocked(?:<[^>]*>)?\s*\(",
    "tryMutating": r"\btryMutating(?:<[^>]*>)?\s*\(",
    "runCatching": r"\brunCatching\s*\{",
    "catch_Throwable": r"catch\s*\([^:]+:\s*Throwable\b",
    "catch_Exception": r"catch\s*\([^:]+:\s*Exception\b",
    "getOrDefault": r"\.getOrDefault\s*\(",
    "non_null_assertion": r"!!",
    "runBlocking": r"\brunBlocking(?:\s*\([^)]*\))?\s*\{",
    "Thread.sleep": r"Thread\.sleep\s*\(",
}
counts = collections.Counter()
locations = {key: [] for key in patterns}
for path in sorted(paths):
    if not path.endswith(".kt"): continue
    code = quoted.sub(lambda m: "\n" * m[0].count("\n"), read(path))
    # Remove function declarations so helper definitions are not counted as calls.
    code = re.sub(r"fun\s*(?:<[^>]*>)?\s+\w+\s*\(", "(", code)
    for name, pattern in patterns.items():
        for match in re.finditer(pattern, code):
            counts[name] += 1
            locations[name].append({"file": path, "line": code.count("\n", 0, match.start()) + 1})
print(json.dumps({"ref": args.ref or "working-tree", "scope": "app/src/main/java/**/*.kt; lexical counts, not defects", "counts": dict(counts), "locations": locations}, ensure_ascii=False, indent=2))
