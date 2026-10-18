"""把 LaTeX 公式标签规整成统一写法，训练公式识别时用。

同一个公式常有多种写法（`a_0` / `a_{0}`、`\\le` / `\\leq`、`\\left(` / `(`、`\\dfrac` / `\\frac`、
各种空白和 `\\,` 间距），渲染出来一样，但当作训练标签会让模型以为是不同答案。
这里在电脑端训练前统一：样本文件和题库里保留原文，不改。

规则：
- 拆成记号（命令、单字符、括号），输出用空格分隔的记号序列；
- 上下标、\\frac / \\sqrt 等的单记号参数补上大括号：`a_0` → `a _ { 0 }`；
- 同义命令归一：\\le→\\leq，\\ge→\\geq，\\ne→\\neq，\\to→\\rightarrow，\\dfrac/\\tfrac→\\frac，\\gets→\\leftarrow 等；
- 去掉只影响大小或间距、不影响内容的：\\left \\right \\big…、\\displaystyle、\\, \\; \\: \\! \\quad \\qquad 和 `~`。

用法：
    python tools/handwriting_training/normalize_latex.py "a_0 \\le \\dfrac12"
    from normalize_latex import normalize_latex
"""
from __future__ import annotations

import re
import sys

ALIASES = {
    r"\le": r"\leq", r"\ge": r"\geq", r"\ne": r"\neq", r"\to": r"\rightarrow", r"\gets": r"\leftarrow",
    r"\dfrac": r"\frac", r"\tfrac": r"\frac", r"\lbrace": r"\{", r"\rbrace": r"\}", r"\land": r"\wedge",
    r"\lor": r"\vee", r"\lnot": r"\neg", r"\iff": r"\Longleftrightarrow", r"\implies": r"\Longrightarrow",
    r"\cdotp": r"\cdot", r"\dots": r"\ldots", r"\mathbf": r"\boldsymbol", r"\bm": r"\boldsymbol",
}
DROP = {r"\left", r"\right", r"\big", r"\Big", r"\bigg", r"\Bigg", r"\bigl", r"\bigr", r"\Bigl", r"\Bigr",
        r"\biggl", r"\biggr", r"\displaystyle", r"\textstyle", r"\,", r"\;", r"\:", r"\!", r"\ ", r"\quad",
        r"\qquad", "~"}
# 需要参数的命令及参数个数；单记号参数补大括号
ARITY = {r"\frac": 2, r"\binom": 2, r"\sqrt": 1, r"\overline": 1, r"\underline": 1, r"\hat": 1, r"\bar": 1,
         r"\vec": 1, r"\tilde": 1, r"\dot": 1, r"\ddot": 1, r"\widehat": 1, r"\widetilde": 1,
         r"\mathbb": 1, r"\mathcal": 1, r"\mathrm": 1, r"\boldsymbol": 1, r"\operatorname": 1, r"\text": 1}
TOKEN = re.compile(r"\\[A-Za-z]+\*?|\\.|[^\s]")


def tokenize(tex: str) -> list[str]:
    tokens = []
    for t in TOKEN.findall(tex):
        t = ALIASES.get(t, t)
        if t not in DROP:
            tokens.append(t)
    return tokens


def _group(tokens: list[str], i: int) -> tuple[list[str], int]:
    """从 i 读一个参数：已有大括号就原样（递归规整），否则单个记号补上大括号。"""
    if i >= len(tokens):
        return ["{", "}"], i
    if tokens[i] == "{":
        depth, j = 0, i
        while j < len(tokens):
            depth += tokens[j] == "{"
            depth -= tokens[j] == "}"
            if depth == 0:
                break
            j += 1
        return ["{"] + _normalize(tokens[i + 1:j]) + ["}"], j + 1
    if tokens[i] in ARITY:  # 例如 a^\frac12 这种，参数本身是命令
        inner, j = _command(tokens, i)
        return ["{"] + inner + ["}"], j
    return ["{", tokens[i], "}"], i + 1


def _command(tokens: list[str], i: int) -> tuple[list[str], int]:
    out = [tokens[i]]
    j = i + 1
    if tokens[i] == r"\sqrt" and j < len(tokens) and tokens[j] == "[":  # \sqrt[n]{x}
        k = tokens.index("]", j) if "]" in tokens[j:] else len(tokens) - 1
        out += ["["] + _normalize(tokens[j + 1:k]) + ["]"]
        j = k + 1
    for _ in range(ARITY[tokens[i]]):
        arg, j = _group(tokens, j)
        out += arg
    return out, j


def _normalize(tokens: list[str]) -> list[str]:
    out: list[str] = []
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in ("^", "_"):
            arg, i = _group(tokens, i + 1)
            out += [t] + arg
        elif t in ARITY:
            cmd, i = _command(tokens, i)
            out += cmd
        elif t == "{":
            arg, i = _group(tokens, i)
            out += arg
        else:
            out.append(t)
            i += 1
    return out


def normalize_latex(tex: str) -> str:
    tex = tex.strip()
    for open_, close in (("$$", "$$"), (r"\[", r"\]"), ("$", "$")):
        if tex.startswith(open_) and tex.endswith(close) and len(tex) > len(open_) + len(close):
            tex = tex[len(open_):-len(close)].strip()
            break
    return " ".join(_normalize(tokenize(tex)))


def _self_test() -> None:
    same = [
        ("a_0", "a_{0}"),
        (r"x \le \dfrac12", r"x\leq\frac{1}{2}"),
        (r"\left( x+1 \right)^2", r"(x+1)^{2}"),
        (r"\int_0^1 f(x)\,dx", r"\int_{0}^{1}f(x)dx"),
        (r"\sqrt[3]x", r"\sqrt[3]{x}"),
        (r"$$\displaystyle \lim_{x\to0}$$", r"\lim_{x\rightarrow 0}"),
        (r"e^\frac12", r"e^{\frac{1}{2}}"),
    ]
    for a, b in same:
        assert normalize_latex(a) == normalize_latex(b), (a, b, normalize_latex(a), normalize_latex(b))
    assert normalize_latex("a_0") == "a _ { 0 }"
    assert normalize_latex("a_{10}") != normalize_latex("a_10")  # a_10 是 a 下标 1 再接 0，本来就不同


if __name__ == "__main__":
    if len(sys.argv) > 1:
        print(normalize_latex(" ".join(sys.argv[1:])))
    else:
        _self_test()
        print("自检通过")
