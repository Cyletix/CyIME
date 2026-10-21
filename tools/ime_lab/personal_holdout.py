"""Time-split hold-out test set from the user's own typed messages (chat_corpus.py output).

Only messages written after --after are used, so nothing in the set was seen by a model or weight table
built from a corpus snapshot taken at that time (the 2026-10-13 personal snapshot fed the personal word
model). Every distinct item keeps its occurrence count, so scores can be read per item or by how often
the user actually writes it (usage-frequency sampling without drawing a sample).

Groups written in the public-set format that lexicon_regression.py (--public-set) and CyIME-Windows'
tools/rime-eval read:

  sentence.jsonl  clause      2-12 Han characters between punctuation, typed as full pinyin
                  word        2-4 character words (jieba), typed as full pinyin
                  typo-word / typo-clause
                              the same items with one letter of a doubled n / ng+g syllable boundary typed
                              once (cheng'gong -> chengong); "keys" holds what is typed
  jianpin.jsonl   initials    the words typed as first letters (zh/ch/sh as z/c/s)

Pasted output, harness envelopes, file listings and long messages are skipped. Items equal to a fixed
regression case (the reports fixes were tuned on) are left out, and the words the typo rule was developed on
get no typo item; both are counted.
Nothing here prints message text; the output belongs outside the repository.

  personal_holdout.py --corpus chat_corpus.sqlite3 --after 2026-10-13T03:15:00Z --output DIR
                      [--exclude FILE ...] [--max-chars 200] [--learn-from TEXT --learn-top N]

--learn-from writes learn.tsv (full pinyin, word, occurrences): the N most frequent 2-4 character words of a
text written before --after (e.g. the 2026-10-13 personal training split, one Han run per line), for
lexicon_regression.py --learn, so the same items can be typed with the user's own words learned.
Needs jieba and pypinyin.
"""
import argparse
import json
import re
import sqlite3
from collections import Counter
from pathlib import Path

import jieba
from pypinyin import Style, lazy_pinyin

REPO = Path(__file__).resolve().parents[2]
HAN = re.compile(r"[\u4e00-\u9fff]+")
# Signs of text the user did not type: markup, harness envelopes, file listings, tables.
NOT_TYPED = re.compile(r"<[a-z_/]|\*\*|```|^#|Files mentioned|questionItemId|\|\s*-{3}|^\s*\d+\.\s", re.M)
# The line Claude Code adds for an attached image; the user's words around it are kept.
IMAGE_NOTE = re.compile(r"^\[Image[^\n]*\]\s*$", re.M)


def typed_messages(corpus, after, max_chars):
    rows = sqlite3.connect(corpus).execute(
        "select learning_text from messages where timestamp > ? order by timestamp", (after,)).fetchall()
    kept, skipped = [], Counter()
    for (text,) in rows:
        text = IMAGE_NOTE.sub("", text).strip()
        if len(text) > max_chars:
            skipped["long"] += 1
        elif NOT_TYPED.search(text):
            skipped["markup"] += 1
        elif not HAN.search(text):
            skipped["no han"] += 1
        else:
            kept.append(text)
    return kept, skipped


def syllables(text):
    out = lazy_pinyin(text, style=Style.NORMAL, errors=lambda chars: [None] * len(chars))
    return out if len(out) == len(text) and all(s and s.isalpha() for s in out) else None


def doubled_typos(pinyin):
    """Inputs with one letter of a doubled n or ng+g syllable boundary typed once (the 2026-10-21 rule)."""
    out = []
    for i in range(len(pinyin) - 1):
        left, right = pinyin[i], pinyin[i + 1]
        if right[0] == "n" and left.endswith("n") or right[0] == "g" and left.endswith("ng"):
            out.append("".join(pinyin[:i + 1]) + "".join(pinyin[i + 1:])[1:])
    return out


