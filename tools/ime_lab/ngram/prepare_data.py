"""Prepare inputs for the T9 word n-gram model (see README.md in this directory).

corpus: LCCC-base utterances -> Han runs, one per line (original spaces removed; the
        model re-segments with the T9 dictionary so its units match the decoder).
vocab:  every Han word of the rime_ice dictionary tables, with the tables' own counts
        used only as the first segmentation guess (tables without counts get none).
"""
import argparse
import gzip
import json
import re
from pathlib import Path

HAN_RUN = re.compile(r"[㐀-䶿一-鿿〇]+")
TABLES = ("8105", "base", "ext", "tencent", "others")


def corpus(source, output, exclude=None, holdout_sessions=0, holdout_output=None, seed="20261018"):
    """LCCC dialogues (gzip JSONL, one utterance list per line) -> Han runs, one per line.
    exclude: file of sentences never to emit verbatim (evaluation items; see eval_exclude.py).
    holdout_sessions: that many dialogues, chosen by a salted hash of their position, go to
    holdout_output instead of output, so an unseen evaluation sample exists before training."""
    import hashlib
    excluded = set()
    if exclude:
        with open(exclude, encoding="utf-8") as lines:
            excluded = {line.strip() for line in lines if line.strip()}
    runs = utterances = chars = dropped = held = 0
    holdout = open(holdout_output, "w", encoding="utf-8") if holdout_sessions and holdout_output else None
    with gzip.open(source, "rt", encoding="utf-8") as reader, open(output, "w", encoding="utf-8") as writer:
        for index, line in enumerate(reader):
            try:
                dialog = json.loads(line)
            except ValueError:
                continue
            if isinstance(dialog, dict):
                dialog = dialog.get("dialog") or dialog.get("conversation") or []
            if not isinstance(dialog, list):
                continue
            target = writer
            if holdout is not None and held < holdout_sessions:
                digest = hashlib.sha256(f"{seed}:{index}".encode("utf-8")).digest()
                # about 1 in 400 dialogues until the quota is reached; the quota caps the sample size
                if digest[0] == 0 and digest[1] < 160:
                    target = holdout
                    held += 1
            for utterance in dialog:
                if not isinstance(utterance, str):
                    continue
                utterances += 1
                for run in HAN_RUN.findall(utterance.replace(" ", "")):
                    if run in excluded:
                        dropped += 1
                        continue
                    target.write(run + "\n")
                    if target is writer:
                        runs += 1
                        chars += len(run)
    if holdout is not None:
        holdout.close()
    print(json.dumps({"source": str(source), "utterances": utterances, "runs": runs, "chars": chars,
                      "excluded_runs_dropped": dropped, "holdout_sessions": held}))


def t2s_converter(opencc_dir):
    """Traditional -> simplified with OpenCC's TSPhrases (longest match) then TSCharacters."""
    def table(name):
        mapping = {}
        for line in open(Path(opencc_dir) / name, encoding="utf-8"):
            key, _, values = line.rstrip("\n").partition("\t")
            if key and values:
                mapping[key] = values.split(" ")[0]
        return mapping
    phrases, chars = table("TSPhrases.txt"), table("TSCharacters.txt")
    longest = max(map(len, phrases)) if phrases else 0
    starts = {phrase[0] for phrase in phrases}

    def convert(text):
        if not any(c in chars or c in starts for c in text):
            return text
        out, i = [], 0
        while i < len(text):
            for size in range(min(longest, len(text) - i) if text[i] in starts else 0, 1, -1):
                if text[i:i + size] in phrases:
                    out.append(phrases[text[i:i + size]])
                    i += size
                    break
            else:
                out.append(chars.get(text[i], text[i]))
                i += 1
        return "".join(out)
    return convert


def text(source, output, opencc_dir=None):
    """Plain text lines (e.g. OPUS OpenSubtitles mono, gzip) -> Han runs, one per line.
    Some files marked simplified are traditional: with opencc_dir they are converted."""
    runs = lines = chars = 0
    convert = t2s_converter(opencc_dir) if opencc_dir else (lambda s: s)
    opener = gzip.open if str(source).endswith(".gz") else open
    with opener(source, "rt", encoding="utf-8", errors="replace") as reader, open(output, "w", encoding="utf-8") as writer:
        for line in reader:
            lines += 1
            for run in HAN_RUN.findall(convert(line.replace(" ", ""))):
                writer.write(run + "\n")
                runs += 1
                chars += len(run)
    print(json.dumps({"source": str(source), "lines": lines, "runs": runs, "chars": chars}))


