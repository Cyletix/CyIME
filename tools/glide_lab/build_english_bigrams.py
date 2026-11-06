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

滑行词表另补 english.txt 没有的常用词（english_glide_words.txt，只给滑行用，英文补全不用）：
english.txt 是去掉脏话的网页常用词，fuck、shit、god、huh、alright、goodbye、kidding 都没有，一笔划不出来（2026-11-06 fuck 全落到 duck）。
补的是对白里出现至少 EXTRA_MIN_COUNT 次、网页词频里也有的词（大多数时候大写的人名、多数带撇号的写法、goin 这类丢 g 的写法、
侮辱性称呼不收），加上 tools/glide_lab/english_glide_extra.txt 手写的聊天用词（lol、omg、wtf）；
对白里的词按对白词频、手写的词按对白或网页词频（取靠前的那个）插进 english.txt 的排名。

用法：python -B tools/glide_lab/build_english_bigrams.py --norvig <目录> --cornell <movie_lines.txt> [--holdout <测试句输出>]
--holdout 时按台词编号留出十分之一不参与统计，写成测试句，供合成轨迹回放评估。
"""
import argparse
import bisect
import collections
import math
import pathlib
import re
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
VOCAB = ROOT / "app/src/main/assets/english.txt"
OUTPUT = ROOT / "app/src/main/assets/english_bigrams.txt"
CONTRACTIONS_OUTPUT = ROOT / "app/src/main/assets/english_contractions.txt"
EXTRA_OUTPUT = ROOT / "app/src/main/assets/english_glide_words.txt"
EXTRA_LIST = ROOT / "tools/glide_lab/english_glide_extra.txt"
EXTRA_MIN_COUNT = 5
# 对白里常见、但不该出现在候选里的侮辱性称呼（脏话照收）。
BLOCKED = set("""nigger niggers nigga niggas faggot faggots fag fags retard retards chink chinks spic spics kike kikes
wetback wetbacks tranny trannies gook gooks dyke dykes retarded homo homos queers wop wops dago dagos honky honkies
coon coons squaw squaws midget midgets""".split())
ARTIFACTS = {"quot", "amp", "nbsp"}
# 滑行能划出的缩写（只收真正的缩写，不收所有格）。词表里没有去撇号写法的，按对白里的频率插进词表。
CONTRACTIONS = """i'm i've i'll i'd you're you've you'll you'd he's he'll he'd she's she'll she'd it's it'll
we're we've we'll we'd they're they've they'll they'd that's that'll what's what're what'd who's who'd
where's where'd how's how'd there's here's let's don't doesn't didn't can't couldn't won't wouldn't
shouldn't isn't aren't wasn't weren't haven't hasn't hadn't ain't c'mon y'know o'clock ma'am""".split()
# 去撇号写法本身也是常用词（were、well、ill）时：缩写在对白里至少是原词 5 倍才直接换，至少 1/5 才排进候选。
REPLACE_RATIO, OFFER_RATIO = 5, 0.2
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")
CASED_TOKEN = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
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


def english_ranks(counts: dict[str, int], english: list[str]):
    """词频 → 插进 english.txt 的排名：english.txt 里比它常用的词有几个。"""
    ordered = sorted(-counts.get(word, 0) for word in english)
    return lambda count: bisect.bisect_left(ordered, -count)


