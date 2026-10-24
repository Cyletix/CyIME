"""Build a bundled topic cell dictionary (programming, math, games ...) from a generated word list.

The list (dict.txt) has lines `词<TAB>pin yin`, `# 段名` headers and a final English section of one
word per line. Readings are checked against rime-ice: a word rime-ice already has takes its rime-ice
reading (polyphones such as 重 are fixed this way); otherwise every given syllable must be a reading
rime-ice lists for that character, else the word is read from rime-ice characters and words. Words
rime-ice already ranks at the dictionary weight or higher are left out, as in the Arknights builder.

  python build_topic_dict.py --rime-ice DIR --input dict.txt --id dota2 --title "Dota2" --output-dir DIR

Writes cell_builtin_<id>.dict.yaml and, when there are English words, cell_builtin_<id>.en.txt.
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "arknights"))
from build_arknights_dict import Readings, read_dict  # noqa: E402

WEIGHT = 600
HAN = re.compile(r"^[㐀-䶿一-鿿]{2,12}$")
LATIN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 '._&+-]{0,62}$")


def main():
    parser = argparse.ArgumentParser()
    for key in ("rime-ice", "input", "id", "title", "output-dir"):
        parser.add_argument(f"--{key}", required=True)
    args = parser.parse_args()
    readings = Readings(args.rime_ice)
    char_readings = {}
    for text, code, *_ in read_dict(Path(args.rime_ice) / "cn_dicts" / "8105.dict.yaml"):
        char_readings.setdefault(text, set()).add(code)
    entries, english, fixed, missing, known = {}, [], 0, [], 0
    for raw in Path(args.input).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if all(ord(c) < 128 for c in line):
            text = re.sub(" +", " ", line.split("\t")[0].strip())
            if LATIN.match(text) and re.search("[A-Za-z]", text) and \
                    len(re.sub("[^a-z0-9]", "", text.lower())) >= 2 and text not in english:
                english.append(text)
            continue
        fields = line.split("\t")
        text = fields[0].strip()
        if not HAN.match(text) or text in entries:
            continue
        if readings.known.get(text, 0) >= WEIGHT:
            known += 1
            continue
        given = fields[1].strip().lower().replace("ü", "v").split() if len(fields) > 1 else []
        if text in readings.words:
            code = readings.words[text][0]
        elif len(given) == len(text) and all(s in char_readings.get(c, ()) for c, s in zip(text, given)):
            code = " ".join(given)
        else:
            code = readings.code(text)
        if not code:
            missing.append(text)
            continue
        if code != " ".join(given):
            fixed += 1
        entries[text] = code
    out = Path(args.output_dir)
    name = f"cell_builtin_{args.id}"
    with open(out / f"{name}.dict.yaml", "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# CyIME {args.title}词库。由 tools/dictionaries/topics/build_topic_dict.py 生成，请勿手改。\n")
        f.write("# 词表由 CyletixCode 整理，读音按 rime-ice 校对。\n")
        f.write(f"---\nname: {name}\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        for text, code in entries.items():
            f.write(f"{text}\t{code}\t{WEIGHT}\n")
    english_file = out / f"{name}.en.txt"
    if english:
        english_file.write_text("\n".join(english) + "\n", encoding="utf-8", newline="\n")
    elif english_file.exists():
        english_file.unlink()
    print(f"{args.id}: chinese={len(entries)} english={len(english)} reading_fixed={fixed} "
          f"already_in_rime_ice={known} missing={len(missing)} {missing[:10]}")


if __name__ == "__main__":
    main()
