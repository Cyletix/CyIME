"""Personal-habit test set from the messages the user wrote in the CyIME Claude project.

Input is project_messages.py output plus two hand-made files kept with the data (never in the repo):

  edits.tsv     id<TAB>from<TAB>to<TAB>kind<TAB>sure    one fix of a misheard / mistyped span
                id<TAB>DROP                             pasted or generated message, not typed
                id<TAB>KEEP<TAB>substring               keep only the typed part of a mixed message
                (id = project message id; kind = asr / ime / slip)
  reported.tsv  id<TAB>keys<TAB>expect<TAB>layout<TAB>note    cases the user reported (tuned on)

Fenced code blocks and quoted ">" lines are always removed. Corrected text is split the same way as
personal_holdout.py and written in the same public-set format (lexicon_regression.py --public-set,
CyIME-Windows tools/rime-eval):

  holdout/  messages after --after (nothing trained on them)   sentence.jsonl, jianpin.jsonl
  seen/     messages before --after (may be in the 10-13 personal training snapshot)
  fixes.jsonl     the corrected spans as typing items: group fix-word / fix-clause, "wrong" = what came out
  reported.jsonl  the user's reported cases, group reported (regression list, not hold-out)
  cleaned.jsonl   per message: original, corrected, applied edits, voice/typed guess
  stats.json

Items equal to a fixed regression case or a reported case are left out of holdout/ and seen/.
Nothing here prints message text; the output belongs outside the repository.

  project_testset.py --messages messages.jsonl --edits edits.tsv --reported reported.tsv \
                     --after 2026-10-13T03:15:00Z --output DIR
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

import jieba

from personal_holdout import HAN, doubled_typos, excluded_texts, syllables

FENCE = re.compile(r"```.*?(?:```|\Z)", re.S)
QUOTE = re.compile(r"^\s*>.*$", re.M)
PUNCT = re.compile(r"[,，.。?？!！;；:：、]")
VOICE_GAP = re.compile(r"[\u4e00-\u9fff] +[\u4e00-\u9fff]")


def read_rules(path):
    overrides, edits = {}, {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        fields = line.split("\t")
        msg_id = fields[0]
        if fields[1] in ("DROP", "KEEP"):
            overrides[msg_id] = (fields[1], fields[2] if len(fields) > 2 else "")
        else:
            edits.setdefault(msg_id, []).append(
                {"from": fields[1], "to": fields[2], "kind": fields[3], "sure": fields[4] == "1"})
    return overrides, edits


def guess_mode(text):
    """Speech input leaves spaces between phrases and almost no punctuation; typing uses commas."""
    gaps, punct = len(VOICE_GAP.findall(text)), len(PUNCT.findall(text))
    return "voice" if gaps >= 2 and gaps >= 3 * max(punct, 1) else "typed"


def apply_edits(text, edits, missing, msg_id):
    applied = []
    for edit in edits:
        if edit["from"] in text:
            text = text.replace(edit["from"], edit["to"])
            applied.append(edit)
        else:
            missing.append(msg_id)
    return text, applied


def clause_items(texts):
    clauses, words = Counter(), Counter()
    for text in texts:
        for run in HAN.findall(text):
            if 2 <= len(run) <= 12:
                clauses[run] += 1
            for word in jieba.lcut(run, HMM=False):
                if 2 <= len(word) <= 4 and HAN.fullmatch(word):
                    words[word] += 1
    return clauses, words


def build_set(texts, banned, dropped):
    clauses, words = clause_items(texts)
    sentence, jianpin = [], []
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
            for keys in doubled_typos(pinyin):
                sentence.append({**base, "group": f"typo-{group}", "keys": keys,
                                 "id": f"t{group[0]}{len(sentence) + 1:05d}"})
            if group == "word":
                keys = "".join(s[0] for s in pinyin)
                jianpin.append({**base, "group": "initials", "keys": keys, "id": f"j{len(jianpin) + 1:05d}"})
    return sentence, jianpin


def changed_han(old, new):
    """The Han text a fix put in: `new` itself when short, else the changed core widened to 2+ chars."""
    new_han = "".join(HAN.findall(new))
    start = 0
    while start < min(len(old), len(new)) and old[start] == new[start]:
        start += 1
    end = 0
    while end < min(len(old), len(new)) - start and old[-1 - end] == new[-1 - end]:
        end += 1
    core = "".join(HAN.findall(new[start:len(new) - end]))
    if not core:
        return ""  # only Latin text changed (Paraformer, Colemak): nothing to type in Chinese
    if len(new_han) <= 6:
        return new_han
    # Widen the core to the jieba words it touches; a lone one-character word takes its left neighbour.
    at = new_han.find(core)
    words, pos, picked = jieba.lcut(new_han, HMM=False), 0, []
    for i, word in enumerate(words):
        if pos < at + len(core) and at < pos + len(word):
            picked.append(i)
        pos += len(word)
    if not picked:
        return core
    lo, hi = picked[0], picked[-1]
    if sum(len(w) for w in words[lo:hi + 1]) < 2:
        lo, hi = (lo - 1, hi) if lo > 0 else (lo, min(hi + 1, len(words) - 1))
    return "".join(words[lo:hi + 1])


def fix_items(cleaned):
    """Each corrected span becomes a typing item: the word itself and the clause it sits in."""
    out, seen = [], set()
    for row in cleaned:
        runs = HAN.findall(row["corrected"])
        for edit in row["edits"]:
            target = changed_han(edit["from"], edit["to"])
            if not target or not HAN.search(edit["to"]):
                continue
            # Only text typed in one go: 跟QWER一样 must not become 跟一样.
            whole = any(target in run for run in runs)
            candidates = [("fix-word", target)] if 2 <= len(target) <= 6 and whole else []
            candidates += [("fix-clause", run) for run in runs if target in run and 2 <= len(run) <= 12]
            for group, text in candidates:
                pinyin = syllables(text)
                if not pinyin or (group, text) in seen:
                    continue
                seen.add((group, text))
                out.append({"group": group, "context": "", "text": text, "pinyin": pinyin, "count": 1,
                            "wrong": edit["from"], "kind": edit["kind"], "sure": edit["sure"],
                            "after_cutoff": row["after_cutoff"], "msg": row["id"],
                            "id": f"f{len(out) + 1:05d}"})
    return out


def write_jsonl(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--messages", required=True, type=Path)
    parser.add_argument("--edits", required=True, type=Path)
    parser.add_argument("--reported", required=True, type=Path)
    parser.add_argument("--after", required=True, help="ISO timestamp; later messages form the hold-out set")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--exclude", nargs="*", default=None, help="fixed regression cases (personal_holdout default)")
    args = parser.parse_args()
    jieba.setLogLevel(60)
    messages = [json.loads(line) for line in args.messages.read_text(encoding="utf-8").splitlines() if line.strip()]
    overrides, edits = read_rules(args.edits)

    cleaned, missing, skipped = [], [], Counter()
    for msg in messages:
        msg_id = msg["id"]
        action, keep = overrides.get(msg_id, (None, None))
        if action == "DROP":
            skipped["pasted"] += 1
            continue
        text = msg["text"]
        if action == "KEEP":
            if keep not in text:
                missing.append(msg_id)
                continue
            text = keep
        text = QUOTE.sub("", FENCE.sub(" ", text)).strip()
        if not HAN.search(text):
            skipped["no han"] += 1
            continue
        corrected, applied = apply_edits(text, edits.get(msg_id, []), missing, msg_id)
        cleaned.append({"id": msg["id"], "sent_at": msg["sent_at"], "thread": msg.get("thread"),
                        "after_cutoff": (msg["sent_at"] or "") > args.after, "mode": guess_mode(text),
                        "original": text, "corrected": corrected, "edits": applied})

    reported = []
    for line in args.reported.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            msg_id, keys, expect, layout, note = line.split("\t")
            reported.append({"group": "reported", "context": "", "text": expect, "keys": keys,
                             "pinyin": syllables(expect) or [], "layout": layout, "note": note,
                             "msg": msg_id, "count": 1, "id": f"r{len(reported) + 1:03d}"})
    exclude = args.exclude
    banned = excluded_texts(exclude) if exclude is not None else excluded_texts(
        [str(Path(__file__).resolve().parent / "fixtures/lexicon_regression.tsv"),
         "D:/GitHub/CyIME-Windows/tests/fixtures/reported-cases.tsv"])
    banned |= {r["text"] for r in reported}

    stats = {"after": args.after, "messages": len(messages), "kept_messages": len(cleaned),
             "skipped_messages": dict(skipped), "edits_missing_for": sorted(set(missing)),
             "edits_applied": sum(len(r["edits"]) for r in cleaned),
             "edits_by_kind": dict(Counter(e["kind"] for r in cleaned for e in r["edits"])),
             "mode": dict(Counter(r["mode"] for r in cleaned)), "reported": len(reported)}
    for split, rows in (("holdout", [r for r in cleaned if r["after_cutoff"]]),
                        ("seen", [r for r in cleaned if not r["after_cutoff"]])):
        dropped = Counter()
        sentence, jianpin = build_set([r["corrected"] for r in rows], banned, dropped)
        write_jsonl(args.output / split / "sentence.jsonl", sentence)
        write_jsonl(args.output / split / "jianpin.jsonl", jianpin)
        stats[split] = {"messages": len(rows), "dropped_items": dict(dropped),
                        "items": dict(Counter(r["group"] for r in sentence + jianpin)),
                        "occurrences": {g: sum(r["count"] for r in sentence + jianpin if r["group"] == g)
                                        for g in sorted({r["group"] for r in sentence + jianpin})}}
    fixes = fix_items(cleaned)
    stats["fixes"] = dict(Counter(f["group"] for f in fixes))
    # Each set directory holds jianpin.jsonl + sentence.jsonl, the pair the eval tools read.
    write_jsonl(args.output / "fixes" / "sentence.jsonl", fixes)
    write_jsonl(args.output / "fixes" / "jianpin.jsonl", [])
    top1 = [r for r in reported if r["layout"] == "26"]
    write_jsonl(args.output / "reported" / "sentence.jsonl", top1)
    write_jsonl(args.output / "reported" / "jianpin.jsonl", [])
    write_jsonl(args.output / "reported" / "other.jsonl", [r for r in reported if r["layout"] != "26"])
    write_jsonl(args.output / "cleaned.jsonl", cleaned)
    (args.output / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
