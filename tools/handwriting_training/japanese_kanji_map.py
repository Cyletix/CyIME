"""日语手写的漢字表：内置汉字手写模型（简体字表）的类 → 日语字形，加日语常用度；以及模型认不出的日本字。

    python japanese_kanji_map.py --data D:/CyIME-Data/datasets/japanese-kanji \\
        --model D:/GitHub/CyIME/.gradle/bundled-model-cache/ochwpro --out app/src/main/assets/handwriting/japanese_kanji.json

数据：OpenCC 的简→繁（STCharacters）与日本新字体（JPShinjitaiCharacters，新字体→旧字），
KANJIDIC2 的常用 / 人名用漢字与报刊频度。输出：
  map:   简体字（模型的类）→ [日语字形…]（只收常用、人名用漢字，或有频度的字）
  freq:  日语字 → 频度名次（1 最常用；没有频度的常用漢字记 2600，人名用记 3000）
  extra: 模型字表里映射不到、又是常用漢字的日本字（込、働、峠…），走笔画模板比对
"""
from __future__ import annotations

import argparse
import gzip
import json
import pathlib
import re
import sys


def read_opencc(path: pathlib.Path) -> dict[str, list[str]]:
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        k, _, v = line.partition("\t")
        out[k] = v.split()
    return out


def read_kanjidic(path: pathlib.Path) -> dict[str, int]:
    """日语字 → 名次。常用漢字（grade 1–8）与人名用漢字（grade 9–10）全收。"""
    text = gzip.open(path, "rt", encoding="utf-8").read()
    rank = {}
    for body in re.findall(r"<character>(.*?)</character>", text, re.S):
        lit = re.search(r"<literal>(.*?)</literal>", body).group(1)
        grade = re.search(r"<grade>(\d+)</grade>", body)
        freq = re.search(r"<freq>(\d+)</freq>", body)
        g = int(grade.group(1)) if grade else 0
        if freq:
            rank[lit] = int(freq.group(1))
        elif 1 <= g <= 8:
            rank[lit] = 2600
        elif g >= 9:
            rank[lit] = 3000
    return rank


def build(data: pathlib.Path, model: pathlib.Path):
    st = read_opencc(data / "STCharacters.txt")
    shin = read_opencc(data / "JPShinjitaiCharacters.txt")  # 新字体 → 旧字
    old_to_new: dict[str, list[str]] = {}
    for new, olds in shin.items():
        for old in olds:
            old_to_new.setdefault(old, []).append(new)
    rank = read_kanjidic(data / "kanjidic2.xml.gz")
    chars = json.loads((model / "char_index.json").read_text(encoding="utf-8"))["chars"]

    mapping: dict[str, list[str]] = {}
    for c in chars:
        if len(c) != 1 or not ("\u4e00" <= c <= "\u9fff"):
            continue
        forms = []
        for t in [c] + st.get(c, []):          # 简体本身，以及它的繁体
            for j in [t] + old_to_new.get(t, []):  # 繁体本身，以及日本新字体
                if j in rank and j not in forms:
                    forms.append(j)
        # 简体字本身若不是日语字（如“图”），只留日语字形
        forms.sort(key=lambda j: rank[j])
        # 同一个字的旧字形（广 → 広 之外的 廣、气 → 気 之外的 氣）不和新字体抢候选位；
        # 真正不同的字（发 → 発、髪）都留下
        kept = []
        for j in forms:
            if any(j in shin.get(k, []) for k in kept):
                continue
            kept.append(j)
        forms = kept
        if forms:
            mapping[c] = forms
    reachable = {j for v in mapping.values() for j in v}
    # 旧字形（廣、龍…）写出来按新字体出候选即可，不另做模板
    old_forms = {o for k in reachable for o in shin.get(k, [])}
    extra = sorted((j for j, r in rank.items() if r <= 2600 and j not in reachable and j not in old_forms), key=lambda j: rank[j])
    used = reachable | set(extra)
    return {"map": mapping, "freq": {j: rank[j] for j in sorted(used)}, "extra": extra}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--data", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--out")
    args = ap.parse_args()
    data = build(pathlib.Path(args.data), pathlib.Path(args.model))
    print(f"模型类 → 日语字形 {len(data['map'])} 个，覆盖日语字 {len({j for v in data['map'].values() for j in v})}，"
          f"模型认不出的常用漢字 {len(data['extra'])} 个：{''.join(data['extra'][:60])}…")
    for probe in "図駅広込働気楽動学国":
        src = [s for s, v in data["map"].items() if probe in v]
        print(probe, "←", "".join(src) or "（笔画模板）" if probe in data["extra"] else "".join(src))
    if args.out:
        out = pathlib.Path(args.out)
        out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(f"写入 {out}（{out.stat().st_size // 1024} KB）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
