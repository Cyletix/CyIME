"""从日语语法笔记表格（xlsx）生成训练模式的“日语句子”题库。

用法：
    python tools/handwriting_training/extract_japanese.py D:/CyIME-Data/handwriting_training/日语语法_修订版.xlsx \
        D:/CyIME-Data/handwriting_training/banks/japanese.tsv

取 N1/N2/N3 的语法点和例句、基本文的句子、单词表的单词。题目下方显示语法点和意思方便顺便学习。
输出 TSV 每行：`类别\t长句(0/1)\t题目\t提示\t来源`。短题（≤20 字）为主，长句标 1，App 里偶尔穿插。
表格内容来自教材笔记，生成的 TSV 是外置题库，不进安装包也不进仓库。
"""
from __future__ import annotations

import argparse
import pathlib
import random
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

LONG = 20
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
T = "{%s}t" % NS["m"]
KANA_OR_KANJI = re.compile(r"[\u3040-\u30ff\u4e00-\u9fff]")


def read_xlsx(path: str) -> dict[str, list[dict[str, str]]]:
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
            shared.append("".join(t.text or "" for t in si.iter(T)))
    names = [s.get("name") for s in ET.fromstring(z.read("xl/workbook.xml")).find("m:sheets", NS)]
    out = {}
    for i, name in enumerate(names, 1):
        rows = []
        for row in ET.fromstring(z.read(f"xl/worksheets/sheet{i}.xml")).findall(".//m:sheetData/m:row", NS):
            vals = {}
            for c in row.findall("m:c", NS):
                col = "".join(ch for ch in c.get("r") if ch.isalpha())
                v = c.find("m:v", NS)
                if c.get("t") == "s" and v is not None:
                    vals[col] = shared[int(v.text)]
                elif c.get("t") == "inlineStr":
                    vals[col] = "".join(t.text or "" for t in c.iter(T))
                else:
                    vals[col] = v.text if v is not None else ""
            rows.append(vals)
        out[name] = rows
    return out


def clean(s: str | None) -> str:
    return re.sub(r"\s+", " ", (s or "").strip())


def first_line(s: str | None) -> str:
    return clean((s or "").strip().split("\n")[0])


def jp_meaning(s: str | None) -> str:
    """意味栏常是“日文解释\\n中文解释\\n[硬い言い方]”，优先取含中文的那行。"""
    lines = [clean(x) for x in (s or "").split("\n") if clean(x)]
    zh = [x for x in lines if re.search(r"[\u4e00-\u9fff]", x) and not re.search(r"[\u3040-\u30ff]", x)]
    return (zh or lines or [""])[0]


def grammar_label(g: str) -> str:
    return clean(g).replace("～", "~").strip()


def collect(book: dict) -> list[tuple[str, int, str, str, str]]:
    items: list[tuple[str, int, str, str, str]] = []

    def add(kind: str, text: str, hint: str, source: str) -> None:
        text = clean(text)
        if not text or not KANA_OR_KANJI.search(text) or len(text) > 60:
            return
        items.append((kind, 1 if len(text) > LONG else 0, text, clean(hint).rstrip(" ·"), source))

    for level, g_col, m_col, ex_col, zh_col in (("N1", "E", "F", "I", "J"), ("N2", "E", "F", "J", "K")):
        grammar = meaning = lesson = ""
        for r in book.get(f"{level}完全マスター", [])[1:]:
            lesson = r.get("B") or lesson
            if clean(r.get(g_col)):
                grammar, meaning = grammar_label(r[g_col]), jp_meaning(r.get(m_col))
                add("grammar", grammar.strip("~ "), f"{level} 语法 · {meaning}", f"{level}:{lesson}")
            if clean(r.get(ex_col)) and grammar:
                add("sentence", r[ex_col], f"{level} {grammar} · {clean(r.get(zh_col))}", f"{level}:{lesson}")

    grammar = meaning = ""
    for r in book.get("N3完全マスター", []):
        if clean(r.get("B")) and "課" not in r["B"]:
            grammar, meaning = grammar_label(r["B"]), clean(r.get("C"))
            add("grammar", grammar.strip("~…. "), f"N3 语法 · {meaning}", "N3")
        elif clean(r.get("C")):
            meaning = clean(r["C"])
        ex = (r.get("D") or "").split("\n")
        if grammar and clean(ex[0]) and "課" not in ex[0] and not ex[0].startswith("~"):
            add("sentence", ex[0], f"N3 {grammar} · {clean(ex[1]) if len(ex) > 1 else meaning}", "N3")

    for r in book.get("基本文", [])[1:]:
        if clean(r.get("B")):
            add("sentence", r["B"], f"基本文 · {clean(r.get('G'))}", "基本文")

    for r in book.get("单词", [])[1:]:
        kana, word, mean = clean(r.get("A")), clean(r.get("B")), clean(r.get("C"))
        if word and KANA_OR_KANJI.search(word):
            add("word", word, f"单词 · {kana} · {mean}" if kana != word else f"单词 · {mean}", "单词")
        elif kana:
            add("word", kana, f"单词 · {mean}", "单词")

    seen, unique = set(), []
    for it in items:
        if it[2] not in seen:
            seen.add(it[2])
            unique.append(it)
    return unique


def order(items: list) -> list:
    """短题打乱为主，每 8 题穿插一道长句。"""
    rnd = random.Random(20261012)
    short = [i for i in items if not i[1]]
    long = [i for i in items if i[1]]
    rnd.shuffle(short)
    rnd.shuffle(long)
    out = []
    while short or long:
        out.extend(short[:7]); short = short[7:]
        if long:
            out.append(long.pop())
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("output")
    args = ap.parse_args()
    items = order(collect(read_xlsx(args.xlsx)))
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for kind, is_long, text, hint, source in items:
            f.write("\t".join([kind, str(is_long), text, hint.replace("\t", " "), source]) + "\n")
    kinds = {}
    for it in items:
        kinds[it[0]] = kinds.get(it[0], 0) + 1
    print(f"{len(items)} 题（长句 {sum(i[1] for i in items)}）{kinds} → {out}（{out.stat().st_size} 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
