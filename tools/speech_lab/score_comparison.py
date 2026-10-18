"""Score frozen human references; bootstrap paired clip differences, never model-generated labels."""
import argparse, importlib.util, json, re, unicodedata
from pathlib import Path
import numpy as np

# Use the already installed, Apache-2.0 Whisper normalizer (numbers/contractions).
NORMALIZER = Path('D:/GitHub/ASRInput/training/.venv/Lib/site-packages/transformers/models/whisper/english_normalizer.py')
spec=importlib.util.spec_from_file_location('whisper_normalizer',NORMALIZER)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
english=module.EnglishTextNormalizer({})


def units(text, language):
    text=re.sub(r'<\|.*?\|>|<(?:en-US|en-GB|ja-JP|zh-CN)>','',text)
    if language=='en':return english(text).split()
    text=unicodedata.normalize('NFKC',text).lower()
    return [c for c in text if unicodedata.category(c)[0] in 'LN']


def distance(reference,hypothesis):
    row=list(range(len(hypothesis)+1))
    for i,a in enumerate(reference,1):
        next_row=[i]
        for j,b in enumerate(hypothesis,1):next_row.append(min(next_row[-1]+1,row[j]+1,row[j-1]+(a!=b)))
        row=next_row
    return row[-1]


def aggregate(folder):
    reports={}
    for path in folder.glob('*.json'):
        r=json.loads(path.read_text(encoding='utf8'))
        if not r.get('complete'):continue
        perlang={}
        for row in r['clips']:
            lang=row['language'];ref=units(row['reference'],lang);hyp=units(row['text'],lang)
            perlang.setdefault(lang,[]).append(dict(**row,errors=distance(ref,hyp),reference_units=len(ref)))
        reports[r['model']]={}
        for lang,rows in perlang.items():
            errors=sum(x['errors'] for x in rows);length=sum(x['reference_units'] for x in rows)
            reports[r['model']][lang]=dict(n=len(rows),metric='WER' if lang=='en' else 'CER',errors=errors,
                reference_units=length,error_rate=errors/length,
                audio_s=sum(x['duration_s'] for x in rows),decode_s=sum(x['decode_s'] for x in rows),
                rtf=sum(x['decode_s'] for x in rows)/sum(x['duration_s'] for x in rows),
                median_decode_s=float(np.median([x['decode_s'] for x in rows])),
                p95_decode_s=float(np.percentile([x['decode_s'] for x in rows],95)),
                empty=sum(not units(x['text'],lang) for x in rows),
                load_s=r.get('load_s'),peak_rss_mib=r.get('peak_rss_mib'),baseline_rss_mib=r.get('baseline_rss_mib'),
                model_bytes=r.get('model_bytes'),streaming=r.get('streaming'),clips=rows)
    return reports


