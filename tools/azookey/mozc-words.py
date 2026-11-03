# Mozc words the azooKey dictionary lacks, as TSV for SuppTool build (see build-dictionary.sh).
# Kept: nouns (names, places, organisations, common, サ変, 形容動詞語幹, 副詞可能 nouns) written with
# kanji or letters; azooKey conjugates verbs and adjectives itself and offers every reading in kana.
# POS and value come from the words both dictionaries have: the most common azooKey (lcid, rcid, mid)
# for each Mozc POS id, and a linear fit of azooKey values on Mozc costs, minus 1 so these words rank
# below the dictionary's own words of the same frequency.
# Usage: python mozc-words.py <mozc dictionary_oss dir> <azooKey dump.tsv> <charID.chid> <out.tsv>
import sys, collections, glob, re
mozc_dir, dump, chid, out = sys.argv[1:5]
chars = set(open(chid, encoding='utf-8').read())
def kata(s):
    return ''.join(chr(ord(c) + 0x60) if 0x3041 <= ord(c) <= 0x3096 else c for c in s)
pos = {}
for line in open(mozc_dir + '/id.def', encoding='utf-8'):
    i, p = line.split(' ', 1); pos[int(i)] = p.strip().split(',')
KEEP = {('名詞', '固有名詞'), ('名詞', '一般'), ('名詞', 'サ変接続'), ('名詞', '形容動詞語幹'), ('名詞', '副詞可能')}
KANA = re.compile(r'^[\u3040-\u30ffー・]+$')
azk = {}
for line in open(dump, encoding='utf-8'):
    f = line.rstrip('\n').split('\t')
    if len(f) != 6: continue
    azk.setdefault((f[0], f[1]), (int(f[2]), int(f[3]), int(f[4]), float(f[5])))
mozc = []
for p in sorted(glob.glob(mozc_dir + '/dictionary0*.txt')):
    for line in open(p, encoding='utf-8'):
        f = line.rstrip('\n').split('\t')
        if len(f) != 5: continue
        mozc.append((kata(f[0]), f[4], int(f[1]), int(f[2]), int(f[3])))
lid_map = collections.defaultdict(collections.Counter)
pairs = []
for ruby, word, lid, rid, cost in mozc:
    a = azk.get((ruby, word))
    if a:
        lid_map[lid][(a[0], a[1], a[2])] += 1
        pairs.append((cost, a[3]))
# Linear fit value = a + b*cost.
n = len(pairs); mx = sum(c for c, _ in pairs) / n; my = sum(v for _, v in pairs) / n
b = sum((c - mx) * (v - my) for c, v in pairs) / sum((c - mx) ** 2 for c, _ in pairs); a = my - b * mx
print('overlap', n, 'fit a=%.3f b=%.6f' % (a, b), file=sys.stderr)
rows = {}
skipped = collections.Counter()
for ruby, word, lid, rid, cost in mozc:
    if (ruby, word) in azk: skipped['present'] += 1; continue
    if not ruby or not set(ruby) <= chars or '\t' in word: skipped['chars'] += 1; continue
    if KANA.match(word): skipped['kana-only'] += 1; continue
    if tuple(pos[lid][:2]) not in KEEP: skipped['not-noun'] += 1; continue
    m = lid_map.get(lid)
    if not m or sum(m.values()) < 3: skipped['pos'] += 1; continue
    (lcid, rcid, mid), _ = m.most_common(1)[0]
    value = a + b * cost - 1.0
    key = (ruby, word)
    if key not in rows or rows[key][3] < value: rows[key] = (lcid, rcid, mid, value)
print('kept', len(rows), dict(skipped), file=sys.stderr)
with open(out, 'w', encoding='utf-8') as o:
    for (ruby, word), (l, r, m, v) in sorted(rows.items()):
        o.write('%s\t%s\t%d\t%d\t%d\t%.2f\n' % (ruby, word, l, r, m, v))
