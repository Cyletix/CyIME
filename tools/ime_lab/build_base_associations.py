"""Reproducible, bounded LCCC word-following baseline. Never writes user history."""
import argparse, collections, gzip, hashlib, json, re
from pathlib import Path

HAN = re.compile(r"[\u4e00-\u9fff]{1,6}\Z")
def transitions(sentence):
    context = ""
    for token in sentence.split():
        if not HAN.fullmatch(token):
            context = ""
            continue
        for size in range(1, min(4, len(context)) + 1):
            yield context[-size:], token
        context = (context + token)[-4:]

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("source",type=Path);ap.add_argument("output",type=Path)
    args=ap.parse_args()
    # Deterministic spread across the training split; no validation/test sentences used.
    counts=collections.Counter(); dialogs=sentences=0
    with gzip.open(args.source,"rt",encoding="utf-8") as f:
        for index,line in enumerate(f):
            if index % 34: continue
            dialogs+=1
            for sentence in json.loads(line):
                sentences+=1; counts.update(transitions(sentence))
    totals=collections.Counter()
    for (ctx,word),count in counts.items():
        if count>=8: totals[ctx]+=count
    contexts={ctx for ctx,count in sorted(totals.items(),key=lambda x:(-x[1],x[0]))[:40000] if count>=20}
    groups=collections.defaultdict(list)
    for (ctx,word),count in counts.items():
        if ctx in contexts and count>=8:groups[ctx].append((word,count))
    rows=[]
    for ctx in sorted(groups):
        for word,count in sorted(groups[ctx],key=lambda x:(-x[1],x[0]))[:6]:
            rows.append(f"{ctx}\t{word}\t{count}\t{totals[ctx]}\n")
    data=("# cyime-base-associations-v1\n"+"".join(rows)).encode("utf-8")
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(gzip.compress(data,mtime=0))
    manifest={"source":"https://huggingface.co/datasets/thu-coai/lccc", "license":"MIT", "source_sha256":hashlib.file_digest(args.source.open("rb"),"sha256").hexdigest(),"split":"train only", "sampling":"zero-based dialogue index modulo 34 == 0", "dialogs":dialogs,"sentences":sentences,"contexts":len(groups),"entries":len(rows),"bytes":args.output.stat().st_size,"sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),"purpose":"post-commit word association baseline, not a pinyin decoder or personal history", "validation":"file integrity only; quality evaluation separate"}
    args.output.with_suffix(".manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(manifest,ensure_ascii=False),flush=True)
if __name__=="__main__":main()
