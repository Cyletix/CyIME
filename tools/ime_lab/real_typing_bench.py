"""Simulated real typist benchmark for 26-key full pinyin (2026-10-31).

The old comparisons asked "given the exact pinyin, is the target first?". People do not type exact pinyin and do
not judge an IME by one final candidate. This benchmark plays a typist (RealTypingProbe.java) and counts what
they feel: how often they must stop, how often they are stuck, how often text already on screen jumps, and, over a
learning history, how often a candidate that used to be right stops being first.

  real_typing_bench.py make-type OUT.tsv [--sentences sentence.jsonl]   typing cases for every typist profile
  real_typing_bench.py make-history STREAM.tsv PROBES.tsv RAW.jsonl [N] learning stream + probes (needs pypinyin)
  real_typing_bench.py score-type RESULT.tsv [RESULT2.tsv]
  real_typing_bench.py score-history RESULT.tsv

Typist profiles (seeded, the same input every run):
  clean   exact pinyin.
  touch   every tap lands at the key centre plus Gaussian noise (sd 0.20 key pitch across, 0.20 row pitch down) on
          the measured phone QWERTY panel; the letter is the nearest key. About 3% of letters change, nearly all
          to a neighbour, never by choice of a "nice" typo.
  fast    the same with sd 0.25 (thumbs, no looking), about 7% of letters.
  slips   per letter: 1.0% left out, 0.5% doubled, 0.7% swapped with the next one (two-thumb timing).
  south   southern accent, no fuzzy setting turned on: n/l, f/h, -in/-ing, -en/-eng each typed the other way in 30%
          of the syllables where it applies (the typist does not know which one is standard).
  zh      z/c/s versus zh/ch/sh, 30% of the syllables where it applies.
  real    touch + slips together (an ordinary day).
"""
from __future__ import annotations

import json
import math
import random
import re
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import typo_bench  # noqa: E402  (same panel geometry, same long sentences)

PITCH, ROW_PITCH, KEY_W, KEY_H = 106, 175, 92, 140
CENTERS = {letter: (x0 + col * PITCH + KEY_W / 2, row * ROW_PITCH + KEY_H / 2)
           for row, (letters, x0) in enumerate(typo_bench.ROWS) for col, letter in enumerate(letters)}


def tap(letter, rng, sd):
    x, y = CENTERS[letter]
    x += rng.gauss(0, sd * PITCH)
    y += rng.gauss(0, sd * ROW_PITCH)
    return min(CENTERS, key=lambda k: (CENTERS[k][0] - x) ** 2 + (CENTERS[k][1] - y) ** 2)


def touch(syllables, rng, sd):
    return "".join(tap(c, rng, sd) for c in "".join(syllables))


def slips(letters, rng):
    out, i = [], 0
    while i < len(letters):
        r = rng.random()
        if r < 0.010:
            pass  # left out
        elif r < 0.015:
            out += [letters[i], letters[i]]
        elif r < 0.022 and i + 1 < len(letters):
            out += [letters[i + 1], letters[i]]
            i += 1
        else:
            out.append(letters[i])
        i += 1
    return "".join(out) or letters


INITIALS = ("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c",
            "s", "y", "w")


def split(s):
    for i in INITIALS:
        if s.startswith(i) and len(s) > len(i):
            return i, s[len(i):]
    return "", s


def accent(syllables, rng, pairs_initial, pairs_final, p=0.3):
    out = []
    for s in syllables:
        ini, fin = split(s)
        if ini in pairs_initial and rng.random() < p:
            ini = pairs_initial[ini]
        for a, b in pairs_final:  # longer endings first; only the first that matches is considered
            if fin.endswith(a):
                if rng.random() < p:
                    fin = fin[: -len(a)] + b
                break
        out.append(ini + fin)
    return out


SOUTH_I = {"n": "l", "l": "n", "f": "h", "h": "f"}
SOUTH_F = [("ing", "in"), ("eng", "en"), ("in", "ing"), ("en", "eng")]
ZH_I = {"zh": "z", "ch": "c", "sh": "s", "z": "zh", "c": "ch", "s": "sh"}

