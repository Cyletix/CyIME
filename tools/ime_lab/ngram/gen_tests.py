"""Generate T9 evaluation sentences with the DeepSeek API (evaluation only, never training).

The key is read at run time from the local Cline credential (deepSeekApiKey), as
tools/ime_lab/lexicon_source.py does, and is never printed or stored. Every raw reply is
archived next to the outputs. Readings are cross-checked against pypinyin; disagreements
are written for manual review instead of being trusted.

Usage (lexicon venv, which has pypinyin):
  python gen_tests.py constructions OUT_DIR [--model M]
  python gen_tests.py everyday OUT_DIR [--model M]
  python gen_tests.py build OUT_DIR          # raw replies -> clauses, pinyin review, chat files
"""
import argparse
import json
import re
import time
import urllib.request
from pathlib import Path

API = "https://api.deepseek.com/chat/completions"
HAN = re.compile(r"[㐀-䶿一-鿿〇]")
HAN_RUN = re.compile(r"[㐀-䶿一-鿿〇]+")
SYSTEM = "你是严谨的中文语言学助理，只输出符合要求的 JSON，不输出任何其他文字。"
READING_RULE = ("pinyin 为句中每个汉字一个无声调拼音（ü 写 v），空格分隔，按句中实际读音："
                "多音字按语境（如“睡不着”的着读 zhao、“得去”的得读 dei、“还是”的还读 hai），"
                "轻声字写其本音（如“的”写 de、“了”写 le），标点不写拼音。")


def key():
    secrets = Path.home() / ".cline/data/secrets.json"
    value = json.loads(secrets.read_text(encoding="utf-8")).get("deepSeekApiKey")
    if not isinstance(value, str) or not value:
        raise SystemExit("No DeepSeek key in the local Cline credentials")
    return value


def ask(prompt, model, out_dir, name):
    body = json.dumps({"model": model, "temperature": 0.7, "response_format": {"type": "json_object"},
                       "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": prompt}]}).encode()
    request = urllib.request.Request(API, data=body, headers={"Content-Type": "application/json",
                                                               "Authorization": "Bearer " + key()})
    with urllib.request.urlopen(request, timeout=300) as response:
        reply = json.loads(response.read().decode("utf-8"))
    content = reply["choices"][0]["message"]["content"]
    (out_dir / "raw").mkdir(parents=True, exist_ok=True)
    (out_dir / "raw" / f"{name}.json").write_text(json.dumps({"model": model, "prompt": prompt, "content": content,
                                                             "usage": reply.get("usage")}, ensure_ascii=False, indent=1),
                                                  encoding="utf-8")
    return json.loads(content)


def constructions(out_dir, model):
    listing = ask(
        "为测试中文九键输入法的整句组句，列出中文日常口语中有代表性、对组句有挑战的句式或语法结构，至少 28 类。"
        "必须包括：A不A正反问（单音节如去不去，双音节拆分如达不达标、喜不喜欢）、有没有与V没V、动词重叠（看看、试一试、研究研究）、"
        "离合词拆分（洗了个澡、见过面、帮个忙）、可能补语（看得懂、听不清、来得及、做不完）、程度补语（好得很、累死了、开心极了）、"
        "趋向补语（拿出来、跑回去）、越…越…、一…就…、连…都/也…、把字句、被字句、是…的、比较句、X来X去与不X不Y等四字格、"
        "句末语气词（吧、呢、嘛、啦、呗）、时间与数量短语、反问与否定（怎么不、哪有、难道）。"
        "输出 {\"categories\":[{\"name\":\"类别名\",\"pattern\":\"结构说明\"}]}。", model, out_dir, "categories")
    categories = listing["categories"]
    examples = []
    for start in range(0, len(categories), 6):
        group = categories[start:start + 6]
        reply = ask(
            "为下列每个句式各写 10 条自然的微信聊天短句，每条 4～14 个汉字（可含标点），不要英文字母和阿拉伯数字，"
            "词汇和话题尽量多样，各条之间不要重复同一个词语搭配。" + READING_RULE +
            "输出 {\"categories\":[{\"name\":\"类别名\",\"examples\":[{\"text\":\"\",\"pinyin\":\"\"}]}]}。句式：" +
            json.dumps(group, ensure_ascii=False), model, out_dir, f"examples-{start // 6:02d}")
        for category in reply["categories"]:
            for example in category["examples"]:
                examples.append({"set": "constructions", "category": category["name"], **example})
        time.sleep(1)
    (out_dir / "constructions.json").write_text(json.dumps(examples, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"categories": len(categories), "examples": len(examples)}, ensure_ascii=False))


