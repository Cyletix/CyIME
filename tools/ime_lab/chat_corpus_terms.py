"""Prepare private chat text for local word-frequency analysis.

Call ``clean_learning_text`` before storing learning text, then pass that
cleaned text to ``count_words``. Neither function reads or writes files.
"""

from collections import Counter
import re
import unicodedata

import jieba


_FENCE_START = re.compile(r"^[ \t]{0,3}(?P<mark>`{3,}|~{3,})")
_QUOTE_LINE = re.compile(r"^[ \t]*>")
_URL = re.compile(
    r"(?i)(?<![A-Za-z0-9])(?:[A-Za-z][A-Za-z0-9+.-]{1,31}://|www\.)"
    r"[^\s<>\"'`，。！？、；：\u3400-\u9fff]+"
)
_EMAIL = re.compile(
    r"(?i)(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@"
    r"(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}(?![A-Za-z0-9.-])"
)
_QUOTED_PATH = re.compile(
    r"(?P<quote>[\"'])(?:[A-Za-z]:[\\/]|\\\\|/)"
    r"[^\"'\r\n]+(?P=quote)"
)
_WINDOWS_PATH = re.compile(
    r"(?<![A-Za-z0-9])(?:[A-Za-z]:[\\/]|\\\\)"
    r"[^\s<>\"'`，。！？、；：|?*]+"
)
_UNIX_PATH = re.compile(
    r"(?<![A-Za-z0-9:/])/(?!/)"
    r"[^\s<>\"'`，。！？、；：|?*]+"
)
_HEX = re.compile(r"(?<![A-Za-z0-9])[A-Fa-f0-9]{32,}(?![A-Za-z0-9])")
_BASE64 = re.compile(
    r"(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{40,}={0,2}"
    r"(?![A-Za-z0-9+/_=-])"
)
_HAN_RUN = re.compile(
    r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
    r"\U00020000-\U0002ebe0]+"
)
_LATIN_WORD = re.compile(r"[A-Za-z]{2,}")


def _without_fences_and_quotes(text: str) -> str:
    chunks = []
    fence_char = ""
    fence_width = 0
    for line in text.splitlines(keepends=True):
        content = line.rstrip("\r\n")
        if fence_char:
            closing = re.fullmatch(
                rf"[ \t]{{0,3}}{re.escape(fence_char)}{{{fence_width},}}[ \t]*",
                content,
            )
            if closing:
                fence_char = ""
            chunks.append(" ")
            continue
        opening = _FENCE_START.match(content)
        if opening:
            mark = opening.group("mark")
            fence_char, fence_width = mark[0], len(mark)
            chunks.append(" ")
        elif _QUOTE_LINE.match(content):
            chunks.append(" ")
        else:
            chunks.append(line)
    return "".join(chunks)


def clean_learning_text(text: str) -> str:
    """Normalize text and replace likely non-prose spans with spaces.

    Fenced Markdown blocks and quote lines are removed in full. URLs, email
    addresses, absolute paths, and long hex/Base64 candidates are replaced
    with boundaries so neighboring words cannot be joined by redaction.
    """
    if not isinstance(text, str):
        raise TypeError("text must be a string")
    cleaned = _without_fences_and_quotes(unicodedata.normalize("NFKC", text))
    for pattern in (
        _URL,
        _EMAIL,
        _QUOTED_PATH,
        _WINDOWS_PATH,
        _UNIX_PATH,
        _HEX,
        _BASE64,
    ):
        cleaned = pattern.sub(" ", cleaned)
    return cleaned


def count_words(text: str) -> Counter[tuple[str, str]]:
    """Count jieba Han words and casefolded English words in cleaned text.

    The caller is responsible for calling ``clean_learning_text`` first.
    One-character tokens are excluded; Han and Latin terms stay separate.
    """
    if not isinstance(text, str):
        raise TypeError("text must be a string")
    counts: Counter[tuple[str, str]] = Counter()
    for match in _HAN_RUN.finditer(text):
        counts.update(
            ("han", word)
            for word in jieba.cut(match.group(), cut_all=False)
            if len(word) >= 2
        )
    counts.update(("latin", match.group().casefold()) for match in _LATIN_WORD.finditer(text))
    return counts
