"""v7 词典第 3 步：只收“英文译回中文能回到原短语”的机器翻译（回译见 backtranslate.py），再做与 filter_translations 相同的清理。
把握度并不可靠（垃圾信息 曾被高把握地译成一句程序报错），回译一致才说明英文没跑偏。
用法：roundtrip_filter.py bt.tsv ecdict.csv out.tsv"""
import sys, re, csv
bt, ecdict, out = sys.argv[1:4]
common = set()
for r in csv.DictReader(open(ecdict, encoding='utf-8', newline='')):
    w = r['word']
    if w.islower() and re.fullmatch(r"[a-z']+", w) and (int(r['frq'] or 0) or int(r['bnc'] or 0)): common.add(w)
SPOKEN = re.compile(r'[我你您他她它们咱的了吗吧呢啊呀嘛在是不没别很太也都就要会能想去来给把被让过着得这那哪谁什么怎]')
NUMERIC = re.compile(r'[0-9０-９一二三四五六七八九十百千万亿两几半岁]')
def norm(z): return re.sub(r"[。？?！!，,、 .]", "", z)
def clean(e):
    e = e.strip().rstrip('.!。,').strip()
    words = []
    for index, w in enumerate(e.split(' ')):
        base = re.sub(r"[^A-Za-z']", '', w).split("'")[0]
        # 只改句首的大写（Isn't it → isn't it）；后面的大写多是专名（in Sydney），保留。
        if index == 0 and w != 'I' and base[:1].isupper() and base[1:].islower() and (base.lower() in common or base.lower().rstrip('s') in common):
            w = w[:1].lower() + w[1:]
        words.append(w)
    return ' '.join(words)
kept = []
for l in open(bt, encoding='utf-8'):
    t, e, z = l.rstrip('\n').split('\t')
    back = norm(z)
    # 译回来正好是原短语，或只多一两个虚字（我累了 ← 我累了。）
    if not (back == t or (t in back and len(back) <= len(t) + 1)): continue
    if NUMERIC.search(t): continue
    # 只翻“说话用的短语”（带代词、虚词、助动词：我也没有、贵吗、进来吧）。不带这些的多是名词复合词，
    # 模型会按字面拆错（白天鹅 → day goose，鸭梨 → duck pear，主营 → main camp），交给词典。
    if not SPOKEN.search(t): continue
    e2 = clean(e)
    words = e2.split()
    if not e2 or not re.fullmatch(r"[A-Za-z0-9' ,?\-]+", e2) or len(words) > 6: continue
    if len(t) >= 3 and len(words) == 1: continue
    # 带地名、人名的不收：机器翻译专名最容易错（在青岛 → in Aoshima）。句首以外出现大写词就算专名。
    if any(w[:1].isupper() and w != 'I' and not w.startswith("I'") for w in words[1:]): continue
    kept.append((t, e2))
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    for t, e in kept: f.write(f"{t}\t{e}\t0\n")
print('kept', len(kept))
