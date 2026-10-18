"""从英语笔记仓库（Obsidian）生成训练模式的“英语”题库。

用法：
    python tools/handwriting_training/extract_english.py D:/OneDrive/Project/English \
        D:/CyIME-Data/handwriting_training/banks/english.tsv

- 单词（words/**）：题目是单词，提示是音标和中文；每个单词再取一条最短的例句；
- 短语（phrases/）：题目是短语，提示是中文，外加例句；
- 句子（Sentence/）：整句，提示是中文翻译和第一条句式分析。
40 个字符以内算短题，更长的句子标为长句（最长 140），App 里偶尔穿插。
输出 TSV 每行：`类别\t长句(0/1)\t题目\t提示\t来源`，只读仓库，不改里面的文件。
"""
from __future__ import annotations

import argparse
import pathlib
import random
import re
import sys

SHORT, MAX_LEN = 40, 140
FRONT = re.compile(r"^---\n(.*?)\n---\n", re.S)
EXAMPLE = re.compile(r"^>\s*(.+?)\s*\n>\s*\{\{(.*?)\}\}", re.M)
PLACEHOLDER = re.compile(r"English Example|Chinese\d|^\s*$")


def front_list(front: str, key: str) -> list[str]:
    m = re.search(rf"^{key}:\s*\n((?:\s+-\s.*\n?)+)", front, re.M)
    if m:
        return [x.strip()[2:].strip().strip("'\"") for x in m.group(1).splitlines() if x.strip().startswith("-")]
    m = re.search(rf"^{key}:\s*(.+)$", front, re.M)
    return [m.group(1).strip().strip("'\"")] if m else []


def clean(s: str) -> str:
    s = re.sub(r"\*\*|__|`|\[\[|\]\]", "", s)
    s = re.sub(r"\s+([,.;:!?])", r"\1", re.sub(r"\s+", " ", s)).strip()
    s = re.sub(r"(\w) '(\w)", r"\1'\2", s)  # 例句里的 "Don 't"
    return s.lstrip("- ").strip()


def examples(text: str) -> list[tuple[str, str]]:
    out = []
    for en, zh in EXAMPLE.findall(text):
        en, zh = clean(en), clean(zh)
        if not PLACEHOLDER.search(en) and re.search(r"[A-Za-z]", en) and 3 <= len(en) <= MAX_LEN:
            out.append((en, zh))
    return out


def collect(repo: pathlib.Path) -> list[tuple[str, int, str, str, str]]:
    items: list[tuple[str, int, str, str, str]] = []

    def add(kind: str, text: str, hint: str, source: str) -> None:
        text = clean(text)
        if text and re.search(r"[A-Za-z]", text) and len(text) <= MAX_LEN:
            items.append((kind, 1 if len(text) > SHORT else 0, text, clean(hint), source))

    for md in sorted((repo / "words").rglob("*.md")):
        if md.name.lower() == "readme.md":
            continue
        text = md.read_text(encoding="utf-8", errors="replace")
        front = FRONT.match(text).group(1) if FRONT.match(text) else ""
        word = md.stem
        meanings = (x.lstrip("- ").strip() for x in front_list(front, "Chinese"))
        zh = " / ".join(dict.fromkeys(x for x in meanings if x))[:30]
        ipa = (front_list(front, "ipa_us") or front_list(front, "ipa_uk") or [""])[0]
        rel = md.relative_to(repo).as_posix()
        add("word", word, " · ".join(x for x in ("单词", ipa, zh) if x), rel)
        ex = sorted(examples(text), key=lambda e: len(e[0]))
        if ex:
            add("sentence", ex[0][0], f"{word} · {ex[0][1]}", rel)

    for md in sorted((repo / "phrases").glob("*.md")):
        if md.name.lower() == "readme.md":
            continue
        text = md.read_text(encoding="utf-8", errors="replace")
        front = FRONT.match(text).group(1) if FRONT.match(text) else ""
        zh = " / ".join(front_list(front, "Chinese"))[:30]
        rel = md.relative_to(repo).as_posix()
        add("phrase", md.stem, f"短语 · {zh}" if zh else "短语", rel)
        for en, czh in examples(text)[:2]:
            add("sentence", en, f"{md.stem} · {czh}", rel)

    for md in sorted((repo / "Sentence").glob("*.md")):
        if md.name.lower() == "readme.md":
            continue
        text = md.read_text(encoding="utf-8", errors="replace")
        sec = dict(re.findall(r"^## (\w+)\n(.*?)(?=^## |\Z)", text, re.M | re.S))
        sentence = clean(re.sub(r"!\[\[.*?\]\]", "", sec.get("Sentence", "")))
        translate = clean(sec.get("Translate", ""))[:40]
        analysis = clean((sec.get("Analysis", "").strip().splitlines() or [""])[0].lstrip("- "))
        rel = md.relative_to(repo).as_posix()
        add("sentence", sentence, " · ".join(x for x in (translate, analysis) if x), rel)

    seen, unique = set(), []
    for it in items:
        if it[2].lower() not in seen:
            seen.add(it[2].lower())
            unique.append(it)
    return unique


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("repo")
    ap.add_argument("output")
    args = ap.parse_args()
    items = collect(pathlib.Path(args.repo))
    rnd = random.Random(20261012)
    short = [i for i in items if not i[1]]
    long = [i for i in items if i[1]]
    rnd.shuffle(short)
    rnd.shuffle(long)
    ordered = []
    while short or long:
        ordered.extend(short[:7]); short = short[7:]
        if long:
            ordered.append(long.pop())
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for kind, is_long, text, hint, source in ordered:
            f.write("\t".join([kind, str(is_long), text, hint.replace("\t", " "), source]) + "\n")
    kinds = {}
    for it in ordered:
        kinds[it[0]] = kinds.get(it[0], 0) + 1
    print(f"{len(ordered)} 题（长句 {sum(i[1] for i in ordered)}）{kinds} → {out}（{out.stat().st_size} 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
