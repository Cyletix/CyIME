"""生成手写候选用的汉字读音表（带声调），候选注释显示拼音时用。

    python tools/handwriting_training/build_hanzi_pinyin.py

读 mozillazg/pinyin-data（MIT，固定在下面的版本）的 pinyin.txt 和 kHanyuPinlu.txt，
写 app/src/main/assets/handwriting/hanzi_pinyin.tsv：每行“字<TAB>读音1/读音2”；基本平面以外只收
hanzi_frequency.tsv 里有的字。
读音顺序：《现代汉语频率词典》（kHanyuPinlu）里有的字按它的使用频率排，只用它收的读音；其余按 pinyin.txt。
多音字最多留三个读音；儿化的 r（儿）和不在首位的弱读轻声（女 nü、和 he）不列。
"""
import pathlib
import unicodedata
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
FREQUENCY = ROOT / "app/src/main/assets/handwriting/hanzi_frequency.tsv"
OUT = ROOT / "app/src/main/assets/handwriting/hanzi_pinyin.tsv"
REVISION = "9d05c9e29cb6a7e5c79f2f5279f5c1014246a19a"
SOURCE = f"https://raw.githubusercontent.com/mozillazg/pinyin-data/{REVISION}/"
MAX_READINGS = 3


def fetch(name: str) -> str:
    with urllib.request.urlopen(SOURCE + name, timeout=60) as response:
        return response.read().decode("utf-8")


def parse(text: str) -> dict[str, list[str]]:
    table = {}
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line.startswith("U+"):
            continue
        code, readings = line.split(":", 1)
        char = chr(int(code[2:], 16))
        table[char] = [r.strip() for r in readings.split(",") if r.strip()]
    return table


TONE_MARKS = {"\u0304", "\u0301", "\u030c", "\u0300"}


def base(reading: str) -> str:
    """去掉声调符号（保留 ü）。轻声读音的 base 就是它自己。"""
    return unicodedata.normalize("NFC", "".join(c for c in unicodedata.normalize("NFD", reading) if c not in TONE_MARKS))


def main() -> None:
    full = parse(fetch("pinyin.txt"))
    common = parse(fetch("kHanyuPinlu.txt"))
    # 基本平面以外只收手写常用度表里有的字（𫖯 这类规范字），其余扩展区字手写也写不出来。
    extra = {line.split("\t", 1)[0] for line in FREQUENCY.read_text(encoding="utf-8").splitlines()}
    rows = []
    for char in sorted(full):
        if ord(char) > 0xFFFF and char not in extra:
            continue
        readings = common.get(char) or full[char]
        # 儿化的 r 不是独立读音。轻声在首位照列（吗 ma、们 men、得 de 主要就读轻声）；不在首位、
        # 同音节另有带调读音的轻声是词里的弱读（女 nü、和 he、和 huo），不列。
        readings = [r for r in readings if r != "r"]
        toned = {base(r) for r in full[char] + readings if base(r) != r}
        kept = [r for i, r in enumerate(readings) if i == 0 or base(r) != r or r not in toned]
        rows.append(f"{char}\t{'/'.join(dict.fromkeys(kept[:MAX_READINGS]))}")
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} 个字 → {OUT}（{OUT.stat().st_size // 1024} KB）")


if __name__ == "__main__":
    main()
