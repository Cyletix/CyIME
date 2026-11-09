"""Rank bundled cell-dictionary words against rime-ice so they come up near the top without displacing
everyday first candidates. Rewrites the weight column of each cell_builtin_*.dict.yaml in place.

Competitors of a word = rime-ice entries with the same full-pinyin code and different text
(base, ext, tencent, others; the build's generated rime_ice directory has codes for all of them).
top1/top2 = the highest and second-highest competitor weights.

- 2 characters: no competitor -> unchanged. Otherwise max(weight, top2 + 1), kept below top1, so the word
  comes second after the everyday first candidate (颗粒 stays first for keli, 可莉 comes second).
  When top1 <= weight the word already leads and is left as is.
- 3+ characters: max(1200, top1 + 1), as build_arknights_dict.py ranks its core terms; when top1 > 20000 the
  word does not chase it and gets max(weight, 1200).
- Weights only go up (the Arknights builder's 1200/800/higher weights are kept), the result is deterministic
  and running it again changes nothing. The header version becomes "2" so installed copies redeploy.

Imported SCEL/TXT dictionaries (weight 100) are not touched. build_topic_dict.py calls rank() on the file it
writes; after regenerating the Arknights dictionary run this script on it as well.

  python rank_cell_dict.py --rime-ice app/build/generated/chinese-assets/rime_ice \
      app/src/main/assets/cell_dictionaries/cell_builtin_*.dict.yaml
"""
import argparse
import collections
from pathlib import Path

CORE_MIN, CORE_MAX = 1200, 20000
VERSION = "2"


def load_competitors(rime_ice):
    """code -> list of (weight, text), heaviest first."""
    by_code = collections.defaultdict(dict)
    for name in ("base", "ext", "tencent", "others"):
        path = Path(rime_ice) / "cn_dicts" / f"{name}.dict.yaml"
        if not path.exists():
            continue
        body = False
        for line in path.read_text(encoding="utf-8").splitlines():
            if not body:
                body = line == "..."
                continue
            if not line or line.startswith("#"):
                continue
            fields = line.split("\t")
            if len(fields) < 3 or not fields[2].strip().isdigit():
                continue
            text, code, weight = fields[0], fields[1], int(fields[2])
            if weight > by_code[code].get(text, -1):
                by_code[code][text] = weight
    return {code: sorted(((w, t) for t, w in texts.items()), reverse=True) for code, texts in by_code.items()}


def ranked_weight(text, code, weight, competitors):
    rivals = [w for w, t in competitors.get(code, ()) if t != text]
    top1 = rivals[0] if rivals else 0
    top2 = rivals[1] if len(rivals) > 1 else 0
    if len(text) <= 2:
        if not rivals or top1 <= weight:
            return weight
        return max(weight, min(top2 + 1, top1 - 1))
    if top1 > CORE_MAX:
        return max(weight, CORE_MIN)
    return max(weight, CORE_MIN, top1 + 1)


def rank(path, competitors):
    """Rewrite one dictionary; returns (words, raised)."""
    lines = Path(path).read_text(encoding="utf-8").split("\n")
    body, words, raised = False, 0, 0
    for i, line in enumerate(lines):
        if not body:
            if line == "...":
                body = True
            elif line.startswith("version:"):
                lines[i] = f'version: "{VERSION}"'
            continue
        fields = line.split("\t")
        if len(fields) < 3 or line.startswith("#") or not fields[2].strip().isdigit():
            continue
        words += 1
        old = int(fields[2])
        new = ranked_weight(fields[0], fields[1], old, competitors)
        if new != old:
            raised += 1
            fields[2] = str(new)
            lines[i] = "\t".join(fields)
    Path(path).write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return words, raised


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--rime-ice", required=True, help="rime_ice directory with cn_dicts/")
    parser.add_argument("dicts", nargs="+")
    args = parser.parse_args()
    competitors = load_competitors(args.rime_ice)
    for path in args.dicts:
        words, raised = rank(path, competitors)
        print(f"{Path(path).name}: words={words} raised={raised}")


if __name__ == "__main__":
    main()
