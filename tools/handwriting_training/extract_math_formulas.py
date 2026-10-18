"""从 Math 笔记仓库提取真实公式和推导过程，生成训练模式的“公式与推导”外置题库（一般用 build_banks.py 统一生成）。

用法：
    python tools/handwriting_training/extract_math_formulas.py D:/GitHub/Math \
        D:/CyIME-Data/handwriting_training/banks/math_formulas.tsv

- 公式题：Markdown 里的 $…$ 和单行 $$…$$，规整空白后去重；
- 推导题：多行 $$…$$（含 \\\\ 换行或 aligned 等环境），或同一段里连续的等式，合成一道整段写的长题，太长按行拆段；
- 每题带提示：笔记名 › 小标题 · 公式前面那句说明，写的时候顺便复习；
- 过滤太短、太长、含中文、以及本地 KaTeX（app/src/main/assets/katex/katex.min.js）渲染报错的。
输出 TSV 每行：`类别(formula/derivation)\t长题(0/1)\tLaTeX\t提示\t来源(相对路径#小标题)`。
短题打乱为主，每 8 题穿插一道推导。
"""
from __future__ import annotations

import argparse
import json
import pathlib
import random
import re
import subprocess
import sys

MIN_LEN, MAX_FORMULA, MAX_DERIVATION = 4, 120, 220
KATEX = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/katex/katex.min.js"
DISPLAY = re.compile(r"\$\$(.+?)\$\$", re.S)
INLINE = re.compile(r"(?<![\\$])\$(?!\$)([^$\n]+?)(?<!\\)\$(?!\$)")
CJK = re.compile(r"[\u3000-\u9fff\uff00-\uffef]")
HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
ENV = re.compile(r"\\(begin|end)\{(aligned|align\*?|gathered|gather\*?|split|eqnarray\*?|array)\}(\{[^}]*\})?")


def norm(tex: str) -> str:
    return re.sub(r"\s+", " ", tex.strip()).strip(" ,.;，。；")


def plain(md: str) -> str:
    """把一行 Markdown 说明变成短提示：去掉公式、链接和格式符号。"""
    s = re.sub(r"\$[^$]*\$", "…", md)
    s = re.sub(r"!?\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", s)
    s = re.sub(r"[*_`>#]+|^\s*[-+]\s+|^\s*\d+\.\s+", "", s).strip(" ：:，,")
    return s[:40] + ("…" if len(s) > 40 else "")


def useful(note: str) -> bool:
    """说明里公式都换成了 …，剩下的文字太少就不显示，只显示笔记名和小标题。"""
    return len(re.sub(r"[…\s.,，。:：;；()（）]", "", note or "")) >= 6


def derivation_lines(body: str) -> list[str]:
    """多行公式拆成行：只在最外层（或对齐环境里）的 \\ 处断行，矩阵等环境内部的换行保留；去掉对齐外壳和 &。"""
    inner = ENV.sub("", body)
    lines, cur, depth, i = [], [], 0, 0
    while i < len(inner):
        if inner.startswith("\\begin{", i):
            depth += 1
        elif inner.startswith("\\end{", i):
            depth -= 1
        elif depth == 0 and inner.startswith("\\\\", i):
            lines.append("".join(cur)); cur = []
            i += 2
            if i < len(inner) and inner[i] == "[":  # \\[2pt] 之类的行距
                j = inner.find("]", i)
                i = j + 1 if j != -1 else i
            continue
        elif depth == 0 and inner[i] == "&" and (i == 0 or inner[i - 1] != "\\"):
            cur.append(" "); i += 1
            continue
        cur.append(inner[i]); i += 1
    lines.append("".join(cur))
    return [norm(l) for l in lines if norm(l)]


def chunks(lines: list[str]) -> list[str]:
    out, cur = [], []
    for line in lines:
        if cur and len(" \\\\ ".join(cur + [line])) > MAX_DERIVATION:
            out.append(cur); cur = []
        cur.append(line)
    if cur:
        out.append(cur)
    return ["\\begin{gathered} " + " \\\\ ".join(c) + " \\end{gathered}" for c in out if len(c) >= 2]


