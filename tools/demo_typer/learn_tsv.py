"""Words the demo script types, with their pinyin, for CyIME's dev op `learn` (learn.tsv: 词<TAB>拼 音<TAB>次数).

Each plain Chinese token in demo.yaml (full pinyin, typed then committed with space) is matched against the
segment's text in order: the next run of hanzi whose toneless pinyin spells the token is the word it must
produce. Abbreviations, shuangpin and English bursts don't spell out and are skipped.

  PYTHONPATH=<venv with pypinyin> python tools/demo_typer/learn_tsv.py --out D:/CyIME-Data/demo-video/learn.tsv
"""
import argparse
import os
import re

import yaml
from pypinyin import Style, lazy_pinyin

HERE = os.path.dirname(os.path.abspath(__file__))
HANZI = re.compile(r"[\u4e00-\u9fff]")


def tokens(segment):
    layout = segment["layout"]
    for line in segment["do"]:
        for t in line.split():
            if t.startswith("layout="):
                layout = t[7:]
            word = t[:-1] if t.endswith("~") else t
            if layout == "cn26" and re.fullmatch(r"[a-z]+", word):
                yield word


def align(text, words):
    """Yield (hanzi, spaced pinyin) for each token that spells the next hanzi run of the text."""
    chars = [(i, c) for i, c in enumerate(text) if HANZI.match(c)]
    pos = 0
    for word in words:
        for start in range(pos, len(chars)):
            spelled, end = "", start
            syllables = []
            while end < len(chars) and len(spelled) < len(word):
                py = lazy_pinyin(chars[end][1], style=Style.NORMAL)[0].replace("ü", "v")
                spelled += py
                syllables.append(py)
                end += 1
            if spelled == word:
                yield "".join(c for _, c in chars[start:end]), " ".join(syllables)
                pos = end
                break
            if start - pos > 6:  # the token is not in the next few characters: abbreviation or English
                break


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--script", default=os.path.join(HERE, "demo.yaml"))
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    plan = yaml.safe_load(open(args.script, encoding="utf-8"))
    seen = {}
    for segment in plan["segments"]:
        for hanzi, pinyin in align(segment["text"], list(tokens(segment))):
            seen.setdefault((hanzi, pinyin), args.count)
    with open(args.out, "w", encoding="utf-8", newline="\n") as out:
        for (hanzi, pinyin), count in seen.items():
            out.write(f"{hanzi}\t{pinyin}\t{count}\n")
    print(args.out, len(seen), "words")


if __name__ == "__main__":
    main()