# Spellings that are not pinyin syllables after an accent swap are kept: the typist really types them.
PROFILES = ("clean", "touch", "fast", "slips", "south", "zh", "real")


def variant(profile, syllables, rng):
    letters = "".join(syllables)
    if profile == "clean":
        return letters
    if profile == "touch":
        return touch(syllables, rng, 0.20)
    if profile == "fast":
        return touch(syllables, rng, 0.25)
    if profile == "slips":
        return slips(letters, rng)
    if profile == "south":
        return "".join(accent(syllables, rng, SOUTH_I, SOUTH_F))
    if profile == "zh":
        return "".join(accent(syllables, rng, ZH_I, []))
    if profile == "real":
        return slips(touch(syllables, rng, 0.20), rng)
    raise ValueError(profile)


def device_taps(profile, syllables, rng):
    """Intended letters and per-tap offsets (in key pitch / row pitch units) for a real keyboard on a device:
    the driver taps key centre + offset on each IME's own layout, so both IMEs get the same finger."""
    sd = {"touch": 0.20, "fast": 0.25, "real": 0.20}.get(profile, 0.0)
    if profile == "south":
        letters = "".join(accent(syllables, rng, SOUTH_I, SOUTH_F))
    elif profile == "zh":
        letters = "".join(accent(syllables, rng, ZH_I, []))
    elif profile in ("slips", "real"):
        letters = slips("".join(syllables), rng)
    else:
        letters = "".join(syllables)
    offsets = [(round(rng.gauss(0, sd), 3), round(rng.gauss(0, sd), 3)) if sd else (0.0, 0.0) for _ in letters]
    return letters, offsets


def make_device(path, per_source=40):
    """A device subset: the long sentences, then the first `per_source` held-out clauses and chat lines."""
    picked, count = [], {}
    for src, text, py in sources(None):
        if src == "long" or count.get(src, 0) < per_source:
            count[src] = count.get(src, 0) + 1
            picked.append((src, text, py))
    rows = []
    for n, (src, text, py) in enumerate(picked):
        for profile in ("clean", "touch", "fast", "south", "real"):
            letters, offsets = device_taps(profile, py, random.Random(f"device:{profile}:{text}"))
            rows.append("\t".join((f"{profile}-{src}-{n:03d}", text, letters,
                                    " ".join(f"{dx},{dy}" for dx, dy in offsets))) + "\n")
    Path(path).write_text("# id\ttarget\tintended letters\tper-tap offset dx,dy in key pitch / row pitch\n"
                          + "".join(rows), encoding="utf-8")
    print(f"{len(rows)} device cases -> {path}")


def sources(sentences):
    items = [("long", t, p.split()) for t, p in typo_bench.LONG]
    for name, fx in (("heldout", "everyday_clauses_20261009.json"), ("chat", "daily_chat_20261003.json")):
        data = json.loads((HERE / "fixtures" / fx).read_text(encoding="utf-8"))
        items += [(name, c["text"], c["pinyin"]) for c in data["cases"] if len(c["pinyin"]) >= 2 and c.get("is_body", True)]
    if sentences:
        for line in Path(sentences).read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            if row.get("group") == "sms":
                items.append(("sms", row["text"], row["pinyin"]))
    seen, out = set(), []
    for src, text, py in items:
        if text not in seen and re.fullmatch(r"[一-鿿]+", text) and len(text) == len(py):
            seen.add(text)
            out.append((src, text, py))
    return out


def make_type(path, sentences=None):
    rows = []
    for n, (src, text, py) in enumerate(sources(sentences)):
        for profile in PROFILES:
            rng = random.Random(f"{profile}:{text}")
            rows.append((f"{profile}-{src}-{n:04d}", variant(profile, py, rng), text, "".join(py)))
    Path(path).write_text("".join("\t".join(r[:3]) + "\n" for r in rows), encoding="utf-8")
    Path(path).with_suffix(".clean.tsv").write_text("".join(f"{r[0]}\t{r[3]}\n" for r in rows), encoding="utf-8")
    print(f"{len(rows)} cases ({len(rows) // len(PROFILES)} sentences x {len(PROFILES)} profiles) -> {path}")


