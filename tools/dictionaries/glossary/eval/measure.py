"""量候选栏前 N 个候选里有英文释义的比例，按长度、类型分类。用法：measure.py gloss.tsv replay.jsonl... """
import sys, json, collections
gloss = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); gloss[k] = v
TOP = 5
tot = collections.Counter(); hit = collections.Counter(); miss = collections.Counter()
seen = set()
for path in sys.argv[2:]:
    for i, l in enumerate(open(path, encoding='utf-8')):
        if '"rows"' not in l: continue
        d = json.loads(l)
        for r in d['rows'][:TOP]:
            t = r['text']
            if not t or all(ord(c) < 128 for c in t): continue
            n = len(t)
            cat = '1字' if n == 1 else '2字' if n == 2 else '3-4字' if n <= 4 else '5字以上'
            cat = (r.get('type') or '?') + '/' + cat
            tot[cat] += 1; tot['全部'] += 1
            if t in gloss: hit[cat] += 1; hit['全部'] += 1
            else: miss[t] += 1
for k in sorted(tot, key=lambda k: -tot[k]):
    print(f"{k:16s} {tot[k]:7d} 有英文 {hit[k]/tot[k]*100:5.1f}%")
print('最常缺：', ' '.join(f"{t}({c})" for t, c in miss.most_common(80)))
