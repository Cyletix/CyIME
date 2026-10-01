"""Count original private text into a reviewable Rime lexicon, never fake user clicks.

Continuations are overlapping conditional statistics, not independent word counts.
This exporter rereads original documents, deduplicates them, keeps dictionary-backed
single-reading tokens, and exports missing/ambiguous readings for review.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import sqlite3

import jieba
from learning_workbench import clean_note, SKIP
from chat_corpus import check_output_location

HAN = re.compile(r"[\u3400-\u9fff]+")
CODE = re.compile(r"[a-z]+(?: [a-z]+)*")


def dictionary_readings(directory):
    readings = defaultdict(set)
    for path in sorted(Path(directory).glob("*.dict.yaml")):
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            fields = line.split("\t")
            if len(fields) >= 2 and HAN.fullmatch(fields[0]) and CODE.fullmatch(fields[1]):
                if len(fields[0]) == len(fields[1].split()):
                    readings[fields[0]].add(fields[1])
    return readings


def observations(documents):
    counts, support, stats = Counter(), Counter(), Counter()
    seen = set()
    for text in documents:
        text = clean_note(text).strip()
        digest = hashlib.sha256(text.encode()).digest()
        if not text or digest in seen:
            stats["duplicate_or_empty"] += 1
            continue
        seen.add(digest)
        words = Counter(word for run in HAN.findall(text)
                        for word in jieba.cut(run, cut_all=False) if len(word) >= 2)
        counts.update(words)
        support.update(words.keys())
        stats["documents"] += 1
        stats["token_observations"] += sum(words.values())
    return counts, support, stats


def partition(counts, support, readings, minimum=3, minimum_documents=2):
    accepted, review = [], []
    for word, count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
        if count < minimum or support[word] < minimum_documents:
            continue
        codes = readings.get(word, set())
        if len(codes) == 1:
            accepted.append((word, next(iter(codes)), count, support[word]))
        else:
            review.append((word, count, support[word], "missing_reading" if not codes else "ambiguous_reading"))
    return accepted, review


def documents(chats, notebook):
    with sqlite3.connect(Path(chats).resolve().as_uri() + "?mode=ro", uri=True) as db:
        for (text,) in db.execute("SELECT learning_text FROM messages ORDER BY id"):
            yield text
    root = Path(notebook).resolve()
    for path in sorted(root.rglob("*.md")):
        relative = path.relative_to(root)
        if any(part in SKIP or part.startswith(".") for part in relative.parts):
            continue
        if not path.resolve().is_relative_to(root) or path.stat().st_size > 4 * 1024 * 1024:
            continue
        yield path.read_text(encoding="utf-8-sig")


def export(chats, notebook, dictionary, output):
    output = Path(output).resolve()
    check_output_location(output)
    if output.exists():
        raise FileExistsError("Choose a new output directory; preserve prior reviews")
    counts, support, stats = observations(documents(chats, notebook))
    accepted, review = partition(counts, support, dictionary_readings(dictionary))
    output.mkdir(parents=True)
    # Raw occurrence counts, not probabilities or Rime userdb commits. A normal
    # dictionary pack compiles these through Rime's existing weight mechanism.
    body = "\n".join(f"{word}\t{code}\t{count}" for word, code, count, _ in accepted)
    (output / "cyime_corpus_words.dict.yaml").write_text(
        "# Private corpus frequency lexicon. Review before installation.\n---\n"
        "name: cyime_corpus_words\nversion: '1'\nsort: by_weight\nuse_preset_vocabulary: false\n...\n"
        + body + "\n", encoding="utf-8")
    (output / "words.json").write_text(json.dumps([
        dict(text=w, code=c, count=n, documents=d) for w, c, n, d in accepted], ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "needs-reading.json").write_text(json.dumps([
        dict(text=w, count=n, documents=d, reason=r) for w, n, d, r in review], ensure_ascii=False, indent=2), encoding="utf-8")
    summary = dict(stats, accepted_words=len(accepted), needs_reading=len(review),
                   unique_tokens=len(counts), installed=False,
                   scope="dictionary-backed token counts; not conditional continuation weights or actual user history",
                   exclusions="single characters, code blocks, duplicate documents, rare tokens, unknown/ambiguous readings")
    (output / "report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for flag in ("chats", "notebook", "dictionary", "output"):
        parser.add_argument("--" + flag, required=True)
    args = parser.parse_args()
    print(json.dumps(export(args.chats, args.notebook, args.dictionary, args.output), ensure_ascii=False))
