"""26-key full-pinyin typo benchmark (2026-10-31): neighbour-key slips inside whole sentences.

  typo_bench.py neighbors                 the measured neighbour map of the phone QWERTY panel
  typo_bench.py make OUT.tsv              clean and typo cases (id, input, target)
  typo_bench.py score BASE.tsv [NEW.tsv]  ranks, rank-weighted score and per-key time (TypoSentenceProbe rows)

Typos are generated, not collected: one or two letters replaced by a measured neighbour of that key, at a seeded
random position, so the same corpus always gives the same cases. Sources: the ranking must-pass list, the
2026-10-31 screenshot sentence (the user's own typo input), intended abbreviations (must not change), long
sentences below, the daily chat fixture
(tuning) and the everyday clauses fixture (held out, never used to choose a rule or a number).
"""
from __future__ import annotations

import json
import math
import random
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Phone QWERTY panel as measured on the user's screenshot (1080 px wide): key pitch 106, keycap 92 x 140,
# row pitch 175; the second row starts half a key in, the third one and a half keys (shift before z).
ROWS = [("qwertyuiop", 18), ("asdfghjkl", 71), ("zxcvbnm", 177)]


def neighbor_map():
    keys = {}
    for row, (letters, x0) in enumerate(ROWS):
        for col, letter in enumerate(letters):
            keys[letter] = (x0 + col * 106, row * 175, 92, 140)
    # Same rule as LetterNeighbors.kt encodeLetterNeighbors.
    entries = []
    for letter in sorted(keys):
        ax, ay, aw, ah = keys[letter]
        near = []
        for other, (bx, by, bw, bh) in keys.items():
            if other == letter:
                continue
            dx = ((bx + bw / 2) - (ax + aw / 2)) / ((aw + bw) / 2)
            dy = ((by + bh / 2) - (ay + ah / 2)) / ((ah + bh) / 2)
            d = math.hypot(dx, dy)
            if 0.01 <= d <= 1.3:
                near.append((d, other))
        entries.append(letter + ":" + "".join(o for _, o in sorted(near)[:6]))
    return ";".join(entries)


RANKING = [("上市公司", "shang shi gong si"), ("房地产", "fang di chan"), ("帮我拿个快递", "bang wo na ge kuai di"),
           ("他也会来的", "ta ye hui lai de"), ("通话", "tong hua"), ("慢慢地", "man man de"), ("好好地", "hao hao de"),
           ("连上了吗", "lian shang le ma"), ("测完了吗", "ce wan le ma"), ("语言键", "yu yan jian"),
           ("打不出来", "da bu chu lai")]
SCREENSHOT = ("可我觉得真正的奇迹是清晨的一杯热咖啡",
              "ke wo jue de zhen zheng de qi ji shi qing chen de yi bei re ka fei",
              "kewojuedezhebzhebgdeqijishiqingxhendeyibeirejafei")
# Long everyday sentences (written for this benchmark; readings checked by hand).
LONG = [
    ("我觉得这个方案还需要再讨论一下", "wo jue de zhe ge fang an hai xu yao zai tao lun yi xia"),
    ("明天下午我们一起去图书馆看书吧", "ming tian xia wu wo men yi qi qu tu shu guan kan shu ba"),
    ("你能不能帮我把这个文件发给他", "ni neng bu neng bang wo ba zhe ge wen jian fa gei ta"),
    ("今天的天气特别好适合出去走走", "jin tian de tian qi te bie hao shi he chu qu zou zou"),
    ("我刚才在地铁上看到一个很有意思的人", "wo gang cai zai di tie shang kan dao yi ge hen you yi si de ren"),
    ("这家餐厅的菜味道不错价格也便宜", "zhe jia can ting de cai wei dao bu cuo jia ge ye pian yi"),
    ("周末要是有空的话我们去看电影吧", "zhou mo yao shi you kong de hua wo men qu kan dian ying ba"),
    ("他说他明天早上八点之前一定到", "ta shuo ta ming tian zao shang ba dian zhi qian yi ding dao"),
    ("我已经把会议的时间改到下周三了", "wo yi jing ba hui yi de shi jian gai dao xia zhou san le"),
    ("真正的问题是我们没有足够的时间", "zhen zheng de wen ti shi wo men mei you zu gou de shi jian"),
]

# Intended abbreviations mixed into full pinyin (initials inside the input on purpose); first candidates must not
# change. Typed as written, no typos added.
ABBR = [("你好不知道", "nihaobzd"), ("今天怎么样", "jintianzmy"), ("我们一起去吃饭", "womenyqqchifan"),
        ("这个怎么办", "zhegezmban"), ("你在干什么", "nizaigansm"), ("明天几点来", "mingtianjdlai"),
        ("我觉得还不错", "wojuedehbc"), ("他妈的真累", "tmdzhenlei"), ("我在公司", "wozaigs"),
        ("这个问题已经解决了", "zhegewtyjjjle"), ("现在几点了", "xianzaijdl"), ("你看过电影吗", "nikanguodyma"),
        ("今天晚上", "jtwanshang"), ("我不知道了", "wobzdle"), ("谢谢你啊", "xxnia"), ("没有问题", "myouwenti"),
        ("我们明天见", "wmmingtianjian"), ("什么时候", "smshihou"), ("你到家了吗", "nidaojlma"),
        ("差不多了", "cbduole")]


