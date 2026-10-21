"""与 App 的 CandidateGlossary.derived 同规则（语气词、吗、数字加单位），用来量。"""
MODAL = "啊呀吧呢嘛哦啦呗喽"
UNITS = [("分钟", "minute", "minutes"), ("小时", "hour", "hours"), ("点", "o'clock", "o'clock"), ("天", "day", "days"),
         ("年", "year", "years"), ("次", "time", "times"), ("岁", "year old", "years old")]
ONES = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
TENS = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]
def cn(t):
    if not t or len(t) > 3: return None
    if t.isdigit(): v = int(t); return v if 1 <= v <= 99 else None
    d = "零一二三四五六七八九"; dig = lambda c: 2 if c == '两' else (d.index(c) if c in d else None)
    if '十' not in t: return dig(t) if len(t) == 1 and t != '零' else None
    if t.count('十') > 1: return None
    i = t.index('十'); tens = 1 if i == 0 else (dig(t[0]) if i == 1 and (dig(t[0]) or 0) >= 2 else None)
    ones = 0 if i == len(t) - 1 else (dig(t[-1]) if i == len(t) - 2 and (dig(t[-1]) or 0) >= 1 else None)
    return None if tens is None or ones is None else tens * 10 + ones
def number(t):
    if t.endswith("点半"):
        v = cn(t[:-2]); return f"half past {ONES[v]}" if v and 1 <= v <= 12 else None
    for u, one, many in UNITS:
        if t.endswith(u):
            v = cn(t[:-len(u)])
            if v is None or (u == "点" and not 1 <= v <= 12): return None
            n = ONES[v] if v < 20 else TENS[v // 10] + ("-" + ONES[v % 10] if v % 10 else "")
            return f"{n} {one if v == 1 else many}"
    return None
def gloss(zh, t):
    g = zh.get(t)
    if g is not None: return g.lstrip('~+') if not (len(t) == 1 and g.startswith('~')) else g[1:]
    if len(t) < 2: return None
    s, last = t[:-1], t[-1]
    if len(s) >= 2 and (last in MODAL or last in '吗么'):
        b = zh.get(s)
        if b is None or b.startswith('~'): b = number(s)
        if b is None: return None
        b = b.lstrip('+'); return b.rstrip('?!') + '?' if last in '吗么' else b
    return number(t)
def load(p):
    zh = {}
    for l in open(p, encoding='utf-8'):
        if l.startswith('#'): continue
        k, v = l.rstrip('\n').split('\t', 1)
        if not k.isascii(): zh[k] = v
    return zh