def paired(reports,first,second,lang):
    a=reports[first][lang]['clips'];b={x['id']:x for x in reports[second][lang]['clips']}
    pairs=[(x,b[x['id']]) for x in a if x['id'] in b]
    n=np.array([x['reference_units'] for x,y in pairs]);d=np.array([y['errors']-x['errors'] for x,y in pairs])
    rng=np.random.default_rng(20261007);idx=rng.integers(0,len(n),(10000,len(n)))
    changes=d[idx].sum(axis=1)/n[idx].sum(axis=1)*100
    return dict(first=first,second=second,language=lang,n=len(pairs),
        improved=int(sum(d<0)),worsened=int(sum(d>0)),same=int(sum(d==0)),
        delta_percentage_points=float(d.sum()/n.sum()*100),ci95_percentage_points=np.percentile(changes,[2.5,97.5]).tolist(),
        changed=[dict(id=x['id'],reference=x['reference'],before=x['text'],after=y['text'],before_errors=x['errors'],after_errors=y['errors'])
                 for x,y in pairs if x['errors']!=y['errors']])


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('folder',type=Path);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--additional-folder',type=Path,action='append',default=[])
    p.add_argument('--production-zh',type=Path);p.add_argument('--production-ja',type=Path);a=p.parse_args()
    reports=aggregate(a.folder);pairs=[]
    for folder in a.additional_folder:
        addition=aggregate(folder)
        assert not (reports.keys() & addition.keys()), 'Duplicate model result'
        reports.update(addition)
    for first,second,lang in [('paraformer','sense-auto','zh'),('paraformer','sense-zh','zh'),
        ('zip-ja-int8','sense-auto','ja'),('zip-ja-fp32','sense-auto','ja'),('zip-ja-int8','zip-ja-fp32','ja'),
        ('zip-zh','zip-zh-xl','zh'),('paraformer','zip-zh-xl','zh'),('sense-auto','qwen3','ja'),
        ('zip-ja-fp32','qwen3','ja'),('sense-auto','nemotron','en'),('sense-auto','qwen3','zh'),
        ('sense-auto','nemotron','zh'),('sense-auto','nemotron','ja'),('sense-auto','qwen3','en'),
        ('paraformer','zip-2026-stream-ctc','zh'),('paraformer','zip-2026-stream-rnnt','en'),
        ('sense-auto','zip-ja-ctc','ja'),('sense-ja','zip-ja-fp32','ja'),
        ('sense-auto','funasr-nano','zh'),('sense-auto','funasr-nano','ja'),('sense-auto','funasr-nano','en')]:
        if first in reports and second in reports:pairs.append(paired(reports,first,second,lang))
    production={};production_pairs=[]
    for lang,path in [('zh',a.production_zh),('ja',a.production_ja)]:
        if path is None:continue
        source=json.loads(path.read_text(encoding='utf8'));assert source['complete']
        for mode,field,name in ([('single','first','prod-para-single'),('refinement','first','prod-para-preview'),('refinement','second','prod-para-sense')]
            if lang=='zh' else [('int8','first','prod-ja-int8'),('fp32','first','prod-ja-fp32')]):
            rows=[]
            for row in source['clips']:
                if row['mode']!=mode:continue
                rows.append(dict(**row,text=row[field],errors=distance(units(row['reference'],lang),units(row[field],lang)),reference_units=len(units(row['reference'],lang))))
            production[name]={lang:dict(n=len(rows),clips=rows,error_rate=sum(x['errors'] for x in rows)/sum(x['reference_units'] for x in rows))}
    combined={**reports,**production}
    for first,second,lang in [('prod-para-single','prod-para-sense','zh'),('prod-para-preview','prod-para-sense','zh'),
        ('prod-ja-int8','prod-ja-fp32','ja'),('prod-ja-fp32','sense-ja','ja'),('prod-ja-int8','sense-auto','ja'),
        ('prod-ja-fp32','funasr-nano','ja'),('prod-ja-int8','funasr-nano','ja')]:
        if first in combined and second in combined:production_pairs.append(paired(combined,first,second,lang))
    # Sensitivity analysis after observing an ITN mismatch: retain only references
    # without any Arabic or CJK numeral. This subset is not a new primary benchmark.
    numeral=re.compile(r'[\d〇零一二三四五六七八九十百千万萬亿億兆]')
    sensitivity={}
    for name,languages in combined.items():
        sensitivity[name]={}
        for lang,r in languages.items():
            if lang=='en':continue
            rows=[x for x in r['clips'] if not numeral.search(x['reference'])]
            sensitivity[name][lang]=dict(n=len(rows),error_rate=sum(x['errors'] for x in rows)/sum(x['reference_units'] for x in rows),clips=rows)
    sensitivity_pairs=[paired(sensitivity,p['first'],p['second'],p['language']) for p in pairs+production_pairs if p['language']!='en']
    a.output.write_text(json.dumps(dict(reports=reports,pairs=pairs,no_numeral_reference=sensitivity,
        no_numeral_pairs=sensitivity_pairs,production=production,production_pairs=production_pairs),ensure_ascii=False,indent=2),encoding='utf8')
    for lang in ['zh','ja','en']:
        print(lang)
        for name,r in sorted([(name,r[lang]) for name,r in reports.items() if lang in r],key=lambda x:x[1]['error_rate']):
            print(name,round(100*r['error_rate'],2),'%',round(r['rtf'],3),'RTF',round(r['peak_rss_mib'] or 0),'MiB')
    for p in pairs:print(p['first'],'->',p['second'],p['language'],'better/worse/equal',p['improved'],p['worsened'],p['same'])


if __name__=='__main__':main()
