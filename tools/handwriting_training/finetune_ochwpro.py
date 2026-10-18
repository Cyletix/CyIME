"""用训练模式写的汉字样本微调内置中文手写模型 ochwpro，并比较微调前后的准确率。

用法（GPU 环境里运行，依赖 torch、numpy、onnx、onnxruntime、safetensors）：
    python tools/handwriting_training/finetune_ochwpro.py \
        --samples <训练模式导出.jsonl> [...更多导出文件] \
        --upstream D:/CyIME-Data/tmp/ochwpro-upstream \
        --out D:/CyIME-Data/handwriting_training/ochwpro-ft/<日期>

    只评估、不训练：加 --eval-only，比较的是原始 onnx 在留出样本上的准确率。

做法：
1. 从上游仓库 ochwpro/model.py 建同结构模型，载入 ochwpro/model_fp16.safetensors（与 App 内置
   模型同一份权重，启动时会用 App 的 onnx 对拍确认）。
2. 只取训练模式里 type=hanzi 的样本。每个字按写的时间排序，最后 --holdout 份留作测试，不参与训练。
3. 预处理和 App 一致（HandwritingInference：每笔最多 8 点，整字包围盒横竖分别归一化），
   训练时在简化前做上游同款增强（下采样、抖动、缩放、旋转）。
4. 防止其它字变差：只训练最后一层编码器、归一化和分类头；损失里加原模型输出的蒸馏项和
   与原权重的距离项（L2-SP）。可选 --anchors 传 Make Me a Hanzi 的 graphics.txt，
   用常用字的标准笔顺做额外的蒸馏和“其它字有没有退步”的检查。
5. 导出与 App 相同接口的 onnx（input[1,200,5] + mask[1,200] → logits），写入 --out，
   并输出 report.json：微调前后留出样本 top-1/top-5、锚点字与原模型的一致率。
"""
from __future__ import annotations

import argparse
import json
import math
import pathlib
import random
import sys

import numpy as np

FIXED_LEN = 200
MAX_POINTS_PER_STROKE = 8
APP_MODEL = pathlib.Path("D:/GitHub/CyIME/.gradle/bundled-model-cache/ochwpro")


# ── 样本 ──────────────────────────────────────────────────────────────

def load_samples(paths: list[str], sample_type: str) -> list[dict]:
    out = []
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                s = json.loads(line)
                if s.get("format") != "cyime-handwriting-training" or s.get("type") != sample_type:
                    continue
                strokes = [[(float(x), float(y)) for x, y, _t in st["points"]] for st in s["strokes"]]
                strokes = [st for st in strokes if st]
                if strokes:
                    out.append({"id": s["id"], "label": s["label"], "strokes": strokes,
                                "t": s.get("created_at_ms", 0)})
    # 重复导出的同一样本只算一次
    return list({s["id"]: s for s in out}.values())


