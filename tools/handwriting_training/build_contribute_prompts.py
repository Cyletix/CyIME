"""生成「贡献手写样本」页的固定题目 app/src/main/assets/handwriting/contribute_prompts.tsv。

每行：类型<TAB>文字<TAB>提示。类型 char（常用字）、rare（生僻字，提示为读音）、word（两字词）。
- 常用字 100 个：字频表前 300 个每隔 3 个取 1 个（50），第 300～2000 个每隔 17 个取 1 个（50）；
- 生僻字 20 个：手写模型字表外、靠生僻字模板认的字为主，外加几个常见于人名的字；
- 两字词 30 个：用来测一次写几个字时的切分和组合。

用法：python tools/handwriting_training/build_contribute_prompts.py
"""
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app/src/main/assets/handwriting"
RARE = "犇垚燚叕圐圙瀺槻龘鱻骉䴓婍翀嫑孖丼彧玥淼"
WORDS = ("我们 中国 时候 已经 知道 现在 什么 没有 可以 因为 所以 但是 觉得 还是 银行 "
         "手机 电脑 输入 快递 谢谢 明天 晚上 工作 学习 朋友 问题 喜欢 东西 地方 应该").split()


def main() -> None:
    freq = [l.split("\t")[0] for l in (ASSETS / "hanzi_frequency.tsv").read_text(encoding="utf-8").splitlines() if l.strip()]
    readings = {}
    for line in (ASSETS / "hanzi_pinyin.tsv").read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 2:
            readings[parts[0]] = parts[1]
    common = freq[:300:3][:50] + freq[300:2000:17][:50]
    assert len(set(common)) == 100
    rows = ["# 贡献手写样本的题目：类型\t文字\t提示。由 tools/handwriting_training/build_contribute_prompts.py 生成。"]
    rows += [f"char\t{c}\t按平时的速度写" for c in common]
    rows += [f"rare\t{c}\t{readings[c].split('/')[0]}" for c in RARE]
    rows += [f"word\t{w}\t像平时手写输入那样写" for w in WORDS]
    (ASSETS / "contribute_prompts.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows) - 1} prompts")


if __name__ == "__main__":
    main()
