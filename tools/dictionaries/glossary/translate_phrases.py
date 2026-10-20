"""把常见短语池整批中译英（opus-mt-zh-en，CPU，低优先级）。输出 短语\t英文\t平均对数概率。可断点续跑。
v6 词典第 2 步，只在电脑上跑、不进 App；模型 Helsinki-NLP/opus-mt-zh-en，需 transformers、sentencepiece、sacremoses（放单独环境）。
上一步 phrase_pool.py，下一步 filter_translations.py。
用法：translate_phrases.py pool.tsv out.tsv min_weight threads"""
import sys, os, math, torch
from transformers import MarianMTModel, MarianTokenizer
pool, out, min_w, threads = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
torch.set_num_threads(threads)
done = set()
if os.path.exists(out):
    done = {l.split('\t')[0] for l in open(out, encoding='utf-8')}
items = [l.split('\t')[0] for l in open(pool, encoding='utf-8') if int(l.rstrip('\n').split('\t')[1]) >= min_w]
items = [t for t in items if t not in done]
name = 'Helsinki-NLP/opus-mt-zh-en'
tok = MarianTokenizer.from_pretrained(name); model = MarianMTModel.from_pretrained(name).eval()
B = 64
with open(out, 'a', encoding='utf-8', newline='\n') as f, torch.inference_mode():
    for i in range(0, len(items), B):
        batch = items[i:i+B]
        enc = tok(batch, return_tensors='pt', padding=True)
        gen = model.generate(**enc, num_beams=4, max_new_tokens=16, output_scores=True, return_dict_in_generate=True)
        texts = tok.batch_decode(gen.sequences, skip_special_tokens=True)
        scores = gen.sequences_scores.tolist()   # 已按长度归一的对数概率
        for t, e, s in zip(batch, texts, scores):
            f.write(f"{t}\t{e.strip()}\t{s:.4f}\n")
        f.flush()
        if i // B % 20 == 0: print(f"{i + len(batch)}/{len(items)}", flush=True)
