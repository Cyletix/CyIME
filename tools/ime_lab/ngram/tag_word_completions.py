"""Mark the rows of the post-commit continuation table that finish the last word instead of
starting the next one, for BaseAssociationModel's whole-word mode.

The table (continuation_build.cc) mixes two kinds of events under one context: the next word
(吃饭 -> 了) and the rest of a word the user has only started (莫 -> 名其妙, 我 -> 们). The second
kind only fits when the user committed the context as a bare character. After a whole phrase was
committed (你在吃饭莫), the last character already ends a word, and 莫名其妙 has nothing to do
with what was typed.

A row (context, continuation) is marked when a suffix of the context plus a prefix of the
continuation is a word of the lexicon: 莫+名 (莫名), 我+们 (我们), 饭+店 (饭店). Particles and
next words (饭+了, 吃饭+的时候, 你好+吗) stay. Only words of two or more characters count.

Lexicon: jieba 0.42.1 dict.txt (MIT, https://github.com/fxsjy/jieba), read as plain data; it is
word level, unlike the phrase-rich IME dictionaries (雾凇 lists 吃饭了, which would hide 了).

Usage: python tag_word_completions.py TABLE.bin LEXICON_dict.txt OUT.gz
Output (gzip, fixed mtime so rebuilding gives the same bytes): header
"# cyime-base-word-completions-v1 <sha256 of TABLE.bin>", then one line per context with marked
rows, "context\\tMASK": MASK is hexadecimal, bit i set = the i-th row of that context in the
table file (file order, before any sorting) is marked. The table has at most 16 rows per context.
"""
import gzip
import hashlib
import io
import sys
from pathlib import Path

HEADER = "# cyime-base-word-completions-v1"


def read_lexicon(path):
    words = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            fields = line.split()
            if fields and len(fields[0]) >= 2:
                words.add(fields[0])
    return words


def completes_word(context, continuation, words):
    return any(context[-k:] + continuation[:j] in words
               for k in range(1, len(context) + 1) for j in range(1, len(continuation) + 1))


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    table, lexicon, out = sys.argv[1:]
    words = read_lexicon(lexicon)
    masks = {}
    seen = {}
    rows = total = 0
    with gzip.open(table, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            context, continuation, _, _ = line.rstrip("\n").split("\t")
            index = seen.get(context, 0)
            seen[context] = index + 1
            rows += 1
            if completes_word(context, continuation, words):
                masks[context] = masks.get(context, 0) | (1 << index)
                total += 1
    digest = hashlib.sha256(Path(table).read_bytes()).hexdigest()
    text = io.StringIO()
    text.write(f"{HEADER} {digest}\n")
    for context, mask in masks.items():
        text.write(f"{context}\t{mask:x}\n")
    with open(out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write(text.getvalue().encode("utf-8"))
    print(f"{Path(out).name}: {total} of {rows} rows marked in {len(masks)} contexts")


if __name__ == "__main__":
    main()
