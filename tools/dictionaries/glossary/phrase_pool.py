"""待翻译的常见短语池：rime_ice 里 2~6 字、没有整词英文（或只有逐词拼的 +）的条目，按权重排。量：假设都翻好，回放候选覆盖能到多少。"""
import sys, json, collections
g = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v
D = 'app/build/generated/chinese-assets/rime_ice/cn_dicts/'
lex = {}
for f in ['base', 'ext', 'tencent']:
    body = False
    for l in open(D + f + '.dict.yaml', encoding='utf-8'):
        if l.startswith('...'): body = True; continue
        if not body or l.startswith('#') or not l.strip(): continue
        p = l.rstrip('\n').split('\t')
        w = int(p[2]) if len(p) > 2 and p[2].isdigit() else 0
        lex[p[0]] = max(lex.get(p[0], 0), w)
pool = sorted(((w, t) for t, w in lex.items() if 2 <= len(t) <= 6 and all('\u4e00' <= c <= '\u9fff' for c in t)
               and (t not in g or g[t].startswith('+'))), reverse=True)
rows = []
for path in sys.argv[2:]:
    for l in open(path, encoding='utf-8'):
        if '"rows"' not in l: continue
        rows += [r['text'] for r in json.loads(l)['rows'][:5] if r['text'] and not all(ord(c) < 128 for c in r['text'])]
base = sum(1 for t in rows if t in g and not g[t].startswith('+'))
for W in [100000, 20000, 5000, 1000, 200]:
    s = {t for w, t in pool if w >= W}
    cov = sum(1 for t in rows if (t in g and not g[t].startswith('+')) or t in s)
    print(f"weight>={W:6d} phrases {len(s):7d}  coverage {cov/len(rows)*100:5.1f}%")
print('whole-word only', f"{base/len(rows)*100:.1f}%")
with open('.codex-artifacts/gloss-coverage/phrase_pool.tsv', 'w', encoding='utf-8', newline='\n') as out:
    for w, t in pool:
        if w >= 1000: out.write(f"{t}\t{w}\n")
