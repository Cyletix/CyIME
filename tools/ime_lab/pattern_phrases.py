"""Collect common spoken sentence patterns from the chat corpus as a typing test set (script statistics only).

Short clauses (3-8 characters, split at punctuation) are matched against 14 grammatical patterns in order;
the first matching pattern wins. The most frequent clauses per pattern are kept and given readings with
pypinyin (run with an interpreter that has it, e.g. D:/CyIME-Data/lexicon-generation/.venv). The lines are
taken after --skip so they never overlap the part of the corpus used to estimate weights; the set is for
scoring only.

Usage: python pattern_phrases.py CORPUS.txt OUT_DIR [--skip 8000000] [--lines 8000000] [--per 100]
Writes OUT_DIR/patterns.tsv (lexicon_regression rows, 26-key full pinyin, run with CYIME_EXTRA_CASES),
OUT_DIR/patterns.txt + patterns.pinyin.txt (t9_replay prepare input) and OUT_DIR/patterns.groups.tsv
(line number -> pattern id, for scoring the nine-key replay).
"""
import argparse
import re
from collections import Counter
from itertools import islice
from pathlib import Path

from pypinyin import Style, lazy_pinyin

C = "[一-鿿]"
MEASURE = "个把顿场次件杯瓶碗张本部下趟觉块份"
PATTERNS = [
    ("01 动词了量词名词", rf"^{C}{{1,2}}了一?[{MEASURE}]{C}{{0,3}}$"),
    ("02 动词个名词", rf"^(?![这那一几两三每各哪某整半])(?!.了)({C}{{1,2}})个{C}{{1,2}}$"),
    ("03 动词重叠", rf"^({C})(一|了)?\1(看|吧|呗)?$"),
    ("04 动词补语", rf"^{C}{{1,2}}(不|得)(到|懂|了|完|动|开|起|下|见|着|住|清楚|明白|出来|过来)(啊|吧|了|呢)?$|^{C}{{1,2}}(完|好|错|懂|到)了$"),
    ("05 动词趋向", rf"^{C}{{1,2}}(过来|过去|回来|回去|起来|下来|下去|出来|出去|进来|进去|上来|上去)(了|吧|啊|呗)?$"),
    ("06 着和过", rf"^{C}{{1,3}}(着|过)(呢|吗|了|办|啊)?$"),
    ("07 句尾语气词", rf"^{C}{{1,4}}(吧|啊|呀|吗|呢|嘛|呗|啦|哦|哈)$"),
    ("08 程度", rf"^(太|好|特别|有点|挺|超级|非常|真|超|很)({C}{{1,3}})(了|啊|的|呀)?$"),
    ("09 否定和反问", rf"^({C})不\1|^({C}{{2}})不\2|^(没|不){C}{{1,3}}(吧|吗|啊|呢)$"),
    ("10 时间地点", rf"^(等会儿?|一会儿|待会儿?|明天|后天|今天|今晚|晚上|马上|在家|在路上|到家|到楼下){C}{{0,4}}$"),
    ("11 把字句被字句", rf"^(把|被){C}{{2,6}}$"),
    ("12 一加动量", rf"^{C}{{1,2}}一(下|声|趟|次|会儿|眼|遍)(吧|啊|呗|呀)?$"),
    ("13 数量名词", rf"^(两|三|四|五|几|多少|一百|十|二十)(个|块|点|天|次|分钟|小时|人|斤|岁)({C}{{0,2}})$"),
    ("14 称呼加动作", rf"^(妈|爸|老板|师傅|老师|哥|姐|宝贝|亲爱的|老婆|老公){C}{{2,5}}$"),
]
SPLIT = re.compile(r"[^一-鿿]+")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus")
    parser.add_argument("out")
    parser.add_argument("--skip", type=int, default=8_000_000)
    parser.add_argument("--lines", type=int, default=8_000_000)
    parser.add_argument("--per", type=int, default=100)
    args = parser.parse_args()
    compiled = [(name, re.compile(rx)) for name, rx in PATTERNS]
    counts = {name: Counter() for name, _ in PATTERNS}
    with open(args.corpus, encoding="utf-8", errors="ignore") as corpus:
        for line in islice(corpus, args.skip, args.skip + args.lines):
            for clause in SPLIT.split(line):
                if 3 <= len(clause) <= 8:
                    for name, rx in compiled:
                        if rx.search(clause):
                            counts[name][clause] += 1
                            break
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, _ in PATTERNS:
        for clause, _ in counts[name].most_common(args.per):
            rows.append((name, clause, [p.replace("ü", "v") for p in lazy_pinyin(clause, style=Style.NORMAL)]))
    with (out / "patterns.tsv").open("w", encoding="utf-8", newline="\n") as tsv, \
            (out / "patterns.txt").open("w", encoding="utf-8", newline="\n") as txt, \
            (out / "patterns.pinyin.txt").open("w", encoding="utf-8", newline="\n") as pin, \
            (out / "patterns.groups.tsv").open("w", encoding="utf-8", newline="\n") as groups:
        tsv.write("id\tkeyboard\tinput\texpect\ttop_k\tsource\tdate\tnote\n")
        for i, (name, clause, py) in enumerate(rows, 1):
            tsv.write(f"pattern-{i:04d}\t26\t{''.join(py)}\t{clause}\t1\tpattern\t2026-10-20\t{name}\n")
            txt.write(clause + "\n")
            pin.write(" ".join(py) + "\n")
            groups.write(f"{i}\t{name}\t{clause}\n")
    for name, _ in PATTERNS:
        print(name, len(counts[name]), "distinct; e.g.", " ".join(c for c, _ in counts[name].most_common(6)))


if __name__ == "__main__":
    main()
