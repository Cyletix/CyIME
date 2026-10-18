"""Build the bundled Arknights cell dictionary (Rime dict.yaml, full-pinyin codes).

Inputs are read-only local snapshots; nothing is downloaded here:
  --rime-ice   a rime_ice user directory containing cn_dicts/ (readings + duplicate check)
  --operators  ArknightsResearch data/derived/<gamedata>/operators.json
  --community  ArknightsResearch data/community/community.sqlite (PRTS skills/enemies/summons)
  --gamedata   ArknightsGameData zh_CN/gamedata/excel (sub-professions, materials)
  --arkrec     directory with arkrec category-definitions.json and menu.json
  --garrison   activity_table.json with AUTOCHESS_SEASON (卫戍协议: modes, bonds, strategies, items)
  --aliases    directory with MIT-licensed operator nickname tables: Arkfans/ArknightsAlias data.json,
               gxy12345/arknights-plugin charProfile/nickname.yaml (as gxy.yaml),
               ahuai114514/astrbot_plugin_wordle2 data/operator_aliases.json (as wordle.json)
Curated terms come from core_terms.txt, community_terms.txt and slang.txt next to this script.

Weights: curated terms of three or more characters outrank same-code everyday words so they
appear first; two-character curated terms and generated proper nouns stay at a modest weight
and never displace an existing everyday word. Words rime_ice already ranks at least that high
are skipped; lower-ranked duplicates are lifted (the schemas' uniquifier hides the repeat).
"""
import argparse
import json
import re
import sqlite3
from pathlib import Path

HERE = Path(__file__).resolve().parent
HAN = re.compile(r"^[㐀-䶿一-鿿0-9Aa]{2,12}$")
DIGITS = dict(zip("0123456789", "ling yi er san si wu liu qi ba jiu".split()))
# Game readings that differ from rime-ice's most frequent reading (重装 is zhòng zhuāng, Defender).
READING_FIXES = {"重装": "zhong zhuang", "降防": "jiang fang", "降攻": "jiang gong", "降抗": "jiang kang",
                 "维什": "wei shi", "梅什": "mei shi", "卡子": "ka zi"}
# Names whose game reading is uncertain or commonly typed both ways get a second code line.
ALT_READINGS = {"裁度": "cai du", "柏喙": "bai hui", "刻俄柏": "ke e bai", "维什戴尔": "wei shen dai er"}
CORE_MIN, CORE_MAX, NOUN, SLANG = 1200, 20000, 600, 800


def read_dict(path):
    in_body = False
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not in_body:
                in_body = line == "..."
                continue
            if not line or line.startswith("#"):
                continue
            yield line.split("\t")


class Readings:
    def __init__(self, rime_dir):
        base = Path(rime_dir) / "cn_dicts"
        self.chars, self.words, self.known = {}, {}, {}
        for text, code, *rest in read_dict(base / "8105.dict.yaml"):
            weight = int(rest[0]) if rest else 0
            if weight >= self.chars.get(text, ("", -1))[1]:
                self.chars[text] = (code, weight)
        for name in ("base", "ext"):
            for row in read_dict(base / f"{name}.dict.yaml"):
                if len(row) < 2:
                    continue
                text, code = row[0], row[1]
                weight = int(row[2]) if len(row) > 2 else 0
                if weight >= self.words.get(text, ("", -1))[1]:
                    self.words[text] = (code, weight)
        for row in read_dict(base / "tencent.dict.yaml"):
            self.known[row[0]] = int(row[1]) if len(row) > 1 else 0
        self.by_code = {}
        for text, (code, weight) in self.words.items():
            if weight > self.by_code.get(code, 0):
                self.by_code[code] = weight
        self.known.update({k: v[1] for k, v in self.words.items()})
        for text, code in READING_FIXES.items():
            self.words[text] = (code, self.words.get(text, ("", 0))[1])

    def code(self, text):
        """Fewest-segment reading using rime_ice words, then single-character readings."""
        if text in self.words:
            return self.words[text][0]
        n = len(text)
        best = [None] * (n + 1)
        best[0] = (0, [])
        for i in range(n):
            if best[i] is None:
                continue
            for j in range(min(n, i + 6), i, -1):
                piece = text[i:j]
                if j - i == 1:
                    ch = piece
                    reading = DIGITS.get(ch) or ("a" if ch in "Aa" else None) or (self.chars.get(ch) or (None,))[0]
                else:
                    reading = (self.words.get(piece) or (None,))[0]
                if reading is None:
                    continue
                cand = (best[i][0] + 1, best[i][1] + [reading])
                if best[j] is None or cand[0] < best[j][0]:
                    best[j] = cand
        return " ".join(best[n][1]) if best[n] else None


def lines(path):
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        raw = raw.strip()
        if raw and not raw.startswith("#"):
            yield raw.split("\t")


