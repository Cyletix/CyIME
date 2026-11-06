#!/usr/bin/env python3
"""合并用户贡献的词库文件（App 里「词库 › 贡献」分享或上传的 cyime_dict_contribute_*.txt）。

脱敏（App 里已经做过一遍，这里按同样规则再做一遍，旧版或改过的文件也挡得住）：
- 只收 2～4 个汉字、拼音、1～999 次的行；只认格式版本 1，别的版本整份跳过（以后改格式在这里加分支）。
- 全由数字字组成的（一三八、两千、幺幺零）去掉：卡号、电话念出来的片段。
- 像人名的（姓或小老阿开头 2～3 字、复姓开头 3～4 字，规则和姓表读 App 的 DictContributionRules.kt）
  不在 --known 词库里就去掉。
- 至少 --min-contributors 个不同的人都用过才输出：只有一个人用的名字、地址片段不会进词库。
每人每个词的次数先封顶 --per-person-cap，免得一个人的次数压过所有人。同一个人交了好几份，只算日期最新的那份。

用法：
  python tools/dict_contribute/aggregate.py <文件或目录>... --out merged.tsv [--known 词库.dict.yaml ...]
--known 默认用仓库 app/src/main/assets/rime/ 下的全部 .dict.yaml。
输出每行：词<TAB>拼音<TAB>人数<TAB>次数合计<TAB>新词(词库里没有时为 new)；旁边写 <out>.report.txt 记各类去掉了多少。
"""
import argparse
import collections
import pathlib
import re
import sys

FORMAT = "cyime-dict-contribute"
VERSIONS = {"1"}
ROOT = pathlib.Path(__file__).resolve().parents[2]
RULES = ROOT / "app/src/main/java/com/kingzcheung/xime/settings/DictContributionRules.kt"
DEFAULT_KNOWN = ROOT / "app/src/main/assets/rime"
NUMBER_CHARS = set("零〇一二三四五六七八九十两百千万亿幺壹贰叁肆伍陆柒捌玖拾佰仟")
NICKNAME_PREFIXES = set("小老阿")
NAME = re.compile(r"cyime_dict_contribute_([0-9a-f]{16})_(\d{8})\.txt$")
HAN = re.compile(r"^[㐀-鿿\U00020000-\U0003ffff]{2,4}$")
PINYIN = re.compile(r"^([a-z]+( [a-z]+)*)?$")


def kotlin_string(name, text):
    """读 Kotlin 里 `const val NAME = "..." + "..."` 拼起来的字符串。"""
    match = re.search(rf'const val {name} = ((?:"[^"]*"\s*\+?\s*)+)', text)
    if not match:
        raise SystemExit(f"{RULES} 里找不到 {name}")
    return "".join(re.findall(r'"([^"]*)"', match.group(1)))


def name_rules():
    text = RULES.read_text(encoding="utf-8")
    return set(kotlin_string("SURNAMES", text)), set(kotlin_string("COMPOUND_SURNAMES", text).split())


def looks_like_name(word, surnames, compounds):
    if len(word) >= 3 and word[:2] in compounds:
        return True
    if len(word) > 3:
        return False
    return word[0] in surnames or (len(word) == 2 and word[0] in NICKNAME_PREFIXES)


def read(path):
    """返回 (contributor, date, rows)；不是贡献文件或版本不认识返回 None。"""
    if path.suffix == ".gz":
        import gzip
        lines = gzip.decompress(path.read_bytes()).decode("utf-8").splitlines()
    else:
        lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or not lines[0].startswith(f"# {FORMAT} ") or lines[0].split()[-1] not in VERSIONS:
        return None
    header = {}
    rows = []
    for line in lines[1:]:
        if line.startswith("# "):
            key, _, value = line[2:].partition("\t")
            header[key] = value
            continue
        cols = line.split("\t")
        if len(cols) != 3 or not HAN.match(cols[0]) or not PINYIN.match(cols[1]) or not cols[2].isdigit():
            continue  # 文件被改过或格式不对的行直接跳过
        rows.append((cols[0], cols[1], int(cols[2])))
    match = NAME.search(path.name)
    contributor = header.get("contributor") or (match.group(1) if match else path.stem)
    return contributor, (match.group(2) if match else ""), rows


def known_words(paths):
    words = set()
    files = []
    for path in map(pathlib.Path, paths):
        files += sorted(path.glob("*.dict.yaml")) if path.is_dir() else [path]
    for path in files:
        for line in path.read_text(encoding="utf-8").splitlines():
            word = line.split("\t", 1)[0]
            if word and not word.startswith("#"):
                words.add(word)
    return words


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("--out", required=True)
    parser.add_argument("--min-contributors", type=int, default=3)
    parser.add_argument("--per-person-cap", type=int, default=99)
    parser.add_argument("--known", nargs="*", default=[str(DEFAULT_KNOWN)],
                        help="现有词库 .dict.yaml 或目录：标出新词，像人名的词不在这里就去掉")
    args = parser.parse_args(argv)

    files = []
    for item in args.inputs:
        path = pathlib.Path(item)
        files += sorted(path.rglob("cyime_dict_contribute_*.txt")) + sorted(path.rglob("*.txt.gz")) \
            if path.is_dir() else [path]

    latest = {}
    for path in files:
        parsed = read(path)
        if parsed is None:
            print(f"跳过（不是贡献文件或版本不认识）：{path}", file=sys.stderr)
            continue
        contributor, date, rows = parsed
        if contributor not in latest or date >= latest[contributor][0]:
            latest[contributor] = (date, rows)

    known = known_words([k for k in args.known if pathlib.Path(k).exists()])
    if not known:
        print("没有可用的 --known 词库：像人名的词全部去掉", file=sys.stderr)
    surnames, compounds = name_rules()
    dropped = collections.Counter()
    people = collections.Counter()
    total = collections.Counter()
    for _, rows in latest.values():
        for word, pinyin, count in rows:
            if all(ch in NUMBER_CHARS for ch in word):
                dropped["数字"] += 1
                continue
            if looks_like_name(word, surnames, compounds) and word not in known:
                dropped["像人名"] += 1
                continue
            people[(word, pinyin)] += 1
            total[(word, pinyin)] += min(count, args.per_person_cap)

    kept = [key for key, n in people.items() if n >= args.min_contributors]
    dropped["用的人不够"] = len(people) - len(kept)
    kept.sort(key=lambda key: (-people[key], -total[key], key[0]))
    with open(args.out, "w", encoding="utf-8", newline="\n") as out:
        for word, pinyin in kept:
            flag = "new" if known and word not in known else ""
            out.write(f"{word}\t{pinyin}\t{people[(word, pinyin)]}\t{total[(word, pinyin)]}\t{flag}\n")
    summary = (f"{len(latest)} 人，{len(people)} 个词，至少 {args.min_contributors} 人用过的 {len(kept)} 个 → {args.out}\n"
               + "去掉：" + "，".join(f"{k} {v}" for k, v in dropped.items()) + "\n")
    pathlib.Path(args.out + ".report.txt").write_text(summary, encoding="utf-8")
    print(summary, end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