TOPICS = {
    "everyday": ["约饭、出行、天气、时间约定、请求与答应", "工作、学习、考试、身体、情绪、道歉与感谢",
                 "购物、家庭、游戏、追剧、吐槽、安慰与鼓励"],
    # Tuning only (parameter selection); topics differ from the evaluation set above.
    "tune": ["工作沟通、技术讨论、学习计划、开会与进度", "做饭、宠物、运动、健康、家务与生活琐事",
             "朋友八卦、节日、旅行、拍照、搬家与租房"],
}


def everyday(out_dir, model, name="everyday"):
    items = []
    topics = TOPICS[name]
    for index, topic in enumerate(topics):
        reply = ask(
            f"生成 50 条自然的日常微信聊天短句，话题：{topic}。每条 4～16 个汉字（可含标点），口语化，"
            "避免书面语、专有名词、英文字母和阿拉伯数字，各条不要重复。" + READING_RULE +
            "输出 {\"items\":[{\"text\":\"\",\"pinyin\":\"\"}]}。", model, out_dir, f"{name}-{index}")
        items += [{"set": name, "category": topic, **item} for item in reply["items"]]
        time.sleep(1)
    (out_dir / f"{name}.json").write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"items": len(items)}, ensure_ascii=False))


def build(out_dir):
    from pypinyin import Style, lazy_pinyin
    rows, review = [], []
    for name in ("constructions", "everyday", "tune"):
        path = out_dir / f"{name}.json"
        if not path.exists():
            continue
        for item in json.loads(path.read_text(encoding="utf-8")):
            text = item["text"].strip()
            given = item.get("pinyin", "").lower().replace("ü", "v").split()
            chars = HAN.findall(text)
            if len(given) != len(chars):
                review.append({**item, "issue": f"model pinyin count {len(given)} != chars {len(chars)}"})
                given = None
            # pypinyin over the whole sentence keeps phrase context for polyphones.
            auto = [p.replace("ü", "v") for p in lazy_pinyin("".join(chars), style=Style.NORMAL, v_to_u=False)]
            reading = given or auto
            if given and given != auto:
                diff = [f"{c}:{g}/{a}" for c, g, a in zip(chars, given, auto) if g != a]
                review.append({**item, "issue": "model/pypinyin differ: " + " ".join(diff), "auto": " ".join(auto)})
            cursor = 0
            for run in HAN_RUN.findall(text):
                part = reading[cursor:cursor + len(run)]
                cursor += len(run)
                if len(run) >= 2:
                    rows.append({"set": item["set"], "category": item["category"], "text": run, "pinyin": " ".join(part),
                                 "sentence": text})
    seen, unique = set(), []
    for row in rows:
        if row["text"] not in seen:
            seen.add(row["text"])
            unique.append(row)
    for name in ("constructions", "everyday", "tune"):
        chosen = [r for r in unique if r["set"] == name]
        if not chosen:
            continue
        target = out_dir / name
        target.mkdir(exist_ok=True)
        (target / "chat.txt").write_text("".join(r["text"] + "\n" for r in chosen), encoding="utf-8")
        (target / "chat.pinyin.txt").write_text("".join(r["pinyin"] + "\n" for r in chosen), encoding="utf-8")
        (target / "clauses.tsv").write_text("".join(f"{r['category']}\t{r['text']}\t{r['pinyin']}\t{r['sentence']}\n"
                                                    for r in chosen), encoding="utf-8")
    (out_dir / "pinyin-review.json").write_text(json.dumps(review, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"clauses": len(unique), "review": len(review)}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["constructions", "everyday", "tune", "build"])
    parser.add_argument("out_dir", type=Path)
    parser.add_argument("--model", default="deepseek-v4-pro")
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    if args.command == "constructions":
        constructions(args.out_dir, args.model)
    elif args.command in ("everyday", "tune"):
        everyday(args.out_dir, args.model, args.command)
    else:
        build(args.out_dir)
