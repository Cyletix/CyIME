"""Pass/fail check for post-commit suggestions: after each prefix of a test sentence, does one of
the top-K base-table suggestions continue the sentence? Test sentences are never training data.

Usage: python suggest_eval.py TABLE(.tsv|.bin gz) SENTENCES.json [--top 3] [--old OTHER_TABLE]
Reports the overall rate, the rate after the very common single characters the user listed, and
the per-character detail. The lookup mirrors BaseAssociationModel.predict: longest Han suffix wins.
"""
import argparse, collections, gzip, json, sys
from pathlib import Path

COMMON = "的是了不一你我他她它才就这那个都也很在有"


def load(path):
    opener = gzip.open if Path(path).suffix == ".bin" or Path(path).suffix == ".gz" else open
    rows = collections.defaultdict(list)
    with opener(path, "rt", encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            ctx, word, count, total = line.rstrip("\n").split("\t")
            rows[ctx].append((word, int(count) / int(total)))
    return {k: sorted(v, key=lambda e: -e[1]) for k, v in rows.items()}


def predict(rows, context, top):
    tail = ""
    for ch in reversed(context):
        if "一" <= ch <= "鿿":
            tail = ch + tail
            if len(tail) == 4:
                break
        else:
            break
    for size in range(len(tail), 0, -1):
        hit = rows.get(tail[-size:])
        if hit:
            return [w for w, _ in hit[:top]]
    return []


def evaluate(rows, sentences, top):
    total = hit = 0
    by_char = collections.defaultdict(lambda: [0, 0])
    misses = []
    for text in sentences:
        han = "".join(ch for ch in text if "一" <= ch <= "鿿")
        for i in range(1, len(han)):
            prefix, rest = han[:i], han[i:]
            guesses = predict(rows, prefix, top)
            ok = any(rest.startswith(g) for g in guesses)
            total += 1; hit += ok
            last = prefix[-1]
            if last in COMMON:
                by_char[last][0] += 1; by_char[last][1] += ok
                if not ok and len(misses) < 40:
                    misses.append((prefix, rest[:3], guesses))
    return total, hit, by_char, misses


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("table"); ap.add_argument("sentences"); ap.add_argument("--top", type=int, default=3)
    ap.add_argument("--old")
    args = ap.parse_args()
    data = json.loads(Path(args.sentences).read_text(encoding="utf-8"))
    sentences = [item["text"] if isinstance(item, dict) else item for item in data]
    for label, path in (("new", args.table), ("old", args.old)):
        if not path:
            continue
        rows = load(path)
        total, hit, by_char, misses = evaluate(rows, sentences, args.top)
        common_total = sum(v[0] for v in by_char.values()); common_hit = sum(v[1] for v in by_char.values())
        print(f"[{label}] {path}: contexts={len(rows)} positions={total} top{args.top}-hit={hit} ({hit/total:.1%}) | "
              f"after-common-chars={common_hit}/{common_total} ({common_hit/max(1,common_total):.1%})")
        print("   " + " ".join(f"{ch}:{v[1]}/{v[0]}" for ch, v in sorted(by_char.items(), key=lambda e: -e[1][0])))
        if label == "new":
            for prefix, rest, guesses in misses[:12]:
                print(f"   miss: {prefix}|{rest} top={guesses}")


if __name__ == "__main__":
    main()