def extra_words(cornell: pathlib.Path, english: list[str], contractions, web: dict[str, int], holdout: bool) -> list[tuple[str, int]]:
    """(词, 插入排名)：english.txt 没有、对白里常用的词，加上手写的聊天用词。--holdout 时不看留出的台词。"""
    plain: collections.Counter[str] = collections.Counter()
    apostrophe: collections.Counter[str] = collections.Counter()
    capital: collections.Counter[str] = collections.Counter()
    for line_id, text in cornell_texts(cornell):
        if holdout and zlib.crc32(line_id.encode()) % 10 == 0:
            continue
        for index, token in enumerate(CASED_TOKEN.findall(text.replace("’", "'"))):
            word = token.lower().replace("'", "")
            (apostrophe if "'" in token else plain)[word] += 1
            # 句首以外大写的多半是人名、地名。
            if index > 0 and token[0].isupper():
                capital[word] += 1
    known = set(english) | {stem for stem, *_ in contractions}
    total = lambda word: plain[word] + apostrophe[word]
    picked = {word for word in plain if word not in known and word not in BLOCKED and word.isascii() and word.isalpha()
              and len(word) >= 2 and word in web and total(word) >= EXTRA_MIN_COUNT
              and apostrophe[word] * 2 < total(word) and capital[word] * 2 < total(word)}
    # goin、nothin：丢 g 的写法与 going 划法几乎一样；didn：didn`t 断开的半截；quot：网页转义。都不收。
    picked = {word for word in picked if not (word.endswith("in") and (word + "g" in known or word + "g" in picked))
              and word + "t" not in known and word not in ARTIFACTS}
    hand = [line.split("#")[0].strip().lower() for line in EXTRA_LIST.read_text(encoding="utf-8").splitlines()]
    hand = {word for word in hand if word and word.isascii() and word.isalpha() and word not in known}
    cornell_rank = english_ranks({word: total(word) for word in english}, english)
    web_rank = english_ranks(web, english)
    # 对白里的词按对白词频排（网页词频里 porn、nude 偏高）；手写的聊天用词对白里少见，取两者靠前的。
    ranked = [(cornell_rank(total(word)), word) for word in picked - hand] + \
        [(min(cornell_rank(total(word)), web_rank(web.get(word, 0))), word) for word in hand]
    return [(word, rank) for rank, word in sorted(ranked)]


def vocabulary(english: list[str], contractions, extras=()) -> list[str]:
    """english.txt 加上插进去的缩写词和补充词（同一排名时插进去的在前，缩写又在补充词前），
    与 EnglishContractions.vocabulary 一致。"""
    inserted = [(rank, 0, stem) for stem, _, rank, _ in contractions if rank is not None] + \
        [(rank, 0, word) for word, rank in extras]
    return [word for _, _, word in sorted([(rank, 1, word) for rank, word in enumerate(english)] + inserted,
                                          key=lambda item: (item[0], item[1]))]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--norvig", required=True, type=pathlib.Path)
    parser.add_argument("--cornell", required=True, type=pathlib.Path)
    parser.add_argument("--holdout", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT)
    parser.add_argument("--contractions-output", type=pathlib.Path, default=CONTRACTIONS_OUTPUT)
    parser.add_argument("--extra-output", type=pathlib.Path, default=EXTRA_OUTPUT)
    args = parser.parse_args()

    # 与 EnglishContractions.kt 同一套排名：去空白、小写，重复的词按第一次出现。
    english = list(dict.fromkeys(line.strip().lower() for line in VOCAB.read_text(encoding="utf-8").splitlines() if line.strip()))
    contractions = contraction_table(args.cornell, english)
    args.contractions_output.write_text("# 滑行用的英文缩写，由 tools/glide_lab/build_english_bigrams.py 生成。\n"
        "# 去撇号写法 缩写 [插入排名] [?]：有排名的是 english.txt 没有的词，按排名插进滑行词表；? 表示原词也常用，缩写只排进候选。\n" +
        "".join(" ".join([stem, form] + ([str(rank)] if rank is not None else []) + (["?"] if offer else [])) + "\n"
                for stem, form, rank, offer in contractions),
        encoding="utf-8", newline="\n")
    web_counts: dict[str, int] = collections.Counter()
    for line in (args.norvig / "count_1w.txt").read_text(encoding="utf-8").splitlines():
        word, count = line.split("\t")
        web_counts[word.lower()] += int(count)
    extras = extra_words(args.cornell, english, contractions, web_counts, bool(args.holdout))
    args.extra_output.write_text("# 滑行词表补充词（english.txt 没有的常用词），由 tools/glide_lab/build_english_bigrams.py 生成。\n"
        "# 词 插入排名：按排名插进滑行词表，只给滑行和滑行双词表用。\n" +
        "".join(f"{word} {rank}\n" for word, rank in extras), encoding="utf-8", newline="\n")
    vocab = vocabulary(english, contractions, extras)
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

    web_first = web_counts
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
    print(f"{len(extras)} extra words -> {args.extra_output}")
    print(f"{len(rows)} pairs -> {args.output} ({args.output.stat().st_size} bytes)")
    if args.holdout:
        args.holdout.write_text("\n".join(held) + "\n", encoding="utf-8")
        print(f"{len(held)} held-out lines -> {args.holdout}")


if __name__ == "__main__":
    main()