WIKI_VARIANT = re.compile(r"-\{(.*?)\}-", re.S)
WIKI_DROP = [re.compile(x, re.S | re.I) for x in (
    r"<!--.*?-->", r"<ref[^>/]*/>", r"<ref.*?</ref>",
    r"<(math|code|pre|nowiki|gallery|source|syntaxhighlight|timeline|score|chem)[^>]*>.*?</\1>",
    r"\{\|.*?\|\}", r"\[\[(?:File|Image|文件|圖像|图像|档案|檔案|Category|分类|分類):.*?\]\]")]
WIKI_LINK = re.compile(r"\[\[(?:[^\[\]|]*\|)?([^\[\]|]*)\]\]")
WIKI_EXTLINK = re.compile(r"\[https?://[^\s\]]+\s*([^\]]*)\]")
WIKI_TAG = re.compile(r"<[^>]+>")
WIKI_HEADING = re.compile(r"^=+.*?=+\s*$", re.M)


def wiki_variant(match):
    """-{zh-hans:简;zh-hant:繁}- -> the simplified reading, else the first one / plain text."""
    body = match.group(1)
    if ":" not in body:
        return body
    first = None
    for part in body.split(";"):
        key, _, value = part.partition(":")
        if key.strip().lower() in ("zh-hans", "zh-cn", "zh-sg", "zh-my"):
            return value
        if first is None and value:
            first = value
    return first or ""


def wiki_strip_templates(text):
    """Remove {{...}} with nesting (unbalanced braces just drop the rest of the page)."""
    out, depth, i = [], 0, 0
    while i < len(text):
        if text.startswith("{{", i):
            depth += 1
            i += 2
        elif text.startswith("}}", i) and depth:
            depth -= 1
            i += 2
        else:
            if not depth:
                out.append(text[i])
            i += 1
    return "".join(out)


def wiki_clean(markup):
    text = WIKI_VARIANT.sub(wiki_variant, markup)
    for pattern in WIKI_DROP:
        text = pattern.sub(" ", text)
    text = wiki_strip_templates(text)
    text = WIKI_LINK.sub(r"\1", text)
    text = WIKI_EXTLINK.sub(r"\1", text)
    text = WIKI_TAG.sub(" ", text)
    return WIKI_HEADING.sub(" ", text)


def wiki_articles(reader):
    """Yield the wikitext of every article page (namespace 0, not a redirect) of a dump."""
    ns = None
    redirect = False
    buf = None
    for line in reader:
        if buf is not None:
            end = line.find("</text>")
            if end < 0:
                buf.append(line)
                continue
            buf.append(line[:end])
            yield "".join(buf)
            buf = None
        elif "<page>" in line:
            ns, redirect = None, False
        elif "<ns>" in line:
            ns = line.strip()[4:-5]
        elif "<redirect " in line:
            redirect = True
        elif "<text" in line and ns == "0" and not redirect:
            start = line.index(">") + 1
            end = line.find("</text>")
            if end >= 0:
                yield line[start:end]
            else:
                buf = [line[start:]]


def wiki(source, output, opencc_dir, min_chars=2, max_chars=60):
    """zhwiki pages-articles XML dump (bz2) -> Han runs, one per line, simplified.
    Streams the dump: article pages only, markup stripped, every variant marker takes its
    simplified reading, then OpenCC t2s. Runs shorter than min_chars are dropped; longer
    than max_chars are cut at max_chars (the builder only needs local context).
    Progress goes to stderr every 20000 pages."""
    import bz2
    import html
    import sys
    import time
    convert = t2s_converter(opencc_dir)
    pages = kept = runs = chars = 0
    started = time.time()
    opener = bz2.open if str(source).endswith(".bz2") else open
    with opener(source, "rt", encoding="utf-8", errors="replace") as reader, open(output, "w", encoding="utf-8") as writer:
        for markup in wiki_articles(reader):
            pages += 1
            article_runs = 0
            for run in HAN_RUN.findall(convert(wiki_clean(html.unescape(markup)))):
                for k in range(0, len(run), max_chars):
                    piece = run[k:k + max_chars]
                    if len(piece) >= min_chars:
                        writer.write(piece + "\n")
                        article_runs += 1
                        chars += len(piece)
            runs += article_runs
            kept += bool(article_runs)
            if pages % 20000 == 0:
                print(f"wiki {pages} pages, {kept} kept, {runs} runs, {time.time() - started:.0f} s",
                      file=sys.stderr, flush=True)
    print(json.dumps({"source": str(source), "pages": pages, "kept": kept, "runs": runs, "chars": chars}))


