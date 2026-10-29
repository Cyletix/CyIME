"""从拼音词库的单字权重生成手写候选用的汉字常用度表。

    python tools/handwriting_training/build_hanzi_frequency.py

读 app/src/main/assets/rime/pinyin_simp.dict.yaml（同一个字的多个读音权重相加），
写 app/src/main/assets/handwriting/hanzi_frequency.tsv：每行“字<TAB>权重”，按权重从高到低。
"""
import collections
import pathlib
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/assets/rime/pinyin_simp.dict.yaml"
OUT = ROOT / "app/src/main/assets/handwriting/hanzi_frequency.tsv"


def main() -> None:
    weights = collections.Counter()
    for line in SRC.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 3 and len(parts[0]) == 1 and parts[2].strip().isdigit() \
                and "CJK" in unicodedata.name(parts[0], ""):
            weights[parts[0]] += int(parts[2])
    rows = [f"{ch}\t{w}" for ch, w in sorted(weights.items(), key=lambda kv: (-kv[1], kv[0])) if w > 0]
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} 个字 → {OUT}（{OUT.stat().st_size // 1024} KB）")


if __name__ == "__main__":
    main()