def generated(args):
    names = {}

    def add(kind, value):
        value = (value or "").strip()
        value = value.replace("·", "")  # 凯尔希·思衡托 / 维娜·维多利亚 are typed without the dot
        if HAN.match(value) and not value.isdigit() and value not in names:
            names[value] = kind

    ops = json.loads(Path(args.operators).read_text(encoding="utf-8"))["operators"]
    for op in ops:
        add("operator", op.get("干员名称"))
    sub = json.loads((Path(args.gamedata) / "uniequip_table.json").read_text(encoding="utf-8"))["subProfDict"]
    for item in sub.values():
        if not item["subProfessionName"].startswith("预留"):
            add("class", item["subProfessionName"])
    db = sqlite3.connect(f"file:{args.community}?mode=ro", uri=True)
    for kind, name in db.execute("select kind, name from records where kind in ('skill','enemy','summon','operator')"):
        if kind == "skill" and len(name) > 6:
            continue
        add(kind, name)
    items = json.loads((Path(args.gamedata) / "item_table.json").read_text(encoding="utf-8"))["items"]
    for item in items.values():
        # "XX的信物" per operator is noise; the bare 信物 lives in community_terms.txt.
        if item["classifyType"] == "MATERIAL" and not item["name"].endswith("的信物"):
            add("material", item["name"])
    menu = json.loads((Path(args.arkrec) / "menu.json").read_text(encoding="utf-8"))

    def walk(node):
        add("event", node.get("episode"))
        add("zone", node.get("zone"))
        if len(node.get("cn_name") or "") <= 6:
            add("stage", node.get("cn_name"))
        for child in node.get("childNodes") or []:
            walk(child)
    walk(menu)
    activity = json.loads(Path(args.garrison).read_text(encoding="utf-8"))
    for season in activity["activity"]["AUTOCHESS_SEASON"].values():
        for mode in season["modeDataDict"].values():
            add("garrison", mode.get("name"))
        for bond in season["bondInfoDict"].values():
            add("garrison", bond.get("name"))
        for effect in season["effectInfoDataDict"].values():
            name = re.sub("[“”\"！!]", "", (effect.get("effectName") or "").split("·")[0])
            add("garrison", name)
    for item in json.loads((Path(args.arkrec) / "category-definitions.json").read_text(encoding="utf-8")):
        add("category", item.get("name"))
    return names


def aliases(directory):
    """Nicknames only (values), never the operator names they point to."""
    directory = Path(directory)
    found = []
    alias = json.loads((directory / "data.json").read_text(encoding="utf-8"))
    found += [key for key, target in alias.items() if key != target]
    for values in json.loads((directory / "wordle.json").read_text(encoding="utf-8")).values():
        found += values
    for line in (directory / "gxy.yaml").read_text(encoding="utf-8").splitlines():
        if line.startswith("  - "):
            found.append(line[4:].strip().strip("'\""))
    return [name for name in dict.fromkeys(found) if HAN.match(name) and len(name) <= 6 and not name.isdigit()]


def main():
    parser = argparse.ArgumentParser()
    for key in ("rime-ice", "operators", "community", "gamedata", "arkrec", "garrison", "aliases", "output"):
        parser.add_argument(f"--{key}", required=True)
    args = parser.parse_args()
    readings = Readings(args.rime_ice)
    entries, missing = {}, []

    def put(text, code, weight):
        if text in entries:
            return
        code = code or readings.code(text)
        if not code:
            missing.append(text)
            return
        entries[text] = (code, weight)

    def core_weight(text, code):
        competitor = readings.by_code.get(code or readings.code(text) or "", 0)
        # Two-character terms often share a code with everyday words (棘刺/几次); never displace those.
        boost = len(text) >= 3 and competitor <= CORE_MAX
        return max(CORE_MIN, competitor + 1) if boost else CORE_MIN

    for row in [*lines(HERE / "core_terms.txt"), *lines(HERE / "community_terms.txt")]:
        code = row[1] if len(row) > 1 else None
        put(row[0], code, core_weight(row[0], code))
    for row in lines(HERE / "slang.txt"):
        if readings.known.get(row[0], 0) < SLANG:
            put(row[0], row[1] if len(row) > 1 else None, SLANG)
    for name in aliases(args.aliases):
        if readings.known.get(name, 0) < SLANG:
            put(name, None, SLANG)
    counts = {}
    for text, kind in generated(args).items():
        # Operator names rank like curated terms (德克萨斯 over 得克萨斯); other nouns stay modest.
        weight = core_weight(text, None) if kind == "operator" else NOUN
        if readings.known.get(text, 0) >= weight:
            continue
        before = len(entries)
        put(text, None, weight)
        if len(entries) > before:
            counts[kind] = counts.get(kind, 0) + 1

    out = Path(args.output)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# CyIME 明日方舟词库：少人/极限圈说法、干员、技能、敌人、关卡与材料。\n")
        f.write("# 由 tools/dictionaries/arknights/build_arknights_dict.py 生成，请勿手改。\n")
        f.write("# 来源：wiki.arkrec.com 流派定义与关卡目录；PRTS（经 ArknightsResearch 社区库）；\n")
        f.write("# ArknightsGameData 游戏表；干员外号取自 Arkfans/ArknightsAlias、gxy12345/arknights-plugin、\n")
        f.write("# ahuai114514/astrbot_plugin_wordle2（均为 MIT 许可）；玩家聊天常用说法。读音取自 rime-ice。\n")
        f.write("---\nname: cell_builtin_arknights\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        for text, (code, weight) in entries.items():
            f.write(f"{text}\t{code}\t{weight}\n")
            if ALT_READINGS.get(text, code) != code:
                f.write(f"{text}\t{ALT_READINGS[text]}\t{weight}\n")
    print(f"entries={len(entries)} generated={counts} missing={len(missing)} {missing[:20]}")


if __name__ == "__main__":
    main()
