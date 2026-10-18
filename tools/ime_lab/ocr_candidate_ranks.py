"""Turn on-device candidate-bar OCR (ocr.ps1 word boxes) into rank_score.py JSONL, without touching a device.

The device benchmark saved one candidate-bar crop per item and OCR'd it twice (raw ocr.json and a
contrast-normalised ocr_pre.json). Candidates are split at gaps wider than GAP x the median character
width or at any non-Chinese word (pinyin / English comments). The target's rank is the first visible
candidate equal to it in either OCR pass; the human-adjudicated first-candidate verdict from per_item.csv
overrides rank 1. Ranks past the visible bar, or candidates OCR misread, count as not found, so scores
beyond rank 1 are a lower bound.

  ocr_candidate_ranks.py BENCH_DIR OUT_DIR [--gap 0.4]
writes OUT_DIR/<ime>.jsonl (groups full26 / jp26 / t9, weight = corpus occurrence count).
"""

import argparse
import csv
import json
from pathlib import Path

RUNS = {"CyIME": ("cyime_26", "cyime_t9"), "搜狗": ("sogou_26", "sogou_t9"),
        "Gboard": ("gboard_26", "gboard_t9"), "三星": ("samsung_26", "samsung_t9")}
FILE_NAMES = {"CyIME": "cyime", "搜狗": "sogou", "Gboard": "gboard", "三星": "samsung"}


def is_han(s):
    return any("一" <= c <= "鿿" for c in s)


def candidates(words, gap):
    if not words:
        return []
    rows = {}
    for w in words:
        rows.setdefault(round((w["y"] + w["h"] / 2) / 20), []).append(w)
    row = max(rows.values(), key=lambda r: sum(len(w["t"]) for w in r if is_han(w["t"])))
    # Words of neighbouring buckets on the same line (comments sit a little lower) act as separators.
    ys = [w["y"] + w["h"] / 2 for w in row]
    lo, hi = min(ys) - 20, max(ys) + 20
    line = sorted((w for w in words if lo <= w["y"] + w["h"] / 2 <= hi), key=lambda w: w["x"])
    han = [w for w in line if is_han(w["t"])]
    if not han:
        return []
    ch = sorted(w["w"] / max(1, len(w["t"])) for w in han)[len(han) // 2]
    out, cur, last = [], "", None
    for w in line:
        if not is_han(w["t"]):
            if cur:
                out.append(cur)
            cur, last = "", None
            continue
        if last is not None and w["x"] - (last["x"] + last["w"]) > gap * ch:
            out.append(cur)
            cur = ""
        cur += w["t"]
        last = w
    if cur:
        out.append(cur)
    return out


def load_ocr(path):
    if not path.exists():
        return {}
    return {r["file"][:-4]: r["words"] for r in json.loads(path.read_text(encoding="utf-8-sig"))}


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("bench", type=Path)
    p.add_argument("out", type=Path)
    p.add_argument("--gap", type=float, default=0.4)
    a = p.parse_args()
    items = {f"{x['set']}-{x['id']}": x for x in map(json.loads, (a.bench / "items.jsonl").read_text(encoding="utf-8").splitlines())}
    with open(a.bench / "per_item.csv", encoding="utf-8-sig") as f:
        verdict = {row["key"]: row for row in csv.DictReader(f)}
    a.out.mkdir(parents=True, exist_ok=True)
    for ime, runs in RUNS.items():
        ocr = []
        for run in runs:
            d = a.bench / "runs" / run
            ocr.append((load_ocr(d / "ocr.json"), load_ocr(d / "ocr_pre.json")))
            retry = a.bench / "runs" / f"{run}b"  # items re-typed after a failed first pass
            if retry.exists():
                ocr.insert(0, (load_ocr(retry / "ocr.json"), load_ocr(retry / "ocr_pre.json")))
        rows, agree = [], [0, 0]
        for key, it in items.items():
            if key not in verdict:
                continue
            ranks, cands = [], []
            for raw, pre in ocr:
                if key in raw or key in pre:
                    for words in (raw.get(key), pre.get(key)):
                        c = candidates(words or [], a.gap)
                        cands.append(c)
                        if it["target"] in c:
                            ranks.append(c.index(it["target"]) + 1)
                    break
            ok = verdict[key][f"{ime}_对"] == "1"
            rank = min(ranks) if ranks else None
            agree[0] += (rank == 1) == ok
            agree[1] += 1
            rows.append({"id": key, "group": it["set"], "target": it["target"], "rank": rank, "known_top1": ok,
                         "weight": it.get("count", 1), "visible": max((len(c) for c in cands), default=0)})
        (a.out / f"{FILE_NAMES[ime]}.jsonl").write_text(
            "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
        print(f"{ime}: {len(rows)} items, OCR rank-1 agrees with adjudicated top-1 on {agree[0]}/{agree[1]}")


if __name__ == "__main__":
    main()
