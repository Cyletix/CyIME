"""生成中文滑行词表 app/src/main/assets/glide_pinyin.txt。

来源是仓库里的 rime-ice 词频（app/data/rime_ice_8105_weights.tsv.gz、rime_ice_base_weights.tsv.gz）：
同一拼音的各个词频相加，按总频率从高到低取前 LIMIT 个编码，再补上所有单音节，
保证词表外的词也能按音节逐个划出。每行一个编码，音节之间用空格分开（双拼按音节换码要用）。

用法：python -B tools/glide_lab/build_pinyin_lexicon.py
"""
import collections
import gzip
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = ["app/data/rime_ice_8105_weights.tsv.gz", "app/data/rime_ice_base_weights.tsv.gz"]
OUTPUT = ROOT / "app/src/main/assets/glide_pinyin.txt"
LIMIT = 30000
MAX_SYLLABLES = 4
CODE = re.compile(r"^[a-z]+( [a-z]+)*$")


def main() -> None:
    weights: collections.Counter[str] = collections.Counter()
    for source in SOURCES:
        with gzip.open(ROOT / source, "rt", encoding="utf-8") as lines:
            for line in lines:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3 or not CODE.match(parts[1]):
                    continue
                if len(parts[1].split(" ")) > MAX_SYLLABLES:
                    continue
                weights[parts[1]] += int(parts[2])
    ranked = [code for code, _ in sorted(weights.items(), key=lambda item: (-item[1], item[0]))]
    chosen = ranked[:LIMIT]
    picked = set(chosen)
    chosen += [code for code in ranked[LIMIT:] if " " not in code and code not in picked]
    OUTPUT.write_text("\n".join(chosen) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(chosen)} codes -> {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
