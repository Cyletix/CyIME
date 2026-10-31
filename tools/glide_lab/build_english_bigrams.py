"""生成英文滑行的双词表 app/src/main/assets/english_bigrams.txt。

滑行连着划两个词时，用它给“前一个词 + 这个词”整对打分（i 划到 m、n 之间再划 done → im done，上屏 I'm done）。
词与 english.txt 一样去掉撇号、全小写（i'm → im、don't → dont），只收两个词都在滑行词表里的词对
（english.txt 加上插进去的缩写词，见同时生成的 english_contractions.txt）。

来源（原始文件不进仓库，路径用参数给）：
- Norvig《Natural Language Corpus Data》count_1w.txt / count_2w.txt（Google 网页语料，MIT），
  https://github.com/norvig/pytudes/tree/main/data/text
- Cornell Movie-Dialogs Corpus 的 movie_lines.txt（电影对白，口语、缩写多），
  https://www.cs.cornell.edu/~cristian/Cornell_Movie-Dialogs_Corpus.html

前面每行「词 分数」是单词概率 -ln P(词) × 10（对白、网页词频混合）；后面每行「前词 后词 分数」，分数是 -ln P(后|前) × 10，
P 为对白与网页两份条件概率按 0.6 / 0.3 混合，再加 0.1 的后词单词概率；只存比没见过的估算至少高 1 个 nat 的词对，
表里没有的词对由应用按 0.1 × 单词概率估算（见 EnglishBigrams.kt）。

用法：python -B tools/glide_lab/build_english_bigrams.py --norvig <目录> --cornell <movie_lines.txt> [--holdout <测试句输出>]
--holdout 时按台词编号留出十分之一不参与统计，写成测试句，供合成轨迹回放评估。
"""
import argparse
import collections
import math
import pathlib
import re
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
VOCAB = ROOT / "app/src/main/assets/english.txt"
OUTPUT = ROOT / "app/src/main/assets/english_bigrams.txt"
CONTRACTIONS_OUTPUT = ROOT / "app/src/main/assets/english_contractions.txt"
# 滑行能划出的缩写（只收真正的缩写，不收所有格）。词表里没有去撇号写法的，按对白里的频率插进词表。
CONTRACTIONS = """i'm i've i'll i'd you're you've you'll you'd he's he'll he'd she's she'll she'd it's it'll
we're we've we'll we'd they're they've they'll they'd that's that'll what's what're what'd who's who'd
where's where'd how's how'd there's here's let's don't doesn't didn't can't couldn't won't wouldn't
shouldn't isn't aren't wasn't weren't haven't hasn't hadn't ain't c'mon y'know o'clock ma'am""".split()
# 去撇号写法本身也是常用词（were、well、ill）时：缩写在对白里至少是原词 5 倍才直接换，至少 1/5 才排进候选。
REPLACE_RATIO, OFFER_RATIO = 5, 0.2
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")
CORNELL_WEIGHT, WEB_WEIGHT, UNIGRAM_WEIGHT = 0.6, 0.3, 0.1
MIN_CORNELL = 2
CORNELL_UNIGRAM, WEB_UNIGRAM, RANK_UNIGRAM = 0.6, 0.399, 0.001
MIN_GAIN = 10  # 比没见过的估算（0.1 × 单词概率）至少高 1 个 nat 才存，差不多的不存


def tokens(line: str) -> list[str]:
    return [token.replace("'", "") for token in TOKEN.findall(line.lower().replace("’", "'"))]


def cornell_texts(path: pathlib.Path):
    with path.open(encoding="latin-1") as lines:
        for line in lines:
            parts = line.rstrip("\n").split(" +++$+++ ")
            if len(parts) >= 5:
                yield parts[0], parts[4]


def contraction_table(cornell: pathlib.Path, english: list[str]) -> list[tuple[str, str, object]]:
    """(去撇号写法, 缩写, 插入排名（english.txt 已有为 None）, 是否只排进候选)，不够常用的不收。"""
    plain: collections.Counter[str] = collections.Counter()
    forms: collections.Counter[str] = collections.Counter()
    for _, text in cornell_texts(cornell):
        for token in TOKEN.findall(text.lower().replace("’", "'")):
            (forms if "'" in token else plain)[token.replace("'", "")] += 1
    known = set(english)
    counts = sorted((plain[word] + forms[word] for word in english), reverse=True)
    table = []
    for form in CONTRACTIONS:
        stem = form.replace("'", "")
        used, alone = forms[stem], plain[stem]
        if used < OFFER_RATIO * alone:
            continue
        rank = None if stem in known else sum(1 for count in counts if count > used + alone)
        table.append((stem, form, rank, used < REPLACE_RATIO * alone))
    return table


