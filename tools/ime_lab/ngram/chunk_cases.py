"""Split fixture clauses into two typed chunks at the word boundary nearest the middle.

The first chunk stands for text already committed in the editor (left context), the second
is typed with or without that context. Words come from the model's own maximum-probability
segmentation, so the split never cuts a word the model knows.

Usage: python chunk_cases.py MODEL.lm OUTPUT.json FIXTURE.json [FIXTURE.json ...]
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ngram_query import Model  # noqa: E402

MAX_WORD = 6


def segment(model, text):
    best = [0.0] + [float("-inf")] * len(text)
    back = [0] * (len(text) + 1)
    for end in range(1, len(text) + 1):
        for start in range(max(0, end - MAX_WORD), end):
            ident = model.id(text[start:end])
            if ident is None and end - start > 1:
                continue
            score = best[start] + (model.ml(ident) if ident is not None else model.unseen_ml)
            if score > best[end]:
                best[end], back[end] = score, start
    bounds, end = [], len(text)
    while end > 0:
        bounds.append(end)
        end = back[end]
    return sorted(bounds)


def main():
    model = Model(sys.argv[1])
    cases = []
    for path in sys.argv[3:]:
        fixture = json.loads(Path(path).read_text(encoding="utf-8"))
        for case in fixture["cases"]:
            text, keys = case["text"], case["syllable_keys"]
            if len(text) < 4 or len(keys) != len(text):
                continue
            inner = [b for b in segment(model, text)[:-1] if 2 <= b <= len(text) - 2]
            if not inner:
                continue
            split = min(inner, key=lambda b: abs(b - len(text) / 2))
            cases.append({"fixture": Path(path).parent.name if Path(path).name == "fixture.json" else Path(path).stem,
                          "id": case["id"], "text": text, "keys": "".join(keys),
                          "left": text[:split], "right": text[split:], "right_keys": "".join(keys[split:])})
    Path(sys.argv[2]).write_text(json.dumps({"cases": cases}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"cases": len(cases)}))


if __name__ == "__main__":
    main()