def excluded_texts(paths):
    texts = set()
    for path in paths:
        path = Path(path)
        if not path.exists():
            continue
        if path.suffix == ".json":
            data = json.loads(path.read_text(encoding="utf-8"))
            for row in data:
                texts.add(row["expect"] if isinstance(row, dict) else row[1])
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            fields = line.split("\t")
            if len(fields) >= 7:  # lexicon_regression.tsv: only reported and found cases, not the generic sets
                if fields[5].startswith(("user", "found", "纠错")):
                    texts.update(HAN.findall(fields[3]))
            elif len(fields) >= 2:  # CyIME-Windows reported-cases.tsv: input, expect, top_k, note
                texts.update(HAN.findall(fields[1]))
    return texts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--after", required=True, help="ISO timestamp; only messages after it are used")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--max-chars", type=int, default=200)
    parser.add_argument("--exclude", nargs="*", default=[
        str(REPO / "tools/ime_lab/fixtures/lexicon_regression.tsv"),
        "D:/GitHub/CyIME-Windows/tests/fixtures/reported-cases.tsv"],
                        help="fixed regression cases: left out of every group")
    parser.add_argument("--exclude-typo", nargs="*", default=[
        "D:/CyIME-Data/rime-eval/doubled.json", "D:/CyIME-Data/tmp/align/dbl_meta.json"],
                        help="the words the typo rule was developed on: left out of the typo groups")
    parser.add_argument("--learn-from", type=Path, help="text written before --after, for learn.tsv")
    parser.add_argument("--learn-top", type=int, default=3000)
    args = parser.parse_args()
    jieba.setLogLevel(60)
    messages, skipped = typed_messages(args.corpus, args.after, args.max_chars)
    banned = excluded_texts(args.exclude)
    typo_dev = excluded_texts(args.exclude_typo)
    clauses, words = Counter(), Counter()
    for text in messages:
        for run in HAN.findall(text):
            if 2 <= len(run) <= 12:
                clauses[run] += 1
            for word in jieba.lcut(run, HMM=False):
                if 2 <= len(word) <= 4 and HAN.fullmatch(word):
                    words[word] += 1
    sentence, jianpin, dropped = [], [], Counter()
    for group, items in (("clause", clauses), ("word", words)):
        for text, count in sorted(items.items(), key=lambda kv: (-kv[1], kv[0])):
            if text in banned:
                dropped["reported case"] += 1
                continue
            pinyin = syllables(text)
            if not pinyin:
                dropped["no pinyin"] += 1
                continue
            base = {"group": group, "context": "", "text": text, "pinyin": pinyin, "count": count}
            sentence.append({**base, "id": f"{group[0]}{len(sentence) + 1:05d}"})
            typos = doubled_typos(pinyin)
            if typos and text in typo_dev:
                dropped["typo development set"] += 1
                typos = []
            for keys in typos:
                sentence.append({**base, "group": f"typo-{group}", "keys": keys,
                                 "id": f"t{group[0]}{len(sentence) + 1:05d}"})
            if group == "word":
                keys = "".join(s[0] for s in pinyin)
                jianpin.append({**base, "group": "initials", "keys": keys, "id": f"j{len(jianpin) + 1:05d}"})
    args.output.mkdir(parents=True, exist_ok=True)
    for name, rows in (("sentence", sentence), ("jianpin", jianpin)):
        (args.output / f"{name}.jsonl").write_text(
            "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    if args.learn_from:
        learned = Counter(word for line in args.learn_from.read_text(encoding="utf-8").splitlines()
                          for run in HAN.findall(line) for word in jieba.lcut(run, HMM=False)
                          if 2 <= len(word) <= 4 and HAN.fullmatch(word))
        rows = [(pinyin, word, count) for word, count in learned.most_common()
                if (pinyin := syllables(word))][:args.learn_top]
        (args.output / "learn.tsv").write_text("".join(f"{''.join(p)}\t{w}\t{c}\n" for p, w, c in rows),
                                              encoding="utf-8")
    stats = {"after": args.after, "messages": len(messages), "skipped_messages": dict(skipped),
             "dropped_items": dict(dropped), "learn_words": args.learn_top if args.learn_from else 0,
             "items": dict(Counter(r["group"] for r in sentence + jianpin)),
             "occurrences": {g: sum(r["count"] for r in sentence + jianpin if r["group"] == g)
                             for g in sorted({r["group"] for r in sentence + jianpin})}}
    (args.output / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
