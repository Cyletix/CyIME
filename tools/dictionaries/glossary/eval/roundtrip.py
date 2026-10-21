"""可逆检查：中文词的英文释义，再用同一词典按英文查回中文，能否回到原词。用法：roundtrip.py gloss.tsv typed_words.tsv replay.jsonl..."""
import sys, json, re, random, collections
zh, en = {}, {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t', 1); v = v.lstrip('~+')
    (en if k.isascii() else zh)[k] = v
def back(word):
    g = zh.get(word)
    if not g: return None, None
    b = en.get(g.lower().strip())
    if b is None: return 'none', g
    if b == word: return 'exact', g
    parts = re.split(r'[，,；;、/ ]+', b)
    return ('contains' if word in parts or word in b else 'other'), g
def report(name, items):
    c = collections.Counter(); tot = 0
    for w, n in items:
        r, g = back(w)
        if r is None: continue
        c[r] += n; tot += n
    print(name, 'shown', tot, ' '.join(f"{k} {c[k]/tot*100:.1f}%" for k in ('exact','contains','other','none')))
typed = [(w, int(c)) for w, c in (l.rstrip('\n').split('\t') for l in open(sys.argv[2], encoding='utf-8'))]
report('用户用词(按词次)', typed)
report('用户用词(两字以上)', [(w, c) for w, c in typed if len(w) > 1])
occ = collections.Counter()
for p in sys.argv[3:]:
    for l in open(p, encoding='utf-8'):
        if '"rows"' in l:
            for r in json.loads(l)['rows'][:5]:
                if not r['text'].isascii(): occ[r['text']] += 1
report('候选栏回放(按出现)', occ.items())
random.seed(4)
ws = [w for w, _ in occ.items() if back(w)[0] in ('other', 'none')]
for w in random.sample(ws, 25): r, g = back(w); print(f"  {w} -> {g} -> {en.get(g.lower().strip(), '(查不到)')}")
