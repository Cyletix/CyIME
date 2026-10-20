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

v8：去掉带地名、人名的翻译短语（在青岛 → in Aoshima）。
v7：常见的说话用短语（带代词、虚词）改用电脑上离线机器翻译的通顺译文，只收英文译回中文能回到原短语的（roundtrip_filter.py），翻不准的仍用逐词版或不收。
v5：词库里常见、没有整词英文的日常短语预先逐词拼好英文（+ 前缀，只显示不上屏）。
v4：不可靠的单字释义加 “~” 前缀，供 App 拼整句英文时跳过。
v3：修 v2 反查时没拆开 ECDICT 多行释义的错误；单字改为“正好译回本字 → 按顺序第一个含本字的义项 → 首义”；
“X 的异体 / 见 X” 借用 X 的释义；CC-CEDICT 没收的常用词组用 ECDICT 反查补（很多 → plenty，博客 → blog）。

用法：python -I build_glossary.py --ecdict ecdict.csv --cedict cedict.txt.gz         --rime-dict app/src/main/assets/rime/pinyin_simp.dict.yaml         --rime-dict app/src/main/assets/rime/pinyin_simp_ext.dict.yaml --mt-phrases mt-phrases.tsv --output cyime_gloss-v8.tsv
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
APPROX = "~"   # 释义前缀：不可靠的单字释义（见 clean_en），App 显示时去掉
# “variant of 賬號|账号[zhang4 hao4]”、“see 深圳市[Shen1 zhen4 Shi4]” 指向的词
CROSS_REF = re.compile(r"(?:^|/)(?:variant of|see) ([^\[\s/]+)\[")
EN_SINGLE_CHOICES = 6    # 单字每个条目看前几个义项
# 粗口义项不做候选释义（你妈 → “fuck you”）；字面直译说明（lit. …）也不是译文。
EN_PROFANITY = re.compile(r"\b(?:fuck\w*|shit\w*|cunt|bitch\w*|dick|cock|piss\w*|bastard|whore\w*|motherfuck\w*)\b", re.I)
EN_ALT_MAX_RANK = 20000   # 换用后面义项时，这个英文的词频名次上限
COMMON_EN_RANK: dict[str, int] = {}
ORIGINAL_CASE: dict[str, str] = {}    # load_ecdict 填：小写英文 → ECDICT 原写法   # load_ecdict 填：英文词 → 词频名次
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
    "# 格式：每行 词<TAB>释义，按 UTF-8 字节序排序；以 # 开头的行是说明；释义以 ~ 开头的是不可靠的单字释义，以 + 开头的是逐词拼出的日常短语，显示时去掉前缀。",
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
        # “Shenzhen, subprovincial city …” 这类逗号后是说明，只留逗号前的名称。
        text = text.split(", ")[0]
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
    for line in back.get(english.rstrip("…").lower(), "").replace("\\n", "\n").split("\n"):
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
    都对不上时用义项最多的读音的首义。单字规则见下。不加反查、单纯按英文词频挑会把
    打 换成 from、偶 换成 image，不用。"""
    ordered = [choices[:EN_GLOSS_CHOICES] for choices in readings if choices]
    if not ordered:
        return ""
    first = ordered[0][0]
    if len(word) == 1:
        # 单字义项多、顺序不按常用（被 首义 quilt，跟 首义 heel，后 的同形条目是 empress）：
        # 先取译回来正好是这个字的义项；没有就按条目、义项顺序取第一个“译回来含这个字”的
        # （中 → middle，多 → many，难 → difficult）；都没有用首义。读音已限定为 Rime 的常用读音。
        # 改按英文词频挑反而更差（中 → hit，名 → place，自 → from）。
        candidates = [text for choices in ordered for text in choices[:EN_SINGLE_CHOICES]]
        exact = [text for text in candidates if back_match(word, text, back) == 2]
        if exact:
            return exact[0]
        near = [text for text in candidates
                if not text[:1].isupper() and en_rank(text) <= EN_ALT_MAX_RANK and back_match(word, text, back) == 1]
        # 没能“正好译回本字”的单字释义只算参考：加 APPROX 前缀，App 照常显示，但拼整句英文时不用它
        # （会 → meet、跟 → heel 拼进句子会变成 “I meet hard”）。
        return APPROX + (near[0] if near else first)
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
            # 反查补词时还原大小写（rodriguez → Rodriguez）：有原样小写写法就用小写。
            if word.lower() not in ORIGINAL_CASE or word == word.lower():
                ORIGINAL_CASE[word.lower()] = word
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


def load_cedict(path: str, back: dict[str, str], weights: dict[str, dict[str, int]], min_weight: int,
                engine: dict[str, dict[str, int]] | None = None, engine_min: int = 100) -> dict[str, str]:
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
    redirects: dict[str, str] = {}
    strict: set[str] = set()
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
                # 输入法实际用的是 rime_ice 词库，pinyin_simp 没收或权重低的常用词（口罩、缆车）也会出现在候选里。
                # 这类词只在 rime_ice 有这个读音时收，并且释义必须能用英汉词典反查回这个词（见 strict），
                # 否则候选里由单字拼出的 在教 会撞上它的宗教义 “be a believer”。
                lexicon = (engine or {}).get(simp, {})
                if max(lexicon.values(), default=0) < engine_min or plain_pinyin(pinyin) not in lexicon:
                    continue
                strict.update((simp, trad))
            # 只用 Rime 里这个词最常用的那个读音对应的条目：重点 打出来是 zhong dian（emphasis），
            # 不能用 chong dian 的 “recount”；都会 是 dou hui，不能用 du hui 的 “city”。没有对应读音就不给释义。
            elif rime and plain_pinyin(pinyin) != max(rime, key=rime.get):
                continue
            if pinyin[:1].isupper() and simp in common_reading or simp in restricted:
                continue
            choices = en_candidates(body.split("/"))
            if not choices:
                # 只是“X 的异体 / 见 X”的条目（帐号 → 账号，深圳 → 深圳市）：记下来，最后借用 X 的释义。
                target = CROSS_REF.search(body)
                if target:
                    for key in dict.fromkeys((simp, trad)):
                        redirects.setdefault(key, target.group(1).split("|")[-1])
                continue
            order = (1 if pinyin[:1].isupper() else 0, -len(choices))
            for key in dict.fromkeys((simp, trad)):
                # own：这一条写的就是这个字形本身（繁简同形，或按繁体字形查）。
                readings.setdefault(key, []).append((order, choices, key == trad))
    result: dict[str, str] = {}
    for key, entries in readings.items():
        # 本字形的条目排在别的繁体字形简化过来的条目前面（年 先看 年，再看 秊 “grain”）；
        # 简化字合并了几个繁体字时（后 ← 後，里 ← 裏）各条目都参与挑选。
        ordered = sorted(entries, key=lambda item: (not item[2], item[0]))
        lists = [choices for _, choices, _ in ordered]
        if key in strict:
            # 只认能反查回这个词的义项，对不上就不给释义。
            gloss = next((text for choices in lists for text in choices[:EN_GLOSS_CHOICES]
                          if not text[:1].isupper() and back_match(key, text, back) >= 1), "")
        else:
            gloss = clean_en(key, lists, back)
        if gloss:
            result[key] = gloss
    for key, target in redirects.items():
        if key not in result and target in result:
            result[key] = result[target]
    return result


def fill_from_english(result: dict[str, str], back: dict[str, str], weights: dict[str, dict[str, int]],
                      min_weight: int, engine: dict[str, dict[str, int]] | None = None, engine_min: int = 1000) -> int:
    """CC-CEDICT 没收、但 Rime 词库里常用的词（很多、博客、不同的）：用 ECDICT 反查，找中文释义里
    有一条正好是这个词的英文词，取最常用的（单词优先于词组）。返回补了多少条。"""
    wanted = {word for source, floor in ((weights, min_weight), (engine or {}, engine_min))
              for word, readings in source.items()
              if word not in result and max(readings.values(), default=0) >= floor
              and any("一" <= ch <= "鿿" for ch in word)}
    found: dict[str, tuple[tuple[int, int], str]] = {}
    for english, translation in back.items():
        # 最多四个词：再长多是俗语说明（刺死 → smite under the fifth rib）。
        if not re.fullmatch(r"[a-z]+(?:[' ][a-z]+){0,3}", english) or EN_PROFANITY.search(english):
            continue
        rank = en_rank(english)
        if rank > EN_ALT_MAX_RANK:
            continue
        score = (1 if " " in english else 0, rank)
        for line in translation.replace("\\n", "\n").split("\n"):
            if re.search(r"\[[^\]]+\]", line):
                continue
            # 只认每个词性的前两个中文释义：排在后面的多是引申（enough 的 “很多”）。
            for piece in ZH_SPLIT.split(POS_PREFIX.sub("", line.strip()))[:2]:
                piece = PARENS.sub("", piece).strip(" .。")
                if piece in wanted and (piece not in found or score < found[piece][0]):
                    found[piece] = (score, english)
    for word, (_, english) in found.items():
        result[word] = re.sub(r"\bi\b", "I", ORIGINAL_CASE.get(english, english))  # ECDICT 的键是小写（i should think、Rodriguez）
    return len(found)


# 拼日常短语时可单独用的字：意思固定、放进短语里不会错（宁少勿多）。其余单字多义，拼进去会出错。
PHRASE_SAFE_CHARS = {"我": "I", "你": "you", "他": "he", "她": "she", "也": "also", "不": "not", "很": "very",
                     "都": "all", "和": "and", "又": "again", "再": "again", "太": "too", "最": "most", "更": "more"}
PHRASE = "+"   # 释义前缀：逐词拼出的日常短语英文，只做显示参考，App 不拿它上屏


def add_everyday_phrases(entries: dict[str, str], engine: dict[str, dict[str, int]], min_weight: int) -> int:
    """输入法词库里常见、但没有整词英文的 2~6 字短语（我估计、聊天记录、这个周末）：从左往右取两字以上的词典词，
    单字只用 PHRASE_SAFE_CHARS，逐词拼出英文，加 PHRASE 前缀存进词典。按词库权重挑，日常说法优先，
    生僻或说不通的组合（对付一口）不收；有一块拼不上、或拼出连续重复词（how how）就不收。"""
    added = 0
    for word, readings in engine.items():
        if (word in entries or not 2 <= len(word) <= 6 or max(readings.values(), default=0) < min_weight
                or not all("\u4e00" <= ch <= "\u9fff" for ch in word)):
            continue
        parts, start = [], 0
        while start < len(word):
            for length in range(min(6, len(word) - start), 1, -1):
                if length == len(word):
                    continue
                gloss = entries.get(word[start:start + length])
                if gloss and gloss[0] not in (APPROX, PHRASE) and not gloss.endswith("…"):
                    parts.append(gloss)
                    start += length
                    break
            else:
                if word[start] not in PHRASE_SAFE_CHARS:
                    break
                parts.append(PHRASE_SAFE_CHARS[word[start]])
                start += 1
        if start < len(word) or len(parts) < 2:
            continue
        tokens = " ".join(parts).split()
        if any(a.lower() == b.lower() for a, b in zip(tokens, tokens[1:])):
            continue
        entries[word] = PHRASE + " ".join(parts)
        added += 1
    return added


def load_curated(path: str) -> dict[str, str]:
    """人工表：词<TAB>英文；只有词没有英文表示不显示释义（值为空串）。"""
    curated: dict[str, str] = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#") or not line.strip():
                continue
            word, _, gloss = line.rstrip("\n").partition("\t")
            curated.setdefault(word, gloss.strip())
    return curated


def apply_single_policy(entries: dict[str, str], curated: dict[str, str], engine: dict[str, dict[str, int]],
                        max_rank: int) -> int:
    """单字只给有用的释义：看过的字按人工表；没看过的字只留常用字（rime_ice 单字权重前 max_rank）里
    英文能正好译回本字的（没有 APPROX 前缀）。生僻字和不可靠的单字释义一律不显示。返回去掉的条数。"""
    singles = sorted((w for w in engine if len(w) == 1 and "一" <= w <= "鿿"),
                     key=lambda w: -max(engine[w].values()))
    common = set(singles[:max_rank])
    removed = 0
    for key in [k for k in entries if len(k) == 1 and "㐀" <= k <= "鿿"]:
        if key in curated:
            continue
        if key not in common or entries[key].startswith(APPROX):
            del entries[key]
            removed += 1
    for key, gloss in curated.items():
        if gloss:
            entries[key] = gloss
        elif entries.pop(key, None) is not None:
            removed += 1
    return removed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ecdict", required=True)
    parser.add_argument("--cedict", required=True)
    parser.add_argument("--rime-dict", action="append", required=True,
                        help="Rime 拼音词库（pinyin_simp.dict.yaml 等），可重复；用来筛常用中文词")
    parser.add_argument("--min-weight", type=int, default=500,
                        help="多字中文词在 Rime 词库里的最低权重")
    parser.add_argument("--engine-dict", action="append", default=[],
                        help="输入法实际用的 rime_ice 词库（cn_dicts/*.dict.yaml），可重复；补收 pinyin_simp 里没有的常用词")
    parser.add_argument("--word-by-word-phrases", action="store_true",
                        help="同时收逐词拼出的日常短语（v5 的做法，读不通，默认不收）")
    parser.add_argument("--phrase-min-weight", type=int, default=20000,
                        help="日常短语在 rime_ice 词库里的最低权重")
    parser.add_argument("--mt-phrases", help="电脑上离线机器翻译并筛过的常见短语（短语	英文	把握度），见 mt_filter；"
                        "替换同一短语的逐词版")
    parser.add_argument("--curated-singles", help="单字人工表（curated_singles.tsv），需同时给 --engine-dict")
    parser.add_argument("--single-max-rank", type=int, default=3500,
                        help="人工表没看过的单字，只在 rime_ice 单字权重前这么多个里才显示")
    parser.add_argument("--curated-phrases", help="多字词人工表（curated_phrases.tsv），优先于自动释义")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    entries, back = load_ecdict(args.ecdict)
    english = len(entries)
    weights = load_rime_weights(args.rime_dict)
    engine = load_rime_weights(args.engine_dict) if args.engine_dict else None
    chinese = load_cedict(args.cedict, back, weights, args.min_weight, engine)
    filled = fill_from_english(chinese, back, weights, args.min_weight, engine)
    print(f"filled_from_english={filled}", file=sys.stderr)
    for key, gloss in chinese.items():
        entries.setdefault(key, gloss)
    if args.mt_phrases:
        # 机器翻译的短语读起来通顺（很有道理 → that makes sense），优先于逐词拼；同样只显示、不上屏。
        translated = 0
        with open(args.mt_phrases, encoding="utf-8") as handle:
            for line in handle:
                word, translation = line.rstrip("\n").split("\t")[:2]
                if translation and (word not in entries or entries[word].startswith(PHRASE)):
                    entries[word] = PHRASE + translation
                    translated += 1
        print(f"mt_phrases={translated}", file=sys.stderr)
    if engine and args.word_by_word_phrases:
        # v5 的逐词拼短语读不通（很有道理 → very make sense，你怎么知道 → you how? know），v7 起默认不收。
        print(f"everyday_phrases={add_everyday_phrases(entries, engine, args.phrase_min_weight)}", file=sys.stderr)
    if args.curated_singles and engine:
        removed = apply_single_policy(entries, load_curated(args.curated_singles), engine, args.single_max_rank)
        print(f"singles_removed={removed}", file=sys.stderr)
    if args.curated_phrases:
        curated = load_curated(args.curated_phrases)
        for key, gloss in curated.items():
            if gloss:
                entries[key] = gloss
            else:
                entries.pop(key, None)
        print(f"curated_phrases={len(curated)}", file=sys.stderr)
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
