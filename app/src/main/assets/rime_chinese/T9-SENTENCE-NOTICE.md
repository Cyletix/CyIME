T9 sentence scorer
==================

Source: UER-py, Chinese RoBERTa L=4 / H=512, trained on CLUECorpusSmall.
https://huggingface.co/uer/chinese_roberta_L-4_H-512
Revision: f22b50972cc3cf472c6cdfdd71ca2635dfbd434c
Source project: https://github.com/dbiir/UER-py (Apache License 2.0).
See LICENSE-t9-sentence for the source project's license.

CyIME changes: export the masked-position log-probability head to ONNX opset
17; untie shared export initializers; quantize MatMul/Gather weights to INT8.
No training or fine-tuning on CyIME's reported examples.

Reproduction: tools/ime_lab/export_t9_sentence_model.py and
tools/ime_lab/requirements-t9-model.txt. All source downloads are SHA-256 pinned.

Bundled model SHA-256:
159576a96e282f929f5726d53bae5322dc41235d3deef40e806297d9e69ca4fc
Bundled vocabulary SHA-256:
45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c

The bundled configuration uses one CPU worker, 12 final hypotheses, at most
24 characters and four masked positions per hypothesis. Its score cache is
bounded to 2,048 entries and is discarded when Rime is finalized.
