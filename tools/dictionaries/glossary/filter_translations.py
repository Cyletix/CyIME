"""筛机器翻译的常见短语：只收把握度高的，去掉含数字量词的、多字只译出一个词的；去句号、普通词改小写。
v6 词典第 3 步：结果交给 build_glossary.py --mt-phrases。
用法：filter_translations.py mt.tsv gloss.tsv ecdict.csv out.tsv"""
import sys, re, csv
mt, gloss_path, ecdict, out = sys.argv[1:5]
g = {}
for l in open(gloss_path, encoding='utf-8'):
    if l.startswith('#'): continue
    k, v = l.rstrip('\n').split('\t'); g[k] = v
common = set()
for r in csv.DictReader(open(ecdict, encoding='utf-8', newline='')):
    w = r['word']
    if w.islower() and re.fullmatch(r"[a-z']+", w) and (int(r['frq'] or 0) or int(r['bnc'] or 0)): common.add(w)
NUMERIC = re.compile(r'[0-9０-９一二三四五六七八九十百千万亿两几半岁]')
STOP = set("a an the of to in on at for and or is are be it this that i you he she we they my your his her its our their not no do does did will would can could".split())
def stems(text):
    return {w[:4] for w in re.findall(r"[a-z]+", text.lower()) if w not in STOP and len(w) > 2}
def parts_gloss(t):
    """词典里组成这个短语的词（两字以上优先，单字只用可靠的）的英文。"""
    acc = set(); i = 0
    while i < len(t):
        for n in range(min(6, len(t) - i), 0, -1):
            v = g.get(t[i:i+n])
            if v is None or (n == len(t)) or (n == 1 and v.startswith('~')): continue
            acc |= stems(v.lstrip('~+')); i += n; break
        else: i += 1
    return acc
def clean(e):
    e = e.strip().rstrip('.!。,').strip()
    words = []
    for i, w in enumerate(e.split(' ')):
        base = re.sub(r"[^A-Za-z']", '', w).split("'")[0]
        # 模型爱把短语首字母、标题词大写（Full Screen、New Year's gift）：普通词一律改小写，专名和 I 保留。
        if w != 'I' and base[:1].isupper() and base[1:].islower() and (base.lower() in common or base.lower().rstrip('s') in common):
            w = w[:1].lower() + w[1:]
        words.append(w)
    return ' '.join(words)
kept = 0; rows = []
for l in open(mt, encoding='utf-8'):
    t, e, s = l.rstrip('\n').split('\t'); s = float(s)
    e2 = clean(e)
    words = e2.split()
    if not e2 or not re.fullmatch(r"[A-Za-z0-9' ,?\-]+", e2) or len(words) > 6: continue
    toks = re.findall(r"[a-z]+", e2.lower())
    if len(toks) != len(set(toks)) and len(toks) > 3: continue           # 重复（越想越…）
    # 含数字、量词、年龄的短语（八岁 → eight，三人 → three）模型常丢掉一半意思，一律不收。
    if NUMERIC.search(t): continue
    # 中文三个字以上却只译出一个词，多半丢了意思（脸形 → face）。
    if len(t) >= 3 and len(words) == 1: continue
    # 只收模型很有把握的：把握度中等、靠词典对照放进来的那批错得最多（别以为 → don't think so，
    # 北京现代 → Beijing modern，超级玛丽 → super mary），宁可不显示。
    ok = s >= -0.25
    if ok: rows.append((t, e2, s)); kept += 1
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    for t, e, s in rows: f.write(f"{t}\t{e}\t{s:.4f}\n")
print('kept', kept)
