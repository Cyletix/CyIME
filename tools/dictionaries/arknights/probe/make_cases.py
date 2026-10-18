import sys

TERMS = """无精二 四星队 精一1级 精二1级 精一满级 解手流 无A组 孤岛流 自闭忍宗 绝食组 萝卜组 首杀解 最少人解
精一满级四星队 低练度 潜能模拟器 专三 银灰下棋 特蕾西娅 斯卡蒂 精二90级 抄作业 险峻地势 隐秘行动 深海猎人队
虎狼丸 缠丸 白面鸮 安德切尔 史都华德 蛇屠箱 深海色 断罪者 历阵锐枪芬 源石虫 固源岩组 糖聚块 炽合金块""".split()

SENTENCES = [
    ("这关无精二四星队能过吗", "zhe guan wu jing er si xing dui neng guo ma"),
    ("我精一1级打不过", "wo jing yi yi ji da bu guo"),
    ("解手流只要三次操作", "jie shou liu zhi yao san ci cao zuo"),
    ("有没有精一满级四星队的作业", "you mei you jing yi man ji si xing dui de zuo ye"),
    ("孤岛流太难了", "gu dao liu tai nan le"),
    ("首杀解出来了", "shou sha jie chu lai le"),
    ("斯卡蒂专三了", "si ka di zhuan san le"),
    ("最少人解是三人", "zui shao ren jie shi san ren"),
    ("今天刷固源岩组", "jin tian shua gu yuan yan zu"),
    ("低练度也能抄作业", "di lian du ye neng chao zuo ye"),
]

FLY_FINAL = {"iu": "q", "ei": "w", "uan": "r", "ue": "t", "ve": "t", "un": "y", "uo": "o", "ie": "p",
             "ong": "s", "iong": "s", "ai": "d", "en": "f", "eng": "g", "ang": "h", "an": "j",
             "uai": "k", "ing": "k", "uang": "l", "iang": "l", "ou": "z", "ua": "x", "ia": "x",
             "ao": "c", "ui": "v", "v": "v", "in": "b", "iao": "n", "ian": "m"}
FLY_INITIAL = {"zh": "v", "ch": "i", "sh": "u"}
T9 = {c: d for d, letters in zip("23456789", ["abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"]) for c in letters}


def flypy(s):
    if s in ("a", "o", "e"):
        return s + s
    if s[0] in "aoe":
        return s if len(s) == 2 else s[0] + FLY_FINAL[s[1:]] if s[1:] in FLY_FINAL else s[0] + FLY_FINAL[s]
    for ini in ("zh", "ch", "sh"):
        if s.startswith(ini):
            rest = s[2:]
            return FLY_INITIAL[ini] + (rest if len(rest) == 1 else FLY_FINAL[rest])
    rest = s[1:]
    return s[0] + (rest if len(rest) == 1 else FLY_FINAL[rest])


def codes(dict_path):
    out = {}
    for line in open(dict_path, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) == 3:
            out[parts[0]] = parts[1]
    return out


def main():
    table = codes(sys.argv[1])
    items = [("term", t, table[t]) for t in TERMS] + [("sentence", s, p) for s, p in SENTENCES]
    rows = {"rime_ice": [], "double_pinyin_flypy": [], "t9_pinyin": []}
    for kind, text, pinyin in items:
        syl = pinyin.split()
        rows["rime_ice"].append((("".join(syl) if kind == "term" else "".join(syl)), text, kind))
        rows["double_pinyin_flypy"].append(("".join(flypy(s) for s in syl), text, kind))
        rows["t9_pinyin"].append(("".join(T9[c] for c in "".join(syl)), text, kind))
    with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as f:
        for schema, rs in rows.items():
            for keys, text, kind in rs:
                f.write(f"{schema}\t{keys}\t{text}\t{kind}\n")


main()
