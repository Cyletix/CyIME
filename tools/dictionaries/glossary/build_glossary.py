"""生成候选“中英释义”词典包（cyime_gloss）。

输入：
  ECDICT ecdict.csv（MIT，https://github.com/skywind3000/ECDICT）：英文词 → 中文释义
  CC-CEDICT cedict_1_0_ts_utf-8_mdbg.txt(.gz)（CC BY-SA 4.0，https://www.mdbg.net/chinese/dictionary?page=cc-cedict）：中文词 → 英文释义

输出：一个 UTF-8 文本文件。开头若干行以 # 开头的说明（来源与许可），之后每行 `词\t释义`，
按 UTF-8 字节序排序、键唯一，供 App 用内存映射 + 二分查找读取，不整表载入内存。
英文键统一小写；中文键同时收简体和繁体写法。

中文侧的取舍（v2，修 v1 的 几号 → heroin、在教 → be a believer、话 → dialect）：
  - 俚语、粗口、古旧、方言、宗教、学科术语等义项不用；义项全是这类的多字词不给释义。
  - 多字词只收 Rime 拼音词库里权重够高的常用词，且只用 Rime 里最常用读音对应的条目（重点 不是 recount）。
  - 义项优先选“英文译回中文正好是 / 含有这个词”的（用 ECDICT 反查），对不上用首义。
  - 单字只认主读音首义且正好译回本字，否则不显示（行 → row、被 → quilt 这类宁可不显示）。

用法：python -I build_glossary.py --ecdict ecdict.csv --cedict cedict.txt.gz         --rime-dict app/src/main/assets/rime/pinyin_simp.dict.yaml         --rime-dict app/src/main/assets/rime/pinyin_simp_ext.dict.yaml --output cyime_gloss-v2.tsv
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
EN_SKIP = re.compile(r"^(?:Taiwan pr\.|also pr\.|variant of|old variant|see |see also|CL:|used in |abbr\. for|surname |erhua variant|Japanese variant|archaic variant)", re.I)
EN_LABEL = re.compile(r"^(?:\([^)]*\)\s*)+")
# 俚语、粗话、贬义、古旧、方言、宗教等语域标注的义项不拿来做候选释义（几号 → heroin 就来自 "(slang) heroin"）。
EN_REGISTER = re.compile(r"\([^)]*\b(?:slang|vulgar|derog|offensive|pejorative|euphemism|archaic|obsolete|old|literary|"
                         r"classical|dialect|Cantonese|Hokkien|Wu|Buddhism|Buddhist|Taoism|Daoism|Islam|Christianity|religion|"
                         r"loanword|onom|neologism|Internet|chemistry|physics|math|mathematics|medicine|anatomy|zoology|botany|"
                         r"biology|geology|astronomy|TCM)\b[^)]*\)", re.I)
EN_GLOSS_CHOICES = 4
# 粗口义项不做候选释义（你妈 → “fuck you”）；字面直译说明（lit. …）也不是译文。
EN_PROFANITY = re.compile(r"\b(?:fuck\w*|shit\w*|cunt|bitch\w*|dick|cock|piss\w*|bastard|whore\w*|motherfuck\w*)\b", re.I)
EN_ALT_MAX_RANK = 20000   # 换用后面义项时，这个英文的词频名次上限
COMMON_EN_RANK: dict[str, int] = {}   # load_ecdict 填：英文词 → 词频名次
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


def en_candidates(senses: list[str]) -> list[str]:
    """按 CC-CEDICT 原顺序列出可用的英文释义（已清理）；带语域标注、交叉引用类的义项不收。"""
    result = []
    for sense in senses:
        if not sense:
            continue
        labels = EN_LABEL.match(sense)
        if labels and EN_REGISTER.search(labels.group(0)):
            continue
        body = EN_LABEL.sub("", sense)
        if EN_SKIP.match(body) or body.startswith("lit. ") or EN_PROFANITY.search(body):
            continue
        text = body.split(";")[0]
        text = re.sub(r"\([^)]*\)", "", text)
        text = re.sub(r"\S+\|\S+\[[^\]]*\]", "", text)  # 内嵌的中文交叉引用
        text = re.sub(r"\s+", " ", text).strip(" ,.")
        text = re.sub(r"^to ", "", text)
        if not text or re.search(r"[\u4e00-\u9fff]", text):
            continue
        if len(text) > EN_GLOSS_MAX:
            cut = text[:EN_GLOSS_MAX].rsplit(" ", 1)[0]
            text = (cut or text[:EN_GLOSS_MAX]) + "…"
        result.append(text)
    return result


def en_rank(text: str) -> int:
    """英文词频名次；词组按其中最生僻的词算（not bad 算常用，propyl 算冷僻）。"""
    key = text.rstrip("…").lower()
    if key in COMMON_EN_RANK:
        return COMMON_EN_RANK[key]
    words = re.findall(r"[a-z']+", key)
    return max((COMMON_EN_RANK.get(w, 10 ** 9) for w in words), default=10 ** 9)


def back_match(word: str, english: str, back: dict[str, str]) -> int:
    """用 ECDICT 把英文译回中文，看义项里有没有这个中文词：2 = 有一条义项正好是它（place → 地方），
    1 = 只是包含在更长的义项里（regional → 地方的），0 = 没有。
    带 [化]、[医]、[网络] 这类标签的义项不算（丙 → propyl 的 丙基 是化学义）。"""
    best = 0
    for line in back.get(english.rstrip("…").lower(), "").replace("\n", "\n").split("\n"):
        for piece in ZH_SPLIT.split(POS_PREFIX.sub("", line.strip())):
            piece = piece.strip()
            if not piece or piece.startswith("["):
                continue
            piece = PARENS.sub("", piece).strip(" .。")
            if piece == word:
                return 2
            if word in piece:
                best = 1
    return best


def clean_en(word: str, readings: list[list[str]], back: dict[str, str]) -> str:
    """从这个词的各个读音的义项里挑一个英文释义。

    CC-CEDICT 的读音和义项都不按常用程度排（没 有 mei2 “not have” 和 mo4 “drowned”，告诉 有 “tell”
    和 “press charges”，话 的首义是 dialect）。依次看各读音（义项多的读音在前）的前几个义项，取第一个
    “译回中文正好是 / 含有这个词”的；换到非首个候选时不收专名（东京 → Tonkin）和冷僻英文词（丙 → propyl）。
    都对不上时用义项最多的读音的首义。单字另有更严的规则，见下。按英文词频挑会把
    打 换成 from、偶 换成 image，不用。"""
    ordered = [choices[:EN_GLOSS_CHOICES] for choices in readings if choices]
    if not ordered:
        return ""
    first = ordered[0][0]
    if len(word) == 1:
        # 单字多音多义、又常作虚词：只认主读音的首义，且译回来正好是这个字（好 → good → 好）；
        # 换读音、换义项会得到 行 → row、发 → hair、会 → meet 这类，宁可不显示。
        return first if back_match(word, first, back) == 2 else ""
    allowed = [text for choices in ordered for text in choices
               if text is first or not (text[:1].isupper() or en_rank(text) > EN_ALT_MAX_RANK)]
    # 先找译回来正好是这个词的（place → 地方），再找包含它的（talk → 谈话）。
    for level in (2, 1):
        for text in allowed:
            if back_match(word, text, back) >= level:
                return text
    return first


def load_ecdict(path: str) -> tuple[dict[str, str], dict[str, str]]:
    """返回 (英文词 → 中文释义, 英文词或词组 → ECDICT 完整中文释义)，后者用来反查中文义项。"""
    result: dict[str, tuple[int, str]] = {}
    back: dict[str, str] = {}
    with open(path, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            word = row["word"]
            back[word.lower()] = back.get(word.lower(), "") + row["translation"]
            frq, bnc = int(row["frq"] or 0), int(row["bnc"] or 0)
            if frq or bnc:
                COMMON_EN_RANK[word.lower()] = min(COMMON_EN_RANK.get(word.lower(), 10 ** 9), frq or bnc)
            if not EN_WORD.fullmatch(word):
                continue
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
    return {k: v[1] for k, v in result.items()}, back


def load_rime_weights(paths: list[str]) -> dict[str, dict[str, int]]:
    """Rime 拼音词库：词 → {不带调拼音: 权重}。用来判断中文词是否常用，以及它常用的读音。"""
    weights: dict[str, dict[str, int]] = {}
    for path in paths:
        body = False
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                if line.startswith("..."):
                    body = True
                    continue
                if not body or line.startswith("#"):
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3 and parts[2].isdigit():
                    readings = weights.setdefault(parts[0], {})
                    key = plain_pinyin(parts[1])
                    readings[key] = max(readings.get(key, 0), int(parts[2]))
    return weights


def plain_pinyin(pinyin: str) -> str:
    """去掉声调、统一 ü 的写法，比较 Rime（lv）和 CC-CEDICT（lu:4）的读音。"""
    text = pinyin.lower().replace("u:", "v").replace("ü", "v")
    return " ".join(re.sub(r"[0-9]", "", text).split())


def load_cedict(path: str, back: dict[str, str], weights: dict[str, int], min_weight: int) -> dict[str, str]:
    """中文词 → 英文释义。多字词只收 Rime 词库里权重够高的常用词：CC-CEDICT 收了大量专门、罕见的词组，
    候选里由单字拼出来的 “在教” 会撞上 “to be a believer (in a religion)” 这类条目。单字不受限。"""
    opener = gzip.open if path.endswith(".gz") else open
    # 有普通读音的词不用专名读音的释义：普通读音的义项全被语域过滤掉时（中土 只剩 literary），
    # 宁可不显示，也不显示 “China-Turkey”。
    common_reading: set[str] = set()
    # 多字词只要有一个读音的义项全是俚语、书面古语等（几号 只有 slang，中土 普通读音只有 literary），
    # 整个词都不给释义，不拿另一个读音（专名）顶上。
    restricted: set[str] = set()
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            match = CEDICT_LINE.match(line)
            if not match:
                continue
            if not match.group(3)[:1].isupper():
                common_reading.update((match.group(1), match.group(2)))
            senses = [x for x in match.group(4).split("/") if x]
            if len(match.group(2)) > 1 and senses and all(
                    (m := EN_LABEL.match(x)) and EN_REGISTER.search(m.group(0)) for x in senses):
                restricted.update((match.group(1), match.group(2)))
    # 同一写法的各个读音：专名（拼音首字母大写）在后，义项多的读音在前
    # （常用读音义项多，如 东西 dong1xi5 “thing” 胜过 dong1xi1 “east and west”）。
    readings: dict[str, list[tuple[tuple[int, int], list[str], bool]]] = {}
    with opener(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#"):
                continue
            match = CEDICT_LINE.match(line)
            if not match:
                continue
            trad, simp, pinyin, body = match.groups()
            rime = weights.get(simp, {})
            if len(simp) > 1 and max(rime.values(), default=0) < min_weight:
                continue
            # 只用 Rime 里这个词最常用的那个读音对应的条目：重点 打出来是 zhong dian（emphasis），
            # 不能用 chong dian 的 “recount”；都会 是 dou hui，不能用 du hui 的 “city”。没有对应读音就不给释义。
            if rime and plain_pinyin(pinyin) != max(rime, key=rime.get):
                continue
            if pinyin[:1].isupper() and simp in common_reading or simp in restricted:
                continue
            choices = en_candidates(body.split("/"))
            if not choices:
                continue
            order = (1 if pinyin[:1].isupper() else 0, -len(choices))
            for key in dict.fromkeys((simp, trad)):
                # own：这一条写的就是这个字形本身（繁简同形，或按繁体字形查）。
                readings.setdefault(key, []).append((order, choices, key == trad))
    result: dict[str, str] = {}
    for key, entries in readings.items():
        # 繁简同形的字（年）不用别的繁体字形简化过来的条目（秊 → 年 的 “grain”）；
        # 只有简体字形（话 ← 話）才靠繁体条目。
        if any(own for _, _, own in entries):
            entries = [entry for entry in entries if entry[2]]
        gloss = clean_en(key, [choices for _, choices, _ in sorted(entries, key=lambda item: item[0])], back)
        if gloss:
            result[key] = gloss
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ecdict", required=True)
    parser.add_argument("--cedict", required=True)
    parser.add_argument("--rime-dict", action="append", required=True,
                        help="Rime 拼音词库（pinyin_simp.dict.yaml 等），可重复；用来筛常用中文词")
    parser.add_argument("--min-weight", type=int, default=500,
                        help="多字中文词在 Rime 词库里的最低权重")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    entries, back = load_ecdict(args.ecdict)
    english = len(entries)
    chinese = load_cedict(args.cedict, back, load_rime_weights(args.rime_dict), args.min_weight)
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
