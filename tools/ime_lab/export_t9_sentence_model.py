"""Reproduce the bundled T9 masked-language scorer from pinned UER weights.

Run in a separate virtual environment with requirements-t9-model.txt installed.
This exports/quantizes pretrained weights; it never trains on regression inputs.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import urllib.request

REVISION = "f22b50972cc3cf472c6cdfdd71ca2635dfbd434c"
SOURCE = "https://huggingface.co/uer/chinese_roberta_L-4_H-512/resolve/" + REVISION
HASHES = {
    "pytorch_model.bin": "f186784f34c09a4a7f41f249b9e7a61b83c12e08205f412512b86b6638327e8d",
    "config.json": "fb94be7f6103c7f6aff7757c915283f9d6c45706f208f0751bf783a8a77c47c4",
    "vocab.txt": "45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c",
}


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, default=Path(".gradle/t9-sentence-model"))
    parser.add_argument("--output-dir", type=Path, default=Path("app/src/main/assets/rime_chinese"))
    args = parser.parse_args()
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    for name, expected in HASHES.items():
        target = args.cache_dir / name
        if not target.exists() or sha(target) != expected:
            temporary = target.with_suffix(target.suffix + ".download")
            urllib.request.urlretrieve(SOURCE + "/" + name, temporary)
            if sha(temporary) != expected:
                raise ValueError("Source checksum mismatch: " + name)
            temporary.replace(target)

    import torch
    import onnx
    from transformers import BertConfig, BertForMaskedLM
    from onnxruntime.quantization import quantize_dynamic, QuantType

    torch.set_num_threads(2)
    config = BertConfig.from_json_file(str(args.cache_dir / "config.json"))
    config._attn_implementation = "eager"
    model = BertForMaskedLM(config)
    state = torch.load(args.cache_dir / "pytorch_model.bin", map_location="cpu", weights_only=True)
    state.pop("bert.embeddings.position_ids", None)
    model.load_state_dict(state, strict=True)
    model.eval()

    class Scorer(torch.nn.Module):
        def __init__(self, base):
            super().__init__()
            self.model = base

        def forward(self, input_ids, attention_mask, positions, targets):
            hidden = self.model.bert(input_ids, attention_mask=attention_mask).last_hidden_state
            selected = hidden[torch.arange(hidden.shape[0]), positions]
            scores = self.model.cls(selected).log_softmax(-1)
            return scores.gather(1, targets.unsqueeze(1)).squeeze(1)

    wrapper = Scorer(model).eval()
    tokens = torch.tensor([[101, 2769, 4696, 103, 102], [101, 2769, 103, 4638, 102]])
    source = args.cache_dir / "scorer.onnx"
    torch.onnx.export(
        wrapper, (tokens, torch.ones_like(tokens), torch.tensor([3, 2]), torch.tensor([4638, 4696])),
        str(source), input_names=["input_ids", "attention_mask", "positions", "targets"],
        output_names=["scores"], dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"}, "attention_mask": {0: "batch", 1: "sequence"},
            "positions": {0: "batch"}, "targets": {0: "batch"}, "scores": {0: "batch"}},
        opset_version=17, dynamo=False)

    # The embedding and output projection share weights in PyTorch. ORT's
    # dynamic quantizer transposes a MatMul initializer; isolate its uses first
    # so quantizing the output projection cannot transpose the embedding table.
    graph = onnx.load(source)
    initializers = {item.name: item for item in graph.graph.initializer}
    used = set()
    for node in graph.graph.node:
        for index, name in enumerate(node.input):
            if name not in initializers:
                continue
            if name in used:
                tensor = copy.deepcopy(initializers[name])
                tensor.name = name + "_use_" + str(len(graph.graph.initializer))
                graph.graph.initializer.append(tensor)
                node.input[index] = tensor.name
            used.add(name)
    untied = args.cache_dir / "scorer_untied.onnx"
    onnx.save(graph, untied)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    result = args.output_dir / "t9_sentence.onnx"
    quantize_dynamic(str(untied), str(result), weight_type=QuantType.QInt8,
                     per_channel=True, op_types_to_quantize=["MatMul", "Gather"])
    (args.output_dir / "t9_sentence.vocab").write_bytes((args.cache_dir / "vocab.txt").read_bytes())

    # Check dynamic batch/sequence shapes and numerical drift against PyTorch.
    import numpy as np
    import onnxruntime as ort
    options = ort.SessionOptions()
    options.intra_op_num_threads = options.inter_op_num_threads = 1
    runtime = ort.InferenceSession(str(result), options, providers=["CPUExecutionProvider"])
    errors = []
    for width in (5, 9, 26):
        data = torch.tensor([[101] + [2769, 4696, 4638, 103] * 7 + [102]] * 4)[:, :width]
        data[:, -1] = 102
        positions = torch.tensor([1, 2, 3, 4]).clamp(max=width - 2)
        targets = data[torch.arange(4), positions].clone()
        data[torch.arange(4), positions] = 103
        mask = torch.ones_like(data)
        with torch.inference_mode():
            expected = wrapper(data, mask, positions, targets).numpy()
        actual = runtime.run(None, dict(input_ids=data.numpy(), attention_mask=mask.numpy(),
                                       positions=positions.numpy(), targets=targets.numpy()))[0]
        errors.append(float(np.max(np.abs(expected - actual))))
    if max(errors) > 1.5:
        raise ValueError(f"Quantization drift too large: {errors}")
    print(json.dumps({"revision": REVISION, "sha256": sha(result), "bytes": result.stat().st_size,
                      "max_log_probability_error": max(errors)}, indent=2))


if __name__ == "__main__":
    main()
