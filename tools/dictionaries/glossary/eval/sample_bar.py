"""候选栏里（回放每键前 5 个候选）实际显示出来的释义，随机抽 N 个不同的。用法：sample_bar.py gloss.tsv N seed replay.jsonl..."""
import sys, json, random
g = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v.lstrip('~+')
occ = []
for p in sys.argv[4:]:
    for l in open(p, encoding='utf-8'):
        if '"rows"' not in l: continue
        for r in json.loads(l)['rows'][:5]:
            if r['text'] in g and not r['text'].isascii(): occ.append(r['text'])
random.seed(int(sys.argv[3])); out = []
while len(out) < int(sys.argv[2]):
    w = random.choice(occ)
    if w not in out: out.append(w)
print(' | '.join(f"{w}:{g[w]}" for w in out))
