"""Turn held-out Han runs into a lexicon_regression.py public-set directory (sentence.jsonl + jianpin.jsonl).

The runs come from dialogues that were held out before training (prepare_data.py corpus
--holdout-sessions), so neither the old nor the new model has seen them. Readings are the
dictionary's (greedy word match, like prepare_data.py dev --raw); polyphones in context may be
wrong, which affects every model equally. Two halves are written: ``tune`` for choosing the
pruning threshold and ``final`` for the last comparison, split by a hash of the sentence.

Usage: holdout_public_set.py HOLDOUT.txt DICT_DIR OUT_DIR --group lccc-large [--sentences 600]
                             [--phrases 400] [--min-chars 4] [--max-chars 12]
"""
import argparse
import collections
import hashlib
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_data import greedy_tokens, readings  # noqa: E402

HAN_RUN = re.compile(r"[㐀-䶿一-鿿〇]+")


def pinyin_of(text, words, chars):
    tokens = greedy_tokens(text, words)
    pinyin = []
    for token in tokens:
        if token in words:
            pinyin += words[token][0].split()
        elif all(c in chars for c in token):
            pinyin += [chars[c][0] for c in token]
        else:
            return None
    return pinyin if len(pinyin) == len(text) else None


def initials(pinyin):
    return "".join(s[0] for s in pinyin)


def half(text, seed):
    return "tune" if hashlib.sha256(f"{seed}:{text}".encode("utf-8")).digest()[0] % 2 == 0 else "final"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("holdout", type=Path)
    parser.add_argument("dict_dir", type=Path)
    parser.add_argument("out_dir", type=Path)
    parser.add_argument("--group", default="lccc-large")
    parser.add_argument("--sentences", type=int, default=600, help="per half")
    parser.add_argument("--phrases", type=int, default=400, help="per half")
    parser.add_argument("--min-chars", type=int, default=4)
    parser.add_argument("--max-chars", type=int, default=12)
    parser.add_argument("--seed", default="20261018")
    args = parser.parse_args()
    words, chars = readings(args.dict_dir)
    sentences = []
    phrase_counts = collections.Counter()
    seen = set()
    for line in args.holdout.open(encoding="utf-8"):
        run = line.strip()
        if not run or not HAN_RUN.fullmatch(run):
            continue
        if 2 <= len(run) <= 4:
            phrase_counts[run] += 1
        if args.min_chars <= len(run) <= args.max_chars and run not in seen:
            seen.add(run)
            sentences.append(run)
    # Even sampling over the file keeps the dialogue order mixed; readings must resolve.
    step = max(1, len(sentences) // (args.sentences * 2 * 2))
    chosen = {"tune": [], "final": []}
    for run in sentences[::step]:
        pinyin = pinyin_of(run, words, chars)
        if pinyin is None:
            continue
        part = half(run, args.seed)
        if len(chosen[part]) < args.sentences:
            chosen[part].append((run, pinyin))
        if all(len(v) >= args.sentences for v in chosen.values()):
            break
    phrases = {"tune": [], "final": []}
    for run, count in phrase_counts.most_common():
        if count < 2:
            break
        pinyin = pinyin_of(run, words, chars)
        if pinyin is None:
            continue
        part = half(run, args.seed)
        if len(phrases[part]) < args.phrases:
            phrases[part].append((run, pinyin, count))
        if all(len(v) >= args.phrases for v in phrases.values()):
            break
    summary = {}
    for part in ("tune", "final"):
        out = args.out_dir / part
        out.mkdir(parents=True, exist_ok=True)
        with (out / "sentence.jsonl").open("w", encoding="utf-8", newline="\n") as f:
            for k, (run, pinyin) in enumerate(chosen[part], 1):
                f.write(json.dumps({"id": f"{part[0]}s{k:04}", "group": args.group, "context": "", "text": run,
                                    "pinyin": pinyin, "has_context": False}, ensure_ascii=False) + "\n")
        with (out / "jianpin.jsonl").open("w", encoding="utf-8", newline="\n") as f:
            for k, (run, pinyin, count) in enumerate(phrases[part], 1):
                f.write(json.dumps({"id": f"{part[0]}j{k:04}", "group": args.group, "context": "", "text": run,
                                    "pinyin": pinyin, "keys": initials(pinyin), "count": count}, ensure_ascii=False) + "\n")
        summary[part] = {"sentences": len(chosen[part]), "phrases": len(phrases[part])}
    summary["candidates"] = {"sentences": len(sentences), "phrases_with_count_ge_2": sum(1 for c in phrase_counts.values() if c >= 2)}
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
