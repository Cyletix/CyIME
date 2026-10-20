"""Characters a filtered chat corpus never contains, and the dictionary weights that lost their evidence with them.

LCCC was cleaned with a word blacklist that dropped every line holding certain characters: in the first 8M lines
of LCCC-large 轮, 靠, 草, 抽, 爽, 喷, 灾, 税, 孕 and others occur zero times although their share of upstream
rime-ice weight predicts thousands. chat_base_weights.py read those zeros as "never said in chat" and pushed
every word holding them down (轮 197302 → 444, behind 囵; 一轮, 靠谱, 抽烟, 草莓 likewise). A word containing such
a character has no chat evidence either way, so it gets the move a typical entry got from the blend: its upstream
weight times the median blended/upstream factor of the entries the corpus does cover (only ever raised).

Usage: corpus_holes.py CHAR_COUNTS.tsv UPSTREAM_ZIP GENERATED_RIME_ICE_DIR OUT_DIR [--expected 30]
CHAR_COUNTS.tsv: character<TAB>occurrences in the corpus. Writes OUT_DIR/holes.txt and, for base and 8105,
OUT_DIR/<dict>.tsv.gz (text, code, weight: the generated weights with hole entries re-estimated).
"""
import argparse
import gzip
import io
import zipfile
from collections import defaultdict
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("counts", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("generated", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--expected", type=float, default=30.0,
                        help="a character missing from the corpus is a hole when upstream predicts at least this many")
    parser.add_argument("--ratio", type=float, help="fixed factor instead of the median blend factor")
    parser.add_argument("--quantile", type=float, default=0.5, help="which blend factor counts as typical")
    parser.add_argument("--keep-single", type=Path,
                        help="characters (one per line) whose single-character entries keep their weight: those the "
                             "filter removed for being profane or sexual; words holding them are still re-estimated")
    args = parser.parse_args()
    occurrences = {}
    for line in args.counts.open(encoding="utf-8"):
        char, n = line.rstrip("\n").split("\t")
        occurrences[char] = int(n)
    with zipfile.ZipFile(args.upstream) as zf:
        def upstream(name):
            path = next(n for n in zf.namelist() if n.endswith(f"cn_dicts/{name}.dict.yaml"))
            return {(t, c): w for t, c, w in rows(io.TextIOWrapper(zf.open(path), encoding="utf-8"))}
        tables = {name: upstream(name) for name in ("8105", "base")}
    per_char = defaultdict(int)
    for (text, _), weight in tables["8105"].items():
        if len(text) == 1:
            per_char[text] += weight
    total_up = sum(per_char.values())
    total_seen = sum(occurrences.get(ch, 0) for ch in per_char)
    holes = sorted((ch for ch, w in per_char.items()
                    if occurrences.get(ch, 0) == 0 and w / total_up * total_seen >= args.expected),
                   key=lambda ch: -per_char[ch])
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "holes.txt").write_text("".join(f"{ch}\n" for ch in holes), encoding="utf-8")
    hole_set = set(holes)
    keep_single = set()
    if args.keep_single:
        keep_single = {line.strip() for line in args.keep_single.open(encoding="utf-8")
                       if line.strip() and not line.startswith("#")}
    for name, up in tables.items():
        current = list(rows((args.generated / "cn_dicts" / f"{name}.dict.yaml").open(encoding="utf-8")))
        # The blend moves a typical entry by this factor; a hole entry gets the same move from its upstream weight.
        ratios = sorted(w / up[(t, c)] for t, c, w in current
                        if (t, c) in up and up[(t, c)] > 0 and not hole_set.intersection(t))
        typical = ratios[int(len(ratios) * args.quantile)] if args.ratio is None else args.ratio
        restored = 0
        with gzip.open(args.out / f"{name}.tsv.gz", "wt", encoding="utf-8", newline="\n") as out:
            for text, code, weight in current:
                if text in keep_single:
                    pass
                elif hole_set.intersection(text) and (text, code) in up and round(up[(text, code)] * typical) > weight:
                    weight = round(up[(text, code)] * typical)
                    restored += 1
                out.write(f"{text}\t{code}\t{weight}\n")
        print(f"{name}: typical blend factor {typical:.3f}, {restored} hole entries raised")
    print(f"{len(holes)} hole characters: {''.join(holes[:80])}")


if __name__ == "__main__":
    main()
