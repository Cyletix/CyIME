import sys, json, collections
g = {}
for l in open(sys.argv[1], encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v
w8105 = {}
body=False
for l in open('app/build/generated/chinese-assets/rime_ice/cn_dicts/8105.dict.yaml', encoding='utf-8'):
    if l.strip()=='...': body=True; continue
    if not body or l.startswith('#') or not l.strip(): continue
    p=l.rstrip('\n').split('\t')
    if len(p)>=3: w8105[p[0]]=max(w8105.get(p[0],0),int(p[2]))
cnt=collections.Counter(); 
for path in sys.argv[2:]:
    for l in open(path, encoding='utf-8'):
        if '"rows"' not in l: continue
        for r in json.loads(l)['rows'][:5]:
            t=r['text']
            if len(t)==1 and '\u4e00'<=t<='\u9fff': cnt[t]+=1
ranked=sorted(w8105,key=lambda c:-w8105[c]); rank={c:i for i,c in enumerate(ranked)}
tot=sum(cnt.values()); print('single occurrences',tot,'distinct',len(cnt))
b=collections.Counter()
for c,n in cnt.items():
    r=rank.get(c,99999); band='<500' if r<500 else '<1500' if r<1500 else '<3500' if r<3500 else 'rare'
    v=g.get(c); kind='none' if v is None else 'approx' if v.startswith('~') else 'ok'
    b[(band,kind)]+=n
for k in sorted(b): print(k,b[k],f"{b[k]/tot*100:.1f}%")
import random; random.seed(1)
for band in ['<500','<1500','<3500','rare']:
    xs=[c for c in cnt if (lambda r:('<500' if r<500 else '<1500' if r<1500 else '<3500' if r<3500 else 'rare'))(rank.get(c,99999))==band]
    print(band, ' | '.join(f"{c}:{g.get(c,'-')}" for c in random.sample(xs,min(25,len(xs)))))
