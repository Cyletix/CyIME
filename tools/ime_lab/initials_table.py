"""Initials-only table for the 26-key schemas (CyIME-Windows d2d458c, docs/ANDROID_ALIGNMENT_20261021.md there).

Typed initials (hx, fdc, nsdd) are where spoken usage matters most, full pinyin is where written weights are safe.
This writes cyime_abbrev: the initials of every 2-4 character base.dict word, weight = max(upstream weight, chat
blend), so a written word chat text never uses (房地产, 上市公司) keeps its upstream place while spoken words
(好像, 知道了) can rise above it. The chat blend is the weight base.dict gets in the build (the Android blend of
upstream and LCCC counts, chat_base_weights.py), read from the generated rime_ice directory. Initials that spell an
English word of three or more letters (ppt, jpg, why) are left to the English table.

Usage: python initials_table.py GENERATED_RIME_ICE_DIR UPSTREAM_ZIP OUT_DIR [--upstream-weights OUT.tsv.gz]
Writes OUT_DIR/cyime_abbrev.dict.yaml and cyime_abbrev.schema.yaml; --upstream-weights also writes the upstream
base weights as a text/code/weight gzip TSV (lexicon_regression CYIME_BASE_WEIGHTS) for the variant where full
pinyin goes back to them.
"""
import argparse
import gzip
import io
import zipfile
from pathlib import Path


def rows(lines):
    body = False
    for line in lines:
        if line.strip() == "...":
            body = True
            continue
        if not body or line.startswith("#"):
            continue
        fields = line.rstrip("\n").split("\t")
        if len(fields) >= 3 and fields[2].strip().isdigit():
            yield fields[0], fields[1], int(fields[2])
        elif len(fields) >= 2:
            yield fields[0], fields[1], None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("generated", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--upstream-weights", type=Path)
    parser.add_argument("--min-chars", type=int, default=2)
    parser.add_argument("--max-chars", type=int, default=4)
    args = parser.parse_args()

    current = {(t, c): w for t, c, w in rows((args.generated / "cn_dicts/base.dict.yaml").open(encoding="utf-8"))
               if w is not None}
    with zipfile.ZipFile(args.upstream) as zf:
        name = next(n for n in zf.namelist() if n.endswith("cn_dicts/base.dict.yaml"))
        upstream = {(t, c): w for t, c, w in rows(io.TextIOWrapper(zf.open(name), encoding="utf-8")) if w is not None}
    english = set()
    for name in ("en.dict.yaml", "en_ext.dict.yaml"):
        for text, _, _ in rows((args.generated / "en_dicts" / name).open(encoding="utf-8")):
            word = text.lower()
            if len(word) >= 3 and word.isascii() and word.isalpha():
                english.add(word)

    table = {}
    for (text, code), weight in current.items():
        syllables = code.split()
        if not args.min_chars <= len(syllables) <= args.max_chars or len(text) != len(syllables):
            continue
        initials = "".join(s[0] for s in syllables)
        if initials in english:
            continue
        value = max(weight, upstream.get((text, code), 0))
        table[(text, initials)] = max(table.get((text, initials), 0), value)
    args.out.mkdir(parents=True, exist_ok=True)
    with (args.out / "cyime_abbrev.dict.yaml").open("w", encoding="utf-8", newline="\n") as f:
        f.write("# Rime dictionary\n# encoding: utf-8\n# Initials of 2-4 character base words (tools/ime_lab/initials_table.py)\n"
                "---\nname: cyime_abbrev\nversion: \"2026.10.21\"\nsort: by_weight\n...\n")
        for (text, initials), weight in sorted(table.items(), key=lambda kv: (kv[0][1], -kv[1])):
            f.write(f"{text}\t{initials}\t{weight}\n")
    (args.out / "cyime_abbrev.schema.yaml").write_text(
        "# Compiles cyime_abbrev for table_translator@abbrev in the 26-key schemas.\n"
        "schema:\n  schema_id: cyime_abbrev\n  name: CyIME initials\n  version: '2026.10.21'\n"
        "engine:\n  processors: [speller, selector, express_editor]\n  segmentors: [abc_segmentor]\n"
        "  translators: [table_translator]\nspeller:\n  alphabet: zyxwvutsrqponmlkjihgfedcba\n"
        "translator:\n  dictionary: cyime_abbrev\n  enable_completion: false\n  enable_sentence: false\n"
        "  enable_user_dict: false\n", encoding="utf-8")
    if args.upstream_weights:
        with gzip.open(args.upstream_weights, "wt", encoding="utf-8") as f:
            for (text, code), weight in upstream.items():
                f.write(f"{text}\t{code}\t{weight}\n")
    raised = sum(1 for (t, c), w in current.items() if w > upstream.get((t, c), w))
    print(f"cyime_abbrev: {len(table)} rows; {len(english)} English spellings excluded; "
          f"{raised} base entries above upstream")


if __name__ == "__main__":
    main()
