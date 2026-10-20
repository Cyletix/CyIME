"""把机器翻译的英文再译回中文（opus-mt-en-zh），输出 短语\t英文\t回译。可断点续跑。
用法：backtranslate.py mt.tsv out.tsv min_score threads"""
import sys, os, torch
from transformers import MarianMTModel, MarianTokenizer
src, out, min_s, threads = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4])
torch.set_num_threads(threads)
done = set()
if os.path.exists(out): done = {l.split('\t')[0] for l in open(out, encoding='utf-8')}
rows = [l.rstrip('\n').split('\t') for l in open(src, encoding='utf-8')]
rows = [(t, e) for t, e, s in rows if float(s) >= min_s and t not in done]
name = 'Helsinki-NLP/opus-mt-en-zh'
tok = MarianTokenizer.from_pretrained(name); model = MarianMTModel.from_pretrained(name).eval()
B = 64
with open(out, 'a', encoding='utf-8', newline='\n') as f, torch.inference_mode():
    for i in range(0, len(rows), B):
        batch = rows[i:i+B]
        enc = tok([e for _, e in batch], return_tensors='pt', padding=True)
        gen = model.generate(**enc, num_beams=4, max_new_tokens=24)
        for (t, e), z in zip(batch, tok.batch_decode(gen, skip_special_tokens=True)):
            f.write(f"{t}\t{e}\t{z.strip()}\n")
        f.flush()
        if i // B % 20 == 0: print(f"{i + len(batch)}/{len(rows)}", flush=True)