def vocabulary(english: list[str], contractions) -> list[str]:
    """english.txt 加上插进去的缩写词（同一排名时缩写在前），与 EnglishContractions.vocabulary 一致。"""
    inserted = [(rank, 0, stem) for stem, _, rank, _ in contractions if rank is not None]
    return [word for _, _, word in sorted([(rank, 1, word) for rank, word in enumerate(english)] + inserted,
                                          key=lambda item: (item[0], item[1]))]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--norvig", required=True, type=pathlib.Path)
    parser.add_argument("--cornell", required=True, type=pathlib.Path)
    parser.add_argument("--holdout", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT)
    parser.add_argument("--contractions-output", type=pathlib.Path, default=CONTRACTIONS_OUTPUT)
    args = parser.parse_args()

    # 与 EnglishContractions.kt 同一套排名：去空白、小写，重复的词按第一次出现。
    english = list(dict.fromkeys(line.strip().lower() for line in VOCAB.read_text(encoding="utf-8").splitlines() if line.strip()))
    contractions = contraction_table(args.cornell, english)
    args.contractions_output.write_text("# 滑行用的英文缩写，由 tools/glide_lab/build_english_bigrams.py 生成。\n"
        "# 去撇号写法 缩写 [插入排名] [?]：有排名的是 english.txt 没有的词，按排名插进滑行词表；? 表示原词也常用，缩写只排进候选。\n" +
        "".join(" ".join([stem, form] + ([str(rank)] if rank is not None else []) + (["?"] if offer else [])) + "\n"
                for stem, form, rank, offer in contractions),
        encoding="utf-8", newline="\n")
    vocab = vocabulary(english, contractions)
    known = set(vocab)
    cornell_pairs: collections.Counter[tuple[str, str]] = collections.Counter()
    cornell_first: collections.Counter[str] = collections.Counter()
    cornell_words: collections.Counter[str] = collections.Counter()
    held = []
    for line_id, text in cornell_texts(args.cornell):
        words = tokens(text)
        if args.holdout and zlib.crc32(line_id.encode()) % 10 == 0:
            if 2 <= len(words) <= 12 and all(word in known for word in words):
                held.append(" ".join(words))
            continue
        cornell_words.update(words)
        for a, b in zip(words, words[1:]):
            cornell_first[a] += 1
            cornell_pairs[(a, b)] += 1

    web_first: collections.Counter[str] = collections.Counter()
    for line in (args.norvig / "count_1w.txt").read_text(encoding="utf-8").splitlines():
        word, count = line.split("\t")
        web_first[word.lower()] += int(count)
    web_pairs: collections.Counter[tuple[str, str]] = collections.Counter()
    for line in (args.norvig / "count_2w.txt").read_text(encoding="utf-8", errors="ignore").splitlines():
        key, count = line.split("\t")
        pair = key.lower().split(" ")
        if len(pair) == 2:
            web_pairs[(pair[0], pair[1])] += int(count)

    # 单词概率：对白与网页的词频按 0.6 / 0.4 混合（english.txt 是网页排名，im、dont 在对白里常用得多），
    # 再加千分之一按排名的估算，两边都没出现的词也有一个很小的概率。
    rank_prob = {word: 1.0 / (rank + 10) for rank, word in enumerate(vocab)}
    sums = [sum(table[word] for word in vocab) for table in (cornell_words, web_first, rank_prob)]
    unigram = {word: CORNELL_UNIGRAM * cornell_words[word] / sums[0] + WEB_UNIGRAM * web_first[word] / sums[1] +
               RANK_UNIGRAM * rank_prob[word] / sums[2] for word in vocab}

    pairs = {pair for pair, count in cornell_pairs.items() if count >= MIN_CORNELL} | set(web_pairs)
    rows = []
    for a, b in pairs:
        if a not in known or b not in known:
            continue
        p = UNIGRAM_WEIGHT * unigram[b]
        if cornell_first[a]:
            p += CORNELL_WEIGHT * cornell_pairs[(a, b)] / cornell_first[a]
        if web_first[a]:
            p += WEB_WEIGHT * web_pairs[(a, b)] / web_first[a]
        score = round(-math.log(p) * 10)
        if round(-math.log(UNIGRAM_WEIGHT * unigram[b]) * 10) - score >= MIN_GAIN:
            rows.append((a, b, score))
    rows.sort()
    singles = "".join(f"{word} {min(255, round(-math.log(unigram[word]) * 10))}\n" for word in vocab)
    args.output.write_text(singles + "".join(f"{a} {b} {score}\n" for a, b, score in rows), encoding="utf-8", newline="\n")
    print(f"{len(rows)} pairs -> {args.output} ({args.output.stat().st_size} bytes)")
    if args.holdout:
        args.holdout.write_text("\n".join(held) + "\n", encoding="utf-8")
        print(f"{len(held)} held-out lines -> {args.holdout}")


if __name__ == "__main__":
    main()