def split_holdout(samples: list[dict], holdout: int) -> tuple[list[dict], list[dict]]:
    by_label: dict[str, list[dict]] = {}
    for s in samples:
        by_label.setdefault(s["label"], []).append(s)
    train, test = [], []
    for items in by_label.values():
        items.sort(key=lambda s: s["t"])
        # 样本少时至少留 1 份（只有 1 份的字全部用来训练），否则没有可比的测试集
        k = min(holdout, max(1, len(items) // 4)) if len(items) >= 2 else 0
        train += items[:len(items) - k]
        test += items[len(items) - k:]
    return train, test


# ── 与 App 一致的预处理 ───────────────────────────────────────────────

def simplify(strokes):
    res = []
    for st in strokes:
        if len(st) <= MAX_POINTS_PER_STROKE:
            res.append(st)
        else:
            step = (len(st) - 1) / (MAX_POINTS_PER_STROKE - 1)
            res.append([st[min(max(int(round(i * step)), 0), len(st) - 1)] for i in range(MAX_POINTS_PER_STROKE)])
    return res


def to_sequence(strokes) -> tuple[np.ndarray, np.ndarray]:
    pts, pen = [], []
    for st in simplify(strokes):
        for p in st:
            pts.append(p)
            pen.append(1.0)
        if pen:
            pen[-1] = 0.0
    seq = np.zeros((FIXED_LEN, 5), np.float32)
    mask = np.zeros(FIXED_LEN, np.bool_)
    if not pts:
        return seq, mask
    a = np.asarray(pts, np.float32)
    mn = a.min(0)
    rng = np.maximum(a.max(0) - mn, 1.0)
    d = np.diff(a, axis=0, prepend=a[:1])
    t = min(len(a), FIXED_LEN)
    seq[:t, 0:2] = ((a - mn) / rng)[:t]
    seq[:t, 2:4] = (d / rng)[:t]
    seq[:t, 4] = np.asarray(pen, np.float32)[:t]
    mask[:t] = True
    return seq, mask


def shuffle_order(strokes, rnd: random.Random, p: float):
    """笔顺增强：以概率 p 打乱笔画先后，并随机把个别笔画倒着写，让模型不死认一种笔顺。"""
    if p <= 0 or rnd.random() >= p or len(strokes) < 2:
        return strokes
    out = list(strokes)
    # 只交换少数几对相邻笔画，更像真实的笔顺差异，而不是完全乱序
    for _ in range(rnd.randint(1, max(1, len(out) // 3))):
        i = rnd.randrange(len(out) - 1)
        out[i], out[i + 1] = out[i + 1], out[i]
    return [list(reversed(st)) if rnd.random() < 0.1 else st for st in out]


def augment(strokes, rnd: random.Random, order_p: float = 0.0):
    """上游 dataset._augment_strokes 同款：下采样、抖动、缩放、旋转；order_p>0 时再加笔顺增强。"""
    strokes = shuffle_order(strokes, rnd, order_p)
    ratio = rnd.uniform(0.15, 1.0)
    ds = []
    for st in strokes:
        n = len(st)
        if n <= 2:
            ds.append(st)
            continue
        keep = max(2, int(n * ratio))
        idx = sorted(set([0, n - 1] + [int(i * (n - 1) / (keep - 1)) for i in range(keep)]))[:keep]
        ds.append([st[i] for i in idx])
    allp = [p for st in ds for p in st]
    xs = [p[0] for p in allp]
    ys = [p[1] for p in allp]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    span = max(max(xs) - min(xs), max(ys) - min(ys), 1.0)
    noise = span * 0.02
    sx, sy = rnd.uniform(0.85, 1.15), rnd.uniform(0.85, 1.15)
    ang = math.radians(rnd.uniform(-15, 15))
    ca, sa = math.cos(ang), math.sin(ang)
    out = []
    for st in ds:
        jx, jy = rnd.uniform(-noise, noise), rnd.uniform(-noise, noise)
        new = []
        for x, y in st:
            x, y = (x + jx - cx) * sx, (y + jy - cy) * sy
            new.append((x * ca - y * sa + cx, x * sa + y * ca + cy))
        out.append(new)
    return out


# ── 锚点字（可选）：Make Me a Hanzi graphics.txt 的笔画中线 ─────────────

def load_anchors(path: str | None, chars: list[str], limit: int, exclude: set[str]) -> list[dict]:
    if not path:
        return []
    wanted = {c for c in chars if c and c not in exclude}
    found = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            g = json.loads(line)
            ch = g.get("character")
            if ch in wanted:
                # graphics.txt 坐标 y 向上（1024 方格，基线偏移 900），翻转成 y 向下
                found[ch] = [[(float(x), 900.0 - float(y)) for x, y in m] for m in g["medians"]]
    order = [c for c in chars if c in found][:limit]
    return [{"label": c, "strokes": found[c]} for c in order]


# ── 模型 ──────────────────────────────────────────────────────────────

def build_model(upstream: pathlib.Path, num_classes: int):
    import torch
    from safetensors.torch import load_file

    sys.path.insert(0, str(upstream))
    from ochwpro.model import StrokeTransformer  # type: ignore

    meta = json.loads((upstream / "ochwpro" / "model_meta.json").read_text(encoding="utf-8"))
    model = StrokeTransformer(num_classes=meta["num_classes"], d_model=meta["d_model"],
                              nhead=meta["nhead"], num_layers=meta["num_layers"])
    if meta["num_classes"] != num_classes:
        raise SystemExit(f"上游模型 {meta['num_classes']} 类，App 字表 {num_classes} 类，不匹配")
    state = {k: v.float() for k, v in load_file(str(upstream / "ochwpro" / "model_fp16.safetensors")).items()}
    model.load_state_dict(state)
    return model


def check_against_app_onnx(model, onnx_path: pathlib.Path, probes: list[np.ndarray], masks: list[np.ndarray]) -> float:
    """确认上游权重就是 App 里那份：同样输入下 top-1 一致率。"""
    import onnxruntime as ort
    import torch

    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    names = [i.name for i in sess.get_inputs()]
    mask_bool = sess.get_inputs()[1].type == "tensor(bool)"
    same = 0
    model.eval()
    with torch.no_grad():
        for x, m in zip(probes, masks):
            o = sess.run(None, {names[0]: x[None], names[1]: m[None] if mask_bool else m[None].astype(np.uint8)})[0][0]
            t = model(torch.from_numpy(x[None]), torch.from_numpy(m[None]))[0].numpy()
            same += int(o.argmax() == t.argmax())
    return same / max(len(probes), 1)


def evaluate(model, items: list[dict], char_to_idx: dict[str, int], device) -> dict:
    import torch

    if not items:
        return {"n": 0}
    xs, ms = zip(*(to_sequence(s["strokes"]) for s in items))
    x = torch.from_numpy(np.stack(xs)).to(device)
    m = torch.from_numpy(np.stack(ms)).to(device)
    y = torch.tensor([char_to_idx[s["label"]] for s in items], device=device)
    model.eval()
    with torch.no_grad():
        logits = torch.cat([model(x[i:i + 256], m[i:i + 256]) for i in range(0, len(items), 256)])
    top5 = logits.topk(5, dim=1).indices
    per_label: dict[str, list[int]] = {}
    for s, ok in zip(items, (top5[:, 0] == y).tolist()):
        per_label.setdefault(s["label"], []).append(int(ok))
    return {
        "n": len(items),
        "top1": float((top5[:, 0] == y).float().mean()),
        "top5": float((top5 == y[:, None]).any(1).float().mean()),
        "per_label_top1": {k: sum(v) / len(v) for k, v in sorted(per_label.items())},
        "pred": logits.argmax(1).tolist(),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--samples", nargs="+", required=True, help="训练模式导出的 jsonl")
    ap.add_argument("--upstream", default="D:/CyIME-Data/tmp/ochwpro-upstream")
    ap.add_argument("--app-model", default=str(APP_MODEL), help="App 内置 ochwpro 目录（onnx + char_index.json）")
    ap.add_argument("--out", required=True)
    ap.add_argument("--type", default="hanzi", help="只用这个类别的样本")
    ap.add_argument("--holdout", type=int, default=5, help="每个字留出最后几份做测试")
    ap.add_argument("--anchors", help="Make Me a Hanzi graphics.txt（可选，防退步）")
    ap.add_argument("--anchor-limit", type=int, default=3000)
    ap.add_argument("--steps", type=int, default=600)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--lr", type=float, default=5e-5)
    ap.add_argument("--kd", type=float, default=1.0, help="蒸馏权重")
    ap.add_argument("--l2sp", type=float, default=1e-2, help="与原权重距离的权重")
    ap.add_argument("--train-all", action="store_true", help="训练全部层（默认只训最后一层编码器和分类头）")
    ap.add_argument("--order-aug", type=float, default=0.0,
                    help="笔顺增强概率：训练时随机交换相邻笔画、偶尔倒写，减少对单一笔顺的依赖")
    ap.add_argument("--eval-only", action="store_true")
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()

    import torch
    import torch.nn.functional as F

    rnd = random.Random(args.seed)
    torch.manual_seed(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    app_dir = pathlib.Path(args.app_model)
    chars = json.loads((app_dir / "char_index.json").read_text(encoding="utf-8-sig"))["chars"]
    char_to_idx = {c: i for i, c in enumerate(chars) if c}

    samples = [s for s in load_samples(args.samples, args.type) if s["label"] in char_to_idx]
    if not samples:
        raise SystemExit(f"没有 type={args.type} 且在字表里的样本")
    train, test = split_holdout(samples, args.holdout)
    labels = sorted({s["label"] for s in samples})
    print(f"样本 {len(samples)}：训练 {len(train)}，留出 {len(test)}，{len(labels)} 个字 {''.join(labels)}")
    anchors = load_anchors(args.anchors, chars, args.anchor_limit, set(labels))
    if anchors:
        print(f"锚点字 {len(anchors)} 个")

    model = build_model(pathlib.Path(args.upstream), len(chars)).to("cpu")
    probes = [to_sequence(s["strokes"]) for s in (test + train)[:64]]
    agree = check_against_app_onnx(model, app_dir / "ochwpro.onnx", [p[0] for p in probes], [p[1] for p in probes])
    print(f"上游权重与 App onnx top-1 一致率 {agree:.1%}")
    if agree < 0.95:
        raise SystemExit("上游权重和 App 内置模型对不上，停止（避免微调错的底模）")

    model.to(device)
    teacher = build_model(pathlib.Path(args.upstream), len(chars)).to(device).eval()
    for p in teacher.parameters():
        p.requires_grad_(False)

    before_test = evaluate(model, test, char_to_idx, device)
    before_anchor = evaluate(model, anchors, char_to_idx, device)
    report = {"labels": labels, "train": len(train), "holdout": len(test), "anchors": len(anchors),
              "app_onnx_agreement": agree,
              "before": {"holdout": {k: v for k, v in before_test.items() if k != "pred"},
                         "anchor_top1": before_anchor.get("top1")}}
    print("微调前 留出:", report["before"]["holdout"].get("top1"), report["before"]["holdout"].get("per_label_top1"))

    if not args.eval_only:
        trainable = []
        for name, p in model.named_parameters():
            last_layer = f"transformer.layers.{len(model.transformer.layers) - 1}." in name
            keep = args.train_all or last_layer or name.startswith(("norm.", "classifier."))
            p.requires_grad_(keep)
            if keep:
                trainable.append((name, p))
        ref = {n: p.detach().clone() for n, p in trainable}
        opt = torch.optim.AdamW([p for _, p in trainable], lr=args.lr, weight_decay=0.0)
        pool = anchors or train
        target_ids = torch.tensor([char_to_idx[c] for c in labels], device=device)
        for step in range(1, args.steps + 1):
            model.train()
            batch = [rnd.choice(train) for _ in range(args.batch // 2)] + \
                    [rnd.choice(pool) for _ in range(args.batch - args.batch // 2)]
            xs, ms = zip(*(to_sequence(augment(s["strokes"], rnd, args.order_aug)) for s in batch))
            x = torch.from_numpy(np.stack(xs)).to(device)
            m = torch.from_numpy(np.stack(ms)).to(device)
            y = torch.tensor([char_to_idx[s["label"]] for s in batch], device=device)
            logits = model(x, m)
            with torch.no_grad():
                t_logits = teacher(x, m)
            n_user = args.batch // 2
            ce = F.cross_entropy(logits[:n_user], y[:n_user])
            # 蒸馏：锚点字整体跟原模型一致；练习字只约束“正确答案以外”的分布，
            # 不把原模型认错的答案再教回去
            is_target = (y[:, None] == target_ids[None]).any(1)
            drop = torch.zeros_like(logits, dtype=torch.bool)
            drop[is_target, y[is_target]] = True
            s_log = F.log_softmax(logits.masked_fill(drop, -1e4), -1)
            t_prob = F.softmax(t_logits.masked_fill(drop, -1e4), -1)
            kd = (t_prob * (t_prob.clamp_min(1e-9).log() - s_log)).sum(-1).mean()
            l2 = sum(((p - ref[n]) ** 2).sum() for n, p in trainable)
            loss = ce + args.kd * kd + args.l2sp * l2
            opt.zero_grad()
            loss.backward()
            opt.step()
            if step % 50 == 0 or step == 1:
                print(f"step {step}: ce={ce.item():.3f} kd={kd.item():.4f} l2={l2.item():.4f}")

    after_test = evaluate(model, test, char_to_idx, device)
    after_anchor = evaluate(model, anchors, char_to_idx, device)
    anchor_agree = None
    if anchors:
        anchor_agree = float(np.mean([a == b for a, b in zip(before_anchor["pred"], after_anchor["pred"])]))
    report["after"] = {"holdout": {k: v for k, v in after_test.items() if k != "pred"},
                       "anchor_top1": after_anchor.get("top1"),
                       "anchor_agreement_with_original": anchor_agree}
    print("微调后 留出:", report["after"]["holdout"].get("top1"), report["after"]["holdout"].get("per_label_top1"))
    if anchors:
        print(f"锚点字：原模型 top-1 {before_anchor['top1']:.1%} → {after_anchor['top1']:.1%}，"
              f"与原模型判断一致 {anchor_agree:.1%}")

    if not args.eval_only:
        model.eval().to("cpu")
        for p in model.parameters():
            p.requires_grad_(False)
        # 关掉 Transformer 推理快速路径，否则导出成 ONNX 不支持的融合算子
        torch.backends.mha.set_fastpath_enabled(False)
        onnx_path = out / "ochwpro.onnx"
        torch.onnx.export(
            model, (torch.zeros(1, FIXED_LEN, 5), torch.ones(1, FIXED_LEN, dtype=torch.bool)),
            str(onnx_path), input_names=["input", "mask"], output_names=["logits"],
            dynamic_axes={"input": {0: "batch"}, "mask": {0: "batch"}, "logits": {0: "batch"}},
            opset_version=17, dynamo=False,
        )
        (out / "char_index.json").write_text(
            (app_dir / "char_index.json").read_text(encoding="utf-8-sig"), encoding="utf-8")
        report["onnx"] = str(onnx_path)
        print(f"已导出 {onnx_path}")
    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告 {out / 'report.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
