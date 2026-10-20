"""生成释义私人补充包：按用户自己的输入挑出的释义，叠在公开词典上（App 见 CandidateGlossary.privateFile）。

输入的人工表和输出都只放在电脑本地的私人目录，不提交、不上传；只由“装到我的设备”打包推到用户自己的设备。
人工表格式同 curated_*.tsv：词<TAB>英文，只有词表示不显示释义。输出只收与公开词典不同的条目，不显示的写 "-"。

用法：build_private_addon.py PUBLIC_GLOSS.tsv OUTPUT.tsv CURATED.tsv...
"""
import sys

HIDDEN = "-"


def load_table(path):
    table = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#") or "\t" not in line:
                continue
            key, value = line.rstrip("\n").split("\t", 1)
            table[key] = value.lstrip("~+")
    return table


def main():
    public_path, output, *curated_paths = sys.argv[1:]
    public = load_table(public_path)
    overlay = {}
    for path in curated_paths:
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                if line.startswith("#") or not line.strip():
                    continue
                word, _, gloss = line.rstrip("\n").partition("\t")
                gloss = gloss.strip()
                if gloss:
                    if public.get(word) != gloss:
                        overlay.setdefault(word, gloss)
                elif word in public:
                    overlay.setdefault(word, HIDDEN)
    rows = sorted(overlay.items(), key=lambda item: item[0].encode("utf-8"))
    with open(output, "w", encoding="utf-8", newline="\n") as out:
        out.write("# CyIME 候选释义私人补充包：只装在用户自己的设备上，请勿分发。\n")
        for word, gloss in rows:
            out.write(f"{word}\t{gloss}\n")
    print(f"overlay={len(rows)} hidden={sum(1 for _, g in rows if g == HIDDEN)}", file=sys.stderr)


if __name__ == "__main__":
    main()