def vocab(dict_dir, output, max_len):
    counts = {}
    for table in TABLES:
        body = False
        for line in open(Path(dict_dir) / f"{table}.dict.yaml", encoding="utf-8"):
            if line.startswith("..."):
                body = True
                continue
            if not body or not line.strip() or line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            text = fields[0]
            if not HAN_RUN.fullmatch(text) or len(text) > max_len:
                continue
            weight = int(fields[-1]) if len(fields) >= 3 and fields[-1].isdigit() else 0
            # ext/tencent carry a flat placeholder (100), not a frequency: presence only.
            if table not in ("8105", "base"):
                weight = 0
            counts[text] = max(counts.get(text, 0), weight)
    with open(output, "w", encoding="utf-8") as writer:
        for text, weight in counts.items():
            writer.write(f"{text}\t{weight}\n")
    print(json.dumps({"words": len(counts), "with_counts": sum(1 for w in counts.values() if w > 0)}))


def readings(dict_dir):
    """Most weighted reading per word (base/ext) and per character (8105)."""
    words, chars = {}, {}
    for table, target in (("base", words), ("ext", words), ("8105", chars)):
        body = False
        for line in open(Path(dict_dir) / f"{table}.dict.yaml", encoding="utf-8"):
            if line.startswith("..."):
                body = True
                continue
            if not body or not line.strip() or line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if len(fields) < 2:
                continue
            text, code = fields[0], fields[1]
            weight = int(fields[2]) if len(fields) > 2 and fields[2].isdigit() else 0
            if len(code.split()) != len(text) or not HAN_RUN.fullmatch(text):
                continue
            if text not in target or weight > target[text][1]:
                target[text] = (code, weight)
    return words, chars


def greedy_tokens(text, words, longest=8):
    """Forward maximum matching with the dictionary words; unknown characters stand alone."""
    tokens, i = [], 0
    while i < len(text):
        for size in range(min(longest, len(text) - i), 1, -1):
            if text[i:i + size] in words:
                tokens.append(text[i:i + size])
                i += size
                break
        else:
            tokens.append(text[i])
            i += 1
    return tokens


def dev(segmented, dict_dir, output_dir, count, min_chars, max_chars, raw=False):
    """Held-out T9 clauses from LCCC valid (never train/test) for tuning only.
    With raw=True the input is unsegmented Han runs (one per line) and is split by
    greedy dictionary matching, so no model build is needed to make a fixture."""
    words, chars = readings(dict_dir)
    candidates = []
    for line in open(segmented, encoding="utf-8"):
        tokens = greedy_tokens(line.strip(), words) if raw else line.split()
        text = "".join(tokens)
        if "<unk>" in tokens or len(tokens) < 2 or not min_chars <= len(text) <= max_chars:
            continue
        pinyin = []
        for token in tokens:
            if token in words:
                pinyin += words[token][0].split()
            elif all(c in chars for c in token):
                pinyin += [chars[c][0] for c in token]
            else:
                pinyin = None
                break
        if pinyin and len(pinyin) == len(text):
            candidates.append((text, " ".join(pinyin)))
    step = max(1, len(candidates) // count)
    chosen = candidates[::step][:count]
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / "chat.txt").write_text("".join(t + "\n" for t, _ in chosen), encoding="utf-8")
    (out / "chat.pinyin.txt").write_text("".join(p + "\n" for _, p in chosen), encoding="utf-8")
    print(json.dumps({"candidates": len(candidates), "chosen": len(chosen), "step": step}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    c = sub.add_parser("corpus"); c.add_argument("source"); c.add_argument("output")
    c.add_argument("--exclude", help="sentences never emitted verbatim (eval_exclude.py output)")
    c.add_argument("--holdout-sessions", type=int, default=0); c.add_argument("--holdout-output")
    t = sub.add_parser("text"); t.add_argument("source"); t.add_argument("output"); t.add_argument("--t2s")
    w = sub.add_parser("wiki"); w.add_argument("source"); w.add_argument("output"); w.add_argument("--t2s", required=True)
    v = sub.add_parser("vocab"); v.add_argument("dict_dir"); v.add_argument("output"); v.add_argument("--max-len", type=int, default=12)
    d = sub.add_parser("dev"); d.add_argument("segmented"); d.add_argument("dict_dir"); d.add_argument("output_dir")
    d.add_argument("--count", type=int, default=400); d.add_argument("--min-chars", type=int, default=4)
    d.add_argument("--max-chars", type=int, default=12)
    d.add_argument("--raw", action="store_true", help="input is unsegmented Han runs; split by dictionary greedy match")
    args = parser.parse_args()
    if args.command == "corpus":
        corpus(args.source, args.output, args.exclude, args.holdout_sessions, args.holdout_output)
    elif args.command == "text":
        text(args.source, args.output, args.t2s)
    elif args.command == "wiki":
        wiki(args.source, args.output, args.t2s)
    elif args.command == "vocab":
        vocab(args.dict_dir, args.output, args.max_len)
    else:
        dev(args.segmented, args.dict_dir, args.output_dir, args.count, args.min_chars, args.max_chars, args.raw)
