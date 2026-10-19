"""Minority-reading table for the joint decoder (t9/reading_table).

For every text with more than one reading in the imported dictionaries, write
text<TAB>spelling<TAB>ln(count / count of the text's main reading) for the readings below
--threshold. The word model scores words, so without this 的 (also read di, as in 目的) took
xxdi from xx地. Duplicates follow Rime: the first imported table wins. cn_dicts/tencent is skipped
(one reading per word, and a million lines the Gradle task would have to hold).

    python tools/ime_lab/reading_table.py app/build/generated/chinese-assets/rime_ice out.txt
"""
import argparse
import math
import re
from pathlib import Path


def imports(root: Path):
    text = (root / "rime_ice.dict.yaml").read_text(encoding="utf-8")
    block = text.split("import_tables:", 1)[1].split("\n...", 1)[0]
    return re.findall(r"(?m)^  - ([^\s#]+)", block)


def entries(path: Path):
    body = path.read_text(encoding="utf-8").split("\n...\n", 1)[1]
    for line in body.splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) >= 2 and re.fullmatch(r"[a-z]+( [a-z]+)*", parts[1]):  # rows without a code are encoded by Rime
            weight = float(parts[2]) if len(parts) > 2 and parts[2].strip() else 0.0
            yield parts[0], parts[1], weight


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("data", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--threshold", type=float, default=-1.0)
    args = parser.parse_args()
    seen = {}
    for name in imports(args.data):
        if name == "cn_dicts/tencent":
            continue
        for text, code, weight in entries(args.data / f"{name}.dict.yaml"):
            seen.setdefault((text, code), weight)
    by_text = {}
    for (text, code), weight in seen.items():
        by_text.setdefault(text, []).append((code, weight))
    rows = []
    for text, readings in by_text.items():
        if len(readings) < 2:
            continue
        top = max(weight for _, weight in readings)
        if top <= 0:
            continue
        for code, weight in readings:
            share = math.log(max(weight, 1.0) / top)
            if share < args.threshold:
                rows.append(f"{text}\t{code}\t{share:.3f}")
    args.output.write_text("\n".join(sorted(rows)) + "\n", encoding="utf-8", newline="\n")
    print(len(rows), "readings")


if __name__ == "__main__":
    main()