def edit(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def flips(firsts):
    """Times text that was already on screen (all but the last character of the previous first candidate)
    is rewritten by the next key."""
    n = 0
    for a, b in zip(firsts, firsts[1:]):
        stable = a[:-1]
        k = min(len(stable), len(b))
        if k and stable[:k] != b[:k]:
            n += 1
    return n


def load_type(path):
    rows = []
    for line in Path(path).read_text(encoding="utf-8").split("\n"):
        if not line:
            continue
        f = line.split("\t")
        rows.append({"id": f[0], "input": f[1], "target": f[2], "rank": int(f[3]), "first": f[4],
                     "picks": int(f[5]), "stops": int(f[6]), "stuck": f[7] == "1", "left": f[8],
                     "firsts": f[9].split("\u001f") if f[9] else [], "top": f[10].split("|"),
                     "us": int(f[11])})
    return rows


def type_table(rows, clean_inputs):
    groups = {}
    for r in rows:
        profile = r["id"].split("-")[0]
        groups.setdefault(profile, []).append(r)
    out = {}
    for profile in PROFILES:
        g = groups.get(profile)
        if not g:
            continue
        chars = sum(len(r["target"]) for r in g)
        letters = sum(len(clean_inputs[r["id"]]) for r in g)
        typos = sum(edit(clean_inputs[r["id"]], r["input"]) for r in g)
        wrong_chars = sum(edit(r["first"], r["target"]) for r in g)
        stops = sum(r["stops"] + (r["stuck"] * 3) for r in g)
        out[profile] = {
            "n": len(g), "typo_rate": typos / letters,
            "affected": sum(clean_inputs[r["id"]] != r["input"] for r in g) / len(g),
            "whole": sum(r["rank"] == 0 for r in g) / len(g),
            "weighted": sum({0: 1, 1: .5, 2: .25, 3: .125}.get(r["rank"], 0) for r in g) / len(g),
            "char_acc": 1 - wrong_chars / chars,
            "stops100": 100 * stops / chars,
            "stuck": sum(r["stuck"] for r in g) / len(g),
            "flips": sum(flips(r["firsts"]) for r in g) / len(g),
            "ms_key": statistics.mean(r["us"] / max(1, len(r["input"])) / 1000 for r in g),
        }
    return out


def score_type(paths):
    clean = {}
    for p in paths:
        cp = Path(p).with_name("cases.clean.tsv")
        if cp.is_file():
            clean.update(dict(line.split("\t") for line in cp.read_text(encoding="utf-8").splitlines()))
    tables = [type_table(load_type(p), clean) for p in paths]
    head = ("| 打字的人 | 句数 | 字母打错率 | 有错的句子 | 整句一次对 | 加权分 | 直接空格的字准确率 | "
            "每百字要停几下 | 卡死（前20没有能用的） | 打字中途已显示的字被改写（次/句） |")
    for p, t in zip(paths, tables):
        print(f"\n{Path(p).name}\n\n{head}\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        for profile, m in t.items():
            print(f"| {profile} | {m['n']} | {m['typo_rate']:.1%} | {m['affected']:.0%} | {m['whole']:.1%} | "
                  f"{m['weighted']:.3f} | {m['char_acc']:.1%} | {m['stops100']:.1f} | {m['stuck']:.1%} | "
                  f"{m['flips']:.2f} |")


def make_history(stream_path, probes_path, raw, count=1500):
    from pypinyin import Style, lazy_pinyin
    rng = random.Random("history-20261031")
    clauses = []
    held = {text for _, text, _ in sources(None)}
    for line in Path(raw).read_text(encoding="utf-8").splitlines():
        for c in json.loads(line).get("clauses", []):
            if 2 <= len(c) <= 12 and re.fullmatch(r"[一-鿿]+", c) and c not in held:
                clauses.append(c)
    rng.shuffle(clauses)
    rows = []
    for n, c in enumerate(clauses[:count]):
        py = lazy_pinyin(c, style=Style.NORMAL, v_to_u=False)
        rows.append(f"s{n:05d}\t{''.join(py).replace('ü', 'v')}\t{c}\n")
    Path(stream_path).write_text("".join(rows), encoding="utf-8")
    probes = [(f"rank-{n:02d}", p.replace(" ", ""), t) for n, (t, p) in enumerate(typo_bench.RANKING)]
    data = json.loads((HERE / "fixtures" / "everyday_clauses_20261009.json").read_text(encoding="utf-8"))
    probes += [(f"heldout-{c['id']}", "".join(c["pinyin"]), c["text"]) for c in data["cases"]
               if len(c["pinyin"]) >= 2]
    # Short codes typed all day; the target is whatever was first before any learning ("*").
    for code in ("de", "shi", "zai", "ta", "le", "ma", "ne", "ba", "hao", "dui", "wo", "ni", "yi", "bu", "you",
                 "he", "ye", "jiu", "dou", "na", "zhe", "shuo", "kan", "xiang", "yao", "hui", "neng", "qu", "lai",
                 "guo", "jiu", "zhi", "dao", "wei", "ke", "mei", "xian", "zhen", "keyi", "shijian", "xianzai",
                 "mingtian", "zhidao", "yinwei", "suoyi", "danshi", "ruguo", "women", "tamen", "shenme",
                 "zenme", "weishenme", "haode", "duibuqi", "meiyou", "yijing", "haishi", "keneng", "yinggai"):
        probes.append((f"short-{code}", code, "*"))
    Path(probes_path).write_text("".join("\t".join(p) + "\n" for p in probes), encoding="utf-8")
    print(f"{len(rows)} stream lines, {len(probes)} probes")


def score_history(path):
    probes, states = {}, {}
    for line in Path(path).read_text(encoding="utf-8").split("\n"):
        if not line:
            continue
        f = line.split("\t")
        if f[0] == "state":
            states[int(f[1])] = " ".join(f[2:])
        else:
            probes.setdefault(int(f[1]), {})[f[2]] = {"rank": int(f[5]), "top": f[6].split("|"), "target": f[4]}
    base = probes[min(probes)]
    print("| 学了多少句 | 误选上屏 | 原本对、现在不是首选 | 原本不对、现在首选对 | 首选变了（全部） | 常打短码首位换了 | 常打短码前5顺序变了 |")
    print("|---:|---|---:|---:|---:|---:|---:|")
    for pos in sorted(probes):
        cur = probes[pos]
        worse = better = changed = short_first = short_order = 0
        for pid, b in base.items():
            c = cur[pid]
            if pid.startswith("short-"):
                short_first += c["top"][:1] != b["top"][:1]
                short_order += c["top"][:5] != b["top"][:5]
                continue
            worse += b["rank"] == 0 and c["rank"] != 0
            better += b["rank"] != 0 and c["rank"] == 0
            changed += c["top"][:1] != b["top"][:1]
        print(f"| {pos} | {states.get(pos, '')} | {worse} | {better} | {changed} | {short_first} | {short_order} |")
    last = probes[max(probes)]
    print("\n变差的例子（学之前首选 → 学之后首选）：")
    for pid, b in base.items():
        c = last[pid]
        if (pid.startswith("short-") and c["top"][:1] != b["top"][:1]) or (b["rank"] == 0 and c["rank"] != 0):
            print(f"  {pid}: {b['top'][:3]} -> {c['top'][:3]}")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "make-type":
        make_type(sys.argv[2], sys.argv[4] if len(sys.argv) > 4 and sys.argv[3] == "--sentences" else None)
    elif cmd == "make-history":
        make_history(sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5]) if len(sys.argv) > 5 else 1500)
    elif cmd == "make-device":
        make_device(sys.argv[2])
    elif cmd == "score-type":
        score_type(sys.argv[2:])
    elif cmd == "score-history":
        score_history(sys.argv[2])
