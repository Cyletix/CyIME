"""用户实际打的词（typed_words.tsv：词\t次数）里有英文释义的比例。用法：typed_cov.py gloss.tsv typed_words.tsv [缺词个数]"""
import sys, collections
g = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v
tot = collections.Counter(); hit = collections.Counter(); approx = collections.Counter(); miss = []
for l in open(sys.argv[2], encoding='utf-8'):
    w, c = l.rstrip('\n').split('\t'); c = int(c)
    cat = '单字' if len(w) == 1 else '两字' if len(w) == 2 else '三字以上'
    tot[cat] += c; tot['全部'] += c
    v = g.get(w)
    if v is not None and not (len(w) == 1 and v.startswith('~')):
        hit[cat] += c; hit['全部'] += c
    elif v is not None: approx[cat] += c
    elif len(w) > 1: miss.append((c, w))
for k in ['单字', '两字', '三字以上', '全部']:
    print(f"{k} 词次 {tot[k]} 有可靠英文 {hit[k]/tot[k]*100:.1f}%  只有~ {approx[k]/tot[k]*100:.1f}%")
n = int(sys.argv[3]) if len(sys.argv) > 3 else 0
if n: print(' '.join(f"{w}({c})" for c, w in sorted(miss, reverse=True)[:n]))
