"""把训练好的手写公式模型（segments-rel-t）导出成手机能跑的 ONNX，并核对导出前后结果一致。

用法：
    python export_formula_onnx.py --ckpt …/best.pt --data D:/CyIME-Data/datasets/mathwriting/prepared --out <目录>

输出：
    formula_encoder.onnx  输入 P[1,S,16,6] 段内点、PM[1,S,16] 段内点掩码、C[1,S,8] 段特征、SM[1,S] 段掩码 → mem[1,S,256]
    formula_decoder.onnx  输入 mem[1,S,256]、SM[1,S]、y[1,T]（已生成的记号，首个为 <bos>）→ logits[1,V]（最后一位的下一个记号）
    formula_vocab.json    记号表；formula_meta.json 预处理参数（App 端要照同样的规则把笔迹变成段）
核对：在验证集抽样上分别用 PyTorch 和 onnxruntime 贪心解码，逐条比较输出记号。
模型只放在本机和自己的设备上（训练数据 CC BY-NC-SA 4.0）。
"""
from __future__ import annotations

import argparse
import json
import pathlib
import random
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import formula_arch_ablation as abl  # noqa: E402
import mathwriting_baseline as base  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--check", type=int, default=100)
    args = ap.parse_args()

    import onnxruntime as ort
    import torch

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    st = torch.load(args.ckpt, map_location="cpu")
    arch, vocab = st["arch"], st["vocab"]
    model = abl.build_model(arch, len(vocab))
    model.load_state_dict(st["model"])
    model.eval()
    for p in model.parameters():
        p.requires_grad_(False)

    import math
    import torch.nn.functional as F

    # 旧版 ONNX 导出器在 nn.MultiheadAttention 里会把序列长度写死成示例输入的长度，
    # 所以导出时用同一份权重、形状全用 -1 的手写注意力重算一遍（只支持一次一条，手机上就是一次一条）。
    def attn(mha, q_in, kv_in, bias):
        d = mha.embed_dim
        H = mha.num_heads
        W, b = mha.in_proj_weight, mha.in_proj_bias
        q = F.linear(q_in, W[:d], b[:d]).reshape(1, -1, H, d // H).transpose(1, 2)
        k = F.linear(kv_in, W[d:2 * d], b[d:2 * d]).reshape(1, -1, H, d // H).transpose(1, 2)
        v = F.linear(kv_in, W[2 * d:], b[2 * d:]).reshape(1, -1, H, d // H).transpose(1, 2)
        a = torch.softmax(q @ k.transpose(-1, -2) / math.sqrt(d // H) + bias, -1)
        return mha.out_proj((a @ v).transpose(1, 2).reshape(1, -1, d))

    def ff(layer, z):
        return layer.linear2(layer.activation(layer.linear1(z)))

    class Enc(torch.nn.Module):
        def forward(self, P, PM, C, SM):
            m = model
            h = m.pin(P.flatten(0, 1))
            h = m.pconv(h.transpose(1, 2)).transpose(1, 2)
            pm = PM.flatten(0, 1).unsqueeze(-1)
            mx = h.masked_fill(~pm, -1e4).amax(1)
            mx = torch.where(pm.any(1), mx, torch.zeros_like(mx))
            pmf = pm.to(h.dtype)
            mean = (h * pmf).sum(1) / pmf.sum(1).clamp_min(1)
            tok = m.pool(torch.cat([mx, mean], -1)).unsqueeze(0)
            tok = tok + m.seg(C) + m.pos(C[..., :2])
            pad = ((~SM).to(tok.dtype) * -1e4)[:, None, None, :]
            c = C[..., :4]
            dxy = c[:, None, :, :2] - c[:, :, None, :2]
            dwh = c[:, None, :, 2:4] - c[:, :, None, 2:4]
            feats = torch.cat([dxy, dxy.abs(), dwh], -1)
            x = tok
            for i, layer in enumerate(m.layers):
                bias = m.rel[i](feats).permute(0, 3, 1, 2) + pad
                y = layer.norm1(x)
                x = x + attn(layer.self_attn, y, y, bias)
                x = x + ff(layer, layer.norm2(x))
            return m.enc_norm(x)

    class Dec(torch.nn.Module):
        def forward(self, mem, SM, y):
            d = model.dec
            pos = torch.ones_like(y).cumsum(1) - 1
            x = d.emb(y) + d.tpos(pos)
            causal = ((pos[:, None, :] > pos[:, :, None]).to(x.dtype) * -1e4)[:, None]
            mem_bias = ((~SM).to(x.dtype) * -1e4)[:, None, None, :]
            for layer in d.dec.layers:
                z = layer.norm1(x)
                x = x + attn(layer.self_attn, z, z, causal)
                x = x + attn(layer.multihead_attn, layer.norm2(x), mem, mem_bias)
                x = x + ff(layer, layer.norm3(x))
            return d.out(d.norm(x[:, -1]))

    valid = base.Split(pathlib.Path(args.data), "valid")
    x0, y0 = valid.item(0)
    (P, PM, C, SM), _ = abl.collate(arch, [(x0, y0)], "cpu", time_drop=0.0)
    enc_path, dec_path = out / "formula_encoder.onnx", out / "formula_decoder.onnx"
    torch.onnx.export(Enc(), (P, PM, C, SM), str(enc_path), input_names=["P", "PM", "C", "SM"], output_names=["mem"],
                      dynamic_axes={"P": {1: "S"}, "PM": {1: "S"}, "C": {1: "S"}, "SM": {1: "S"}, "mem": {1: "S"}},
                      opset_version=17, dynamo=False)
    mem = Enc()(P, PM, C, SM)
    y = torch.tensor([[base.BOS, 5, 6]])
    torch.onnx.export(Dec(), (mem, SM, y), str(dec_path), input_names=["mem", "SM", "y"], output_names=["logits"],
                      dynamic_axes={"mem": {1: "S"}, "SM": {1: "S"}, "y": {1: "T"}}, opset_version=17, dynamo=False)
    (out / "formula_vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    meta = {"arch": arch, "seg_len": abl.SEG_LEN, "point_feat": abl.FEAT, "seg_feat": abl.SEG_FEAT + abl.TIME_FEAT,
            "max_points": base.MAX_POINTS, "max_tokens": base.MAX_TOKENS, "min_step": 0.015, "wide_ratio": 8.0,
            "bos": base.BOS, "eos": base.EOS, "pad": base.PAD}
    (out / "formula_meta.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")

    enc = ort.InferenceSession(str(enc_path), providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(str(dec_path), providers=["CPUExecutionProvider"])

    def ort_greedy(P, PM, C, SM):
        m = enc.run(None, {"P": P, "PM": PM, "C": C, "SM": SM})[0]
        seq = [base.BOS]
        for _ in range(base.MAX_TOKENS):
            lg = dec.run(None, {"mem": m, "SM": SM, "y": np.asarray([seq], np.int64)})[0][0]
            t = int(lg.argmax())
            if t == base.EOS:
                break
            seq.append(t)
        return seq[1:]

    rnd = random.Random(0)
    same = exact_t = exact_o = 0
    idx = rnd.sample(range(valid.n), args.check)
    for i in idx:
        x, gold = valid.item(i)
        inp, _ = abl.collate(arch, [(x, gold)], "cpu", time_drop=0.0)
        with torch.no_grad():
            t = abl.greedy(model, inp)[0]
        o = ort_greedy(*(a.numpy() for a in inp))
        same += t == o
        exact_t += t == gold.tolist()
        exact_o += o == gold.tolist()
    sizes = {p.name: round(p.stat().st_size / 2**20, 1) for p in (enc_path, dec_path)}
    print(f"导出 {sizes} MB；{args.check} 条验证样本：PyTorch 与 ONNX 输出一致 {same}，整条全对 PyTorch {exact_t} / ONNX {exact_o}")
    return 0 if same == args.check else 1


if __name__ == "__main__":
    sys.exit(main())
