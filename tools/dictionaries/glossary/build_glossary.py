"""生成候选“中英释义”词典包（cyime_gloss）。

输入：
  ECDICT ecdict.csv（MIT，https://github.com/skywind3000/ECDICT）：英文词 → 中文释义
  CC-CEDICT cedict_1_0_ts_utf-8_mdbg.txt(.gz)（CC BY-SA 4.0，https://www.mdbg.net/chinese/dictionary?page=cc-cedict）：中文词 → 英文释义

输出：一个 UTF-8 文本文件。开头若干行以 # 开头的说明（来源与许可），之后每行 `词\t释义`，
按 UTF-8 字节序排序、键唯一，供 App 用内存映射 + 二分查找读取，不整表载入内存。
英文键统一小写；中文键同时收简体和繁体写法。

用法：python -I build_glossary.py --ecdict ecdict.csv --cedict cedict.txt.gz --output cyime_gloss.tsv
"""
from __future__ import annotations

import argparse
import csv
import gzip
import re
import sys

EN_WORD = re.compile(r"[A-Za-z][A-Za-z'\-]*")
POS_PREFIX = re.compile(r"^(?:[a-z]+\.\s*)+")
BRACKET_TAG = re.compile(r"^\[[^\]]*\]\s*")
PARENS = re.compile(r"[（(][^（）()]*[）)]")
ZH_SPLIT = re.compile(r"[；;，,、]")
EN_SKIP = re.compile(r"^(?:variant of|old variant|see |see also|CL:|used in |abbr\. for|surname |erhua variant|Japanese variant|archaic variant)", re.I)
EN_LABEL = re.compile(r"^(?:\([^)]*\)\s*)+")
CEDICT_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$")

ZH_GLOSS_MAX = 8
EN_GLOSS_MAX = 24

HEADER = [
    "# CyIME 候选中英释义词典 cyime_gloss",
    "# 英→中释义取自 ECDICT，https://github.com/skywind3000/ECDICT ，其许可如下：",
    "#   MIT License. Copyright (c) 2025 Linwei",
    "#   Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated",
    "#   documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation",
    "#   the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to",
    "#   permit persons to whom the Software is furnished to do so, subject to the following conditions:",
    "#   The above copyright notice and this permission notice shall be included in all copies or substantial portions of",
    "#   the Software.",
    "#   THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO",
    "#   THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE",
    "#   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,",
    "#   TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE",
    "#   SOFTWARE.",
    "# 中→英释义取自 CC-CEDICT（Creative Commons Attribution-ShareAlike 4.0，MDBG），https://www.mdbg.net/chinese/dictionary?page=cc-cedict",
    "# 本文件是上述数据的裁剪改编，按 CC BY-SA 4.0 分发：https://creativecommons.org/licenses/by-sa/4.0/",
    "# 格式：每行 词<TAB>释义，按 UTF-8 字节序排序；以 # 开头的行是说明。",
]


def clean_zh(translation: str) -> str:
    lines = [line.strip() for line in translation.replace("\\n", "\n").split("\n") if line.strip()]
    # ECDICT 把名词义项排在最前，常把形容词/动词为主的词释成冷僻名词义（good → 善行）。
    # 取义项最多的那一行作为主词性；网络、专业释义只在没有正式释义时才用。
    formal = [l for l in lines if not l.startswith("[") and "的过去式" not in l and "的复数" not in l]
    ordered = sorted(formal, key=lambda l: -len(ZH_SPLIT.split(l))) + [l for l in lines if l not in formal]
    for line in ordered:
        text = BRACKET_TAG.sub("", POS_PREFIX.sub("", line))
        text = BRACKET_TAG.sub("", text)
        for piece in ZH_SPLIT.split(text):
            piece = PARENS.sub("", piece).strip(" .。=…")
            if piece and re.search(r"[\u4e00-\u9fff]", piece):
                return piece[:ZH_GLOSS_MAX] + ("…" if len(piece) > ZH_GLOSS_MAX else "")
    return ""


def clean_en(senses: list[str]) -> str:
    usable = [s for s in senses if s and not EN_SKIP.match(EN_LABEL.sub("", s))]
    for sense in usable:
        text = EN_LABEL.sub("", sense).split(";")[0]
        text = re.sub(r"\([^)]*\)", "", text)
        text = re.sub(r"\S+\|\S+\[[^\]]*\]", "", text)  # 内嵌的中文交叉引用
        text = re.sub(r"\s+", " ", text).strip(" ,.")
        text = re.sub(r"^to ", "", text)
        if not text or re.search(r"[\u4e00-\u9fff]", text):
            continue
        if len(text) > EN_GLOSS_MAX:
            cut = text[:EN_GLOSS_MAX].rsplit(" ", 1)[0]
            text = (cut or text[:EN_GLOSS_MAX]) + "…"
        return text
    return ""


def load_ecdict(path: str) -> dict[str, str]:
    result: dict[str, tuple[int, str]] = {}
    with open(path, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            word = row["word"]
            if not EN_WORD.fullmatch(word):
                continue
            frq, bnc = int(row["frq"] or 0), int(row["bnc"] or 0)
            common = frq > 0 or bnc > 0 or row["tag"] or row["collins"] or row["oxford"]
            if not common:
                continue
            gloss = clean_zh(row["translation"])
            if not gloss:
                continue
            key = word.lower()
            # 同一小写键：优先小写原词（普通词），其次词频高的。
            rank = (0 if word == key else 1)
            if key not in result or rank < result[key][0]:
                result[key] = (rank, gloss)
    return {k: v[1] for k, v in result.items()}


def load_cedict(path: str) -> dict[str, str]:
    opener = gzip.open if path.endswith(".gz") else open
    result: dict[str, tuple[tuple[int, int], str]] = {}
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#"):
                continue
            match = CEDICT_LINE.match(line)
            if not match:
                continue
            trad, simp, pinyin, body = match.groups()
            senses = body.split("/")
            gloss = clean_en(senses)
            if not gloss:
                continue
            # 同一写法多个读音时只留一条：专名（拼音首字母大写）靠后，其次取义项多的读音
            # （常用读音义项多，如 东西 dong1xi5 “thing” 胜过 dong1xi1 “east and west”）。
            usable = sum(1 for s in senses if s and not EN_SKIP.match(EN_LABEL.sub("", s)))
            rank = (1 if pinyin[:1].isupper() else 0, -usable)
            for key in dict.fromkeys((simp, trad)):
                if key not in result or rank < result[key][0]:
                    result[key] = (rank, gloss)
    return {k: v[1] for k, v in result.items()}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ecdict", required=True)
    parser.add_argument("--cedict", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    entries = load_ecdict(args.ecdict)
    english = len(entries)
    chinese = load_cedict(args.cedict)
    for key, gloss in chinese.items():
        entries.setdefault(key, gloss)
    rows = sorted(((k, v) for k, v in entries.items() if "\t" not in k + v and "\n" not in k + v),
                  key=lambda item: item[0].encode("utf-8"))
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(HEADER) + "\n")
        for key, gloss in rows:
            out.write(f"{key}\t{gloss}\n")
    print(f"english={english} chinese={len(chinese)} total={len(rows)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
