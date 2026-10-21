"""按用户实际打字的词频，抽显示出来的释义（同一词只抽一次）。用法：sample_shown.py gloss.tsv typed_words.tsv N seed"""
import sys, random
g = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v.lstrip('~+')
rows = [(w, int(c)) for w, c in (l.rstrip('\n').split('\t') for l in open(sys.argv[2], encoding='utf-8')) if w in g]
random.seed(int(sys.argv[4])); out = []
words = [w for w, _ in rows]; wts = [c for _, c in rows]
while len(out) < int(sys.argv[3]):
    w = random.choices(words, wts)[0]
    if w not in out: out.append(w)
for i, w in enumerate(out, 1): print(f"{i}\t{w}\t{g[w]}")