def extract(repo: pathlib.Path) -> list[tuple[str, str, str, str]]:
    items: list[tuple[str, str, str, str]] = []
    for md in sorted(repo.rglob("*.md")):
        rel = md.relative_to(repo).as_posix()
        if any(part.startswith(".") for part in md.relative_to(repo).parts):
            continue
        text = md.read_text(encoding="utf-8", errors="replace")
        text = re.sub(r"```.*?```", "", text, flags=re.S)  # 代码块里的 $ 不是公式
        text = re.sub(r"^---\n.*?\n---\n", "", text, flags=re.S)  # frontmatter
        title, heading, last_note = md.stem, "", ""
        # 逐段处理：段落里先找 $$ 块，再找行内公式
        for para in re.split(r"\n\s*\n", text):
            lines = para.strip().split("\n")
            m = HEADING.match(lines[0]) if lines else None
            if m:
                heading, last_note = plain(m.group(2)), ""
                para = "\n".join(lines[1:])
            where = f"{title} › {heading}" if heading else title
            src = f"{rel}#{heading}" if heading else rel

            def hint() -> str:
                return f"{where} · {last_note}" if useful(last_note) else where

            note_before = plain(DISPLAY.split(para)[0].split("\n")[-1]) if "$$" in para else ""
            if note_before and note_before != "…":
                last_note = note_before
            singles: list[str] = []  # 同一段里连续的单行 $$ 公式，常是一步步的推导
            for block in DISPLAY.findall(para):
                lines_ = derivation_lines(block)
                if len(lines_) >= 2:
                    for d in chunks(lines_):
                        items.append(("derivation", d, hint(), src))
                elif lines_:
                    items.append(("formula", lines_[0], hint(), src))
                    singles.append(lines_[0])
            if len(singles) >= 2 and sum("=" in t for t in singles) >= 2:
                for d in chunks(singles):
                    items.append(("derivation", d, hint(), src))
            rest = DISPLAY.sub(" ", para)
            for line in rest.split("\n"):
                for tex in INLINE.findall(line):
                    note = plain(line)
                    items.append(("formula", norm(tex), f"{where} · {note}" if useful(note) else where, src))
            if plain(rest.split("\n")[-1]) not in ("", "…"):
                last_note = plain(rest.split("\n")[-1])
    return items


def keep(kind: str, tex: str) -> bool:
    limit = MAX_DERIVATION + 40 if kind == "derivation" else MAX_FORMULA
    return MIN_LEN <= len(tex) <= limit and not CJK.search(tex) and "\ufffd" not in tex


def katex_ok(formulas: list[str]) -> list[bool]:
    script = (
        "const katex=require(process.argv[1]);let s='';process.stdin.on('data',d=>s+=d);"
        "process.stdin.on('end',()=>{const out=JSON.parse(s).map(t=>{try{katex.renderToString(t,"
        "{displayMode:true,throwOnError:true,strict:'ignore'});return true}catch(e){return false}});"
        "process.stdout.write(JSON.stringify(out))})"
    )
    res = subprocess.run(["node", "-e", script, str(KATEX)], input=json.dumps(formulas),
                         capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(res.stdout)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("repo")
    ap.add_argument("output")
    args = ap.parse_args()
    seen, items = set(), []
    for kind, tex, hint, src in extract(pathlib.Path(args.repo)):
        if keep(kind, tex) and tex not in seen:
            seen.add(tex)
            items.append((kind, tex, hint, src))
    ok = katex_ok([t for _, t, _, _ in items])
    items = [it for it, good in zip(items, ok) if good]
    rnd = random.Random(20261012)
    short = [i for i in items if i[0] == "formula"]
    long = [i for i in items if i[0] == "derivation"]
    rnd.shuffle(short)
    rnd.shuffle(long)
    ordered = []
    while short or long:
        ordered.extend(short[:7]); short = short[7:]
        if long:
            ordered.append(long.pop())
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for kind, tex, hint, src in ordered:
            f.write("\t".join([kind, "1" if kind == "derivation" else "0", tex, hint.replace("\t", " "), src]) + "\n")
    n_d = sum(1 for i in ordered if i[0] == "derivation")
    print(f"能渲染 {len(ordered)} 题（公式 {len(ordered) - n_d}，推导 {n_d}）→ {out}（{out.stat().st_size} 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