def fixture(name):
    data = json.loads((HERE / "fixtures" / name).read_text(encoding="utf-8"))
    return [(c["text"], " ".join(c["pinyin"])) for c in data["cases"] if len(c["pinyin"]) >= 2]


def typo(pinyin, near, rng, count):
    """Replace `count` letters (in different syllables) by a measured neighbour of the key."""
    syllables = pinyin.split()
    if len(syllables) < count:
        return None
    for _ in range(50):
        chosen = rng.sample(range(len(syllables)), count)
        out = list(syllables)
        for s in chosen:
            i = rng.randrange(len(out[s]))
            out[s] = out[s][:i] + rng.choice(near[out[s][i]]) + out[s][i + 1:]
        text = "".join(out)
        if text != "".join(syllables):
            return text
    return None


def make(path):
    near = {e[0]: e[2:] for e in neighbor_map().split(";")}
    sets = [("rank", RANKING), ("long", LONG), ("chat", fixture("daily_chat_20261003.json")),
            ("heldout", fixture("everyday_clauses_20261009.json"))]
    rows = [("shot-typo2", SCREENSHOT[2], SCREENSHOT[0]), ("shot-clean", SCREENSHOT[1].replace(" ", ""), SCREENSHOT[0])]
    rows += [(f"abbr-{n:03d}-clean", code, text) for n, (text, code) in enumerate(ABBR)]
    for name, items in sets:
        seen = set()
        for n, (text, pinyin) in enumerate(items):
            if text in seen:
                continue
            seen.add(text)
            rng = random.Random(f"{name}:{text}")
            rows.append((f"{name}-{n:03d}-clean", pinyin.replace(" ", ""), text))
            one = typo(pinyin, near, rng, 1)
            if one:
                rows.append((f"{name}-{n:03d}-typo1", one, text))
            if len(pinyin.split()) >= 6:
                two = typo(pinyin, near, rng, 2)
                if two:
                    rows.append((f"{name}-{n:03d}-typo2", two, text))
    Path(path).write_text("".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
    print(f"{len(rows)} cases -> {path}")


def load(path):
    rows = {}
    for line in Path(path).read_text(encoding="utf-8").split("\n"):
        if not line:
            continue
        f = line.split("\t") + ["", ""]
        rows[f[0]] = {"input": f[1], "target": f[2], "rank": int(f[3]),
                      "us": [int(x) for x in f[4].split(",") if x], "top": f[5].split("|") if f[5] else [],
                      "work": [int(x) for x in f[6].split(",") if x]}
    return rows


def group(case_id):
    source, kind = case_id.split("-")[0], case_id.split("-")[-1]
    return ("heldout" if source == "heldout" else "abbr" if source == "abbr" else "tuning"), kind


def summary(rows):
    groups = {}
    for case_id, r in rows.items():
        g = groups.setdefault(group(case_id), [])
        g.append(r["rank"])
    out = {}
    for key, ranks in sorted(groups.items()):
        weighted = sum({0: 1, 1: .5, 2: .25, 3: .125}.get(x, 0) for x in ranks) / len(ranks)
        out[key] = (len(ranks), weighted, sum(x == 0 for x in ranks) / len(ranks),
                    sum(0 <= x < 5 for x in ranks) / len(ranks))
    return out


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(p * len(values)))]


def score(paths):
    runs = [load(p) for p in paths]
    print("| set | kind | n | " + " | ".join(f"weighted / top1 / top5 ({Path(p).stem})" for p in paths) + " |")
    print("|---|---|---:|" + "---|" * len(paths))
    tables = [summary(r) for r in runs]
    for key in tables[0]:
        cells = [f"{t[key][1]:.3f} / {t[key][2]:.1%} / {t[key][3]:.1%}" for t in tables]
        print(f"| {key[0]} | {key[1]} | {tables[0][key][0]} | " + " | ".join(cells) + " |")
    for p, r in zip(paths, runs):
        us = [x / 1000 for row in r.values() for x in row["us"][1:]]
        print(f"{Path(p).stem}: per key ms mean {statistics.mean(us):.3f} p50 {pct(us, .5):.3f} "
              f"p95 {pct(us, .95):.3f} max {max(us):.3f}")
    for p, r in zip(paths, runs):
        work = [x for row in r.values() for x in row["work"]]
        if work:
            print(f"{Path(p).stem}: dictionary work per key mean {statistics.mean(work):.1f} p95 {pct(work, .95)} "
                  f"max {max(work)} total {sum(work)}")
    if len(runs) == 2:
        base, new = runs
        heavier = [(c, i) for c in base if c in new for i, (x, y) in enumerate(zip(base[c]["work"], new[c]["work"]))
                   if y > x]
        if base[next(iter(base))]["work"]:
            print(f"keys with more dictionary work: {len(heavier)} in {len({c for c, _ in heavier})} cases")
        print("\nchanged cases (first candidate or target rank):")
        for case_id in base:
            a, b = base[case_id], new.get(case_id)
            if b and (a["rank"] != b["rank"] or a["top"][:1] != b["top"][:1]):
                print(f"  {case_id} {a['input']} [{a['target']}] rank {a['rank']} -> {b['rank']}; "
                      f"first {a['top'][:1]} -> {b['top'][:1]}")


if __name__ == "__main__":
    if sys.argv[1] == "neighbors":
        print(neighbor_map())
    elif sys.argv[1] == "make":
        make(sys.argv[2])
    elif sys.argv[1] == "score":
        score(sys.argv[2:])
