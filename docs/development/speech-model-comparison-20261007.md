# 中、日、英语音模型比较（2026-10-07）

本轮目标：先验证中文 SenseVoice 二次识别是否值得保留、日语是否应该加同样处理，以及新版／更大 Zipformer 能否直接解决识别质量问题，再决定产品配置。这里只做本地研究与回放，没有修改本轮应用逻辑、版本、模型默认值，也没有打包安装；此前未交付的语音设置与切换草稿保持原状。

## 怎么比较

- Google FLEURS test，固定版本 `70bb2e84b976b7e960aa89f1c648e09c59f894dd`，中文／日语／英语各 32 段，合计 1,045.74 秒（约 17 分 26 秒）。按压缩包顺序、不同 prompt ID、3–25 秒选取，运行模型之前确定；参考稿来自数据集，未用某个模型输出充当答案。数据为朗读语音，非随机全量抽样。来源：[Google FLEURS，CC-BY-4.0](https://huggingface.co/datasets/google/fleurs)。
- 同一 16 kHz 单声道 PCM16；模型无热词、无按参考答案调参。模型对照使用 i7-12700H、Windows、CPU 双线程、sherpa-onnx 1.13.8，独立进程依次运行。新版日语 CTC 使用发布者的本地 Transformers 代码及 CPU FP32，不能把它的 Python/Torch 内存开销全部算成模型本身。
- 中文／日语采用去空白标点、NFKC 后的字错误率 CER；英文采用 Whisper 英文数字／缩写正规化后的词错误率 WER。数值越低越好，不把 CER 和 WER 跨语言排名。
- 主表的中日文仍会把“十五／15”、汉字／假名书写差异计错。发现这个影响后，另做参考稿完全不含阿拉伯和中日文数字字形的敏感性分析：中文 17 段、日语 19 段。这个子集是补充检查，不能冒充新的完整测试集。
- 对相关模型逐句配对，记录改善、退步、持平；10,000 次配对 bootstrap（固定随机种子）给出错误率差的 95% 区间。小样本、单一朗读语料不能支持“所有场景最佳”结论，也不能排除训练集与公开评测数据的潜在重叠。

权重、完整参考稿／输出、逐段耗时、模型 SHA256 和统计保存于 `.codex-artifacts/speech-language-20261007/`；原始音频及清单在 `D:/CyIME-Data/speech-lab/fleurs-20261007/`。脚本和命令见 [语音回放入口](../../tools/speech_lab/README.md)。所需新模型只放共享模型目录，不复制进应用或 worktree，不保留整份语料下载包。

## 必须区分的两类结果

**裸模型对照**：流式模型按 100 ms 喂入，离线模型接收整段；Reazon 两侧各补 0.9 秒静音，日语 CTC 各补 0.5 秒。SenseVoice 同时测试 auto 与明确语言。模型本身的速度、内存和准确度在这层记录。

**现有处理逻辑对照**：直接编译仓库生产 Kotlin 文件，通过管道接原生识别器，而不是另写一套近似分段。

- 中文使用生产 `SpeechDetectionLevel`、`SpeechSegmenter` 和 `SpeechTranscript`：单模型名义 12 秒上限，校正名义 6 秒上限（连续讲话还允许最多 2 秒寻找停顿）；保持真实语速反馈、VAD 增益和暂停重置。分别评分单模型、校正配置的首遍、相同音频段的第二遍。原生模型单线程，与产品相同。
- 日语直接使用生产 `JapaneseZipformerDecoding` 的时间戳空档检测、VAD 验证、最多三层重切，对 INT8／FP32 都执行，不能拿没有纠漏处理的裸模型冒充用户现有版本。本轮日语片段均不足 22 秒，但没有模拟麦克风自然停顿和滚动预览调度。
- 这是同步文本质量回放，不模拟 Android 队列、手机 CPU、采音、热状态或超时。没有真机内存、首字、停顿定稿或耗电验收；桌面 RTF 不能当作手机延迟。

## 中文：第二遍的实际收益

生产分段下，Paraformer 单独为 **18.02% CER**，开启校正后的最终文本为 **8.78%**；32 段中 **24 段改善、1 段退步、7 段持平**。错误率差为 −9.24 个百分点，配对 95% 区间约 [−12.58, −6.32]。

为排除“只是分段变了”的解释，同一校正分段的 Paraformer 首遍为 **16.56%**，SenseVoice 定稿仍为 **8.78%**；**23 改善、1 退步、8 持平**。不含数字的 17 段也从单模型 **18.88%** 降到 **10.84%**，14 改善、0 退步，收益并非仅来自数字格式。

因此，中文第二遍有实际收益，但不是保证每句更准。反例 `zh-1698` 中，第二遍遗漏“而所有”，该句由 3 个错误变为 5 个。现有实现是 SenseVoice **重新听同一段音频并替换首遍文本**，没有读取首遍文字逐字校验或智能合并；“二次识别”比“必然纠错”准确。

## 日语：不能照搬中文的结论

加入现有纠漏算法后，INT8 为 **9.02% CER**、FP32 为 **8.38%**；明确日语的 SenseVoice 也是 **8.38%**。从 FP32 换成 SenseVoice，11 段改善、12 段退步、9 段持平，总体差为零；95% 区间约 [−3.52, 3.16] 个百分点。不能宣称 SenseVoice 稳定优于它。

不含数字的 19 段：Zipformer FP32 **4.45%**、INT8 **5.54%**、SenseVoice 日语 **6.08%**。这一子集方向反转，再次说明不能只看单一平均分。例 `ja-1731`：Zipformer 的「群島」正确，SenseVoice 变成「軍島」；`ja-1764` 原本全文正确，SenseVoice 引入 4 个错误。

FP32 对 INT8 的总体提升仅 0.64 个百分点，6 段改善、4 段退步、22 段相同，配对区间跨零；权重从约 153 MiB 增至 586 MiB。本轮证据不支持为了这点收益默认强推大版，也不支持默认叠加 SenseVoice。

## 新版与大版调查

- **2025 中文 Zipformer XLarge**：约 700M 参数，与约 160M 的原中文版本不同。它更大，但官方不同语料的提升也不一致，本地对照没有超过 SenseVoice。来源：[Sherpa 官方在线模型](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html)、[Icefall 训练结果](https://github.com/k2-fsa/icefall/blob/master/egs/multi_zh-hans/ASR/RESULTS.md)。
- **2026-06-22 新中英 Zipformer 系列**：本轮实际测试 150M 流式 RNNT、150M 流式 CTC INT8、300M 离线 CTC INT8。这个 300M XLarge 与上面的 700M 中文 XLarge 是不同系列，不能只按名称排序。来源：[发布者仓库](https://github.com/pkufool/zipformer)、[官方模型说明](https://pkufool.github.io/zipformer/en/models/)。
- **较新日语 Zipformer CTC**：Reazon 2025 年模型，与旧 K2 v2 的输出结构和训练不同，是替代候选而非原模型简单升级；本轮未超过加入现有纠漏处理的 K2 v2。来源：[Reazon CTC](https://huggingface.co/reazon-research/japanese-zipformer-base-k2-rs35kh)、[K2 v2](https://huggingface.co/reazon-research/reazonspeech-k2-v2)。
- **多语言单模型**：比较 SenseVoiceSmall、Nemotron 3.5 0.6B INT8（320 ms）、Qwen3-ASR 0.6B INT8、Fun-ASR-Nano 0.8B INT8。Nemotron 是原生流式；本轮 Qwen3／Fun-ASR 使用 Sherpa 离线接口，不能把发布者其他运行方式的流式能力直接算成已经接入。来源：[SenseVoice](https://huggingface.co/FunAudioLLM/SenseVoiceSmall)、[Nemotron](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)、[Qwen3 导出](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html)、[Fun-ASR-Nano 导出](https://k2-fsa.github.io/sherpa/onnx/funasr-nano/pretrained.html)。
- 未跑 Qwen3 1.7B、Whisper／Kotoba 系列全部版本、服务器模型或付费云模型；本轮是当前模型及一组约 1 GiB 以内可本地 CPU 使用的候选比较，不是世界模型榜单。没有拿不同公开测试集的分数直接拼成排名。

## 资源与决策边界

源码的 `ResidentSpeechSessionOwner` 只有一个当前引擎槽位；切换模型组合前先关闭旧引擎。当前组合可以含主模型和 SenseVoice 两个识别器。因此“配置了三种语言”不直接等于“三个模型加一个修正器全部常驻”；是否重复加载还要看模型身份和语言提示是否被正确复用。

第二遍会增加推理、内存与定稿等待，日语还可能覆盖正确结果。能否保留原生连续出字、同一句中英／日英混说、切换语言不中断，需要单独的真实交互验证。本轮没有为满足单一分数擅自改默认值。

## 结果总表

共 17 组模型／配置（含同一 SenseVoice 的四种语言提示设置），不是 17 个不同的基础模型。以下是裸模型同录音对照；日语应用纠漏后分数见前文，不能混淆。

| 模型／配置 | 中文 CER | 日语 CER | 英文 WER | 权重 MiB | 进程峰值 RSS MiB | 原生增量流式（本轮接口） |
|---|---:|---:|---:|---:|---:|---|
| Paraformer INT8（当前中英） | 15.10% | — | 37.46% | 226.2 | 335 | 是 |
| SenseVoice INT8 · auto | 7.50% | 8.70% | 9.59% | 228.5 | 425 | 否 |
| SenseVoice INT8 · 中文提示 | 7.41% | — | — | 228.5 | 389 | 否 |
| SenseVoice INT8 · 日语提示 | — | 8.38% | — | 228.5 | 423 | 否 |
| SenseVoice INT8 · 英语提示 | — | — | 9.59% | 228.5 | 384 | 否 |
| 日语 K2 v2 INT8 · 裸模型 | — | 11.26% | — | 152.9 | 543 | 否 |
| 日语 K2 v2 FP32 · 裸模型 | — | 10.62% | — | 586.4 | 1090 | 否 |
| 日语 Zipformer CTC 2025 FP32 | — | 11.00% | — | 374.7 | 1194 | 否 |
| 中文 Zipformer 2025 INT8 | 14.09% | — | — | 159.6 | 294 | 是 |
| 中文 Zipformer 2025 XLarge INT8 | 14.09% | — | — | 735.5 | 894 | 是 |
| 英文 Zipformer Kroko 2025 FP32 | — | — | 19.32% | 67.8 | 209 | 是 |
| Nemotron 3.5 0.6B INT8 · 320ms | 24.61% | 16.83% | 14.60% | 650.6 | 799 | 是 |
| Qwen3-ASR 0.6B INT8 | 8.14% | 12.16% | 7.52% | 941.3 | 2615 | 否 |
| Fun-ASR-Nano 0.8B INT8 | 9.52% | 7.17% | 8.70% | 962.8 | 2301 | 否 |
| 中英 Zipformer 2026 流式 RNNT FP32 | 13.36% | — | 29.94% | 600.9 | 727 | 是 |
| 中英 Zipformer 2026 流式 CTC INT8 | 23.79% | — | 30.68% | 153.9 | 285 | 是 |
| 中英 Zipformer 2026 XLarge CTC INT8 | 9.97% | — | 24.48% | 290.8 | 653 | 否 |

RSS 是整个桌面 worker 的峰值（20 ms 采样，基础 Python 进程约 42 MiB），含运行库、权重与临时张量；不是 Android 占用。尤其不能用日语 CTC 的 Torch 进程直接推断移植后的原生内存。单模型逐段 RTF 不包含模型加载，也不包含为实现滚动预览而重复识别的成本。

| 模型／配置 | 中文 RTF | 日语 RTF | 英文 RTF | 新进程创建识别器（秒） |
|---|---:|---:|---:|---:|
| Paraformer INT8（当前中英） | 0.068 | — | 0.070 | 1.16 |
| SenseVoice INT8 · auto | 0.038 | 0.038 | 0.038 | 1.21 |
| SenseVoice INT8 · 中文提示 | 0.038 | — | — | 1.08 |
| SenseVoice INT8 · 日语提示 | — | 0.038 | — | 1.12 |
| SenseVoice INT8 · 英语提示 | — | — | 0.037 | 1.06 |
| 日语 K2 v2 INT8 · 裸模型 | — | 0.024 | — | 1.62 |
| 日语 K2 v2 FP32 · 裸模型 | — | 0.039 | — | 1.96 |
| 日语 Zipformer CTC 2025 FP32 | — | 0.059 | — | 20.17 |
| 中文 Zipformer 2025 INT8 | 0.083 | — | — | 3.48 |
| 中文 Zipformer 2025 XLarge INT8 | 0.194 | — | — | 6.17 |
| 英文 Zipformer Kroko 2025 FP32 | — | — | 0.023 | 2.75 |
| Nemotron 3.5 0.6B INT8 · 320ms | 0.310 | 0.306 | 0.308 | 2.04 |
| Qwen3-ASR 0.6B INT8 | 0.206 | 0.231 | 0.228 | 3.32 |
| Fun-ASR-Nano 0.8B INT8 | 0.159 | 0.187 | 0.176 | 4.14 |
| 中英 Zipformer 2026 流式 RNNT FP32 | 0.121 | — | 0.121 | 4.07 |
| 中英 Zipformer 2026 流式 CTC INT8 | 0.059 | — | 0.060 | 1.87 |
| 中英 Zipformer 2026 XLarge CTC INT8 | 0.023 | — | 0.023 | 1.76 |

RTF 0.1 表示计算 10 秒音频约用 1 秒；不代表说完后等 1 秒，也不代表首字延迟。离线整段和在线增量工作的时间分布不同。这里只做一次顺序测量，操作系统文件缓存与后台负载未锁定，不能称为严格冷启动或手机实时性比较。

| 不含数字字形的参考稿子集 | 中文 CER（17 段） | 日语 CER（19 段） |
|---|---:|---:|
| Paraformer INT8（当前中英） | 14.46% | — |
| SenseVoice INT8 · auto | 9.44% | 6.19% |
| SenseVoice INT8 · 中文提示 | 9.44% | — |
| SenseVoice INT8 · 日语提示 | — | 6.08% |
| Qwen3-ASR 0.6B INT8 | 7.83% | 6.95% |
| Fun-ASR-Nano 0.8B INT8 | 8.43% | 4.99% |
| 中文 Zipformer 2025 INT8 | 11.45% | — |
| 中文 Zipformer 2025 XLarge INT8 | 14.06% | — |
| 中英 Zipformer 2026 流式 RNNT FP32 | 11.45% | — |
| 中英 Zipformer 2026 流式 CTC INT8 | 22.49% | — |
| 中英 Zipformer 2026 XLarge CTC INT8 | 8.23% | — |
| 日语 Zipformer CTC 2025 FP32 | — | 7.82% |
| Paraformer · 生产单模型分段 | 18.88% | — |
| Paraformer + SenseVoice · 生产分段 | 10.84% | — |
| 日语 K2 v2 INT8 · 生产纠漏 | — | 5.54% |
| 日语 K2 v2 FP32 · 生产纠漏 | — | 4.45% |

## 单模型候选怎样取舍

**Fun-ASR-Nano** 在本批完整日语录音中平均最低（7.17%），但对现有 FP32 纠漏结果只是 12 段改善、9 段退步、11 段持平，差值 95% 区间约 [−4.14, 1.44] 个百分点；不能称为稳定压过现有模型。不含数字子集为 4.99%，现有 FP32 为 4.45%。本次导出权重约 963 MiB，桌面进程峰值约 2.25 GiB；这不是为了很小平均收益就默认上手机的证据。

**Qwen3-ASR 0.6B** 英文平均最低（7.52% WER），SenseVoice 为 9.59%。12 段改善、3 段退步、17 段相同；配对差值区间上界到 0，样本还不足以强保证。它的日语为 12.16%，桌面峰值约 2.55 GiB，不能以英文表现推导“三语统一更好”。中文无数字子集比 SenseVoice 更好（7.83% 对 9.44%），而主表排序受数字书写影响，不能只摘选有利的一张表。

**中文新版 Zipformer** 有局部提升，尤其 2026 中英离线 XLarge，但它并未超过 SenseVoice 的完整中文平均分，英文也明显落后多语言候选；流式新版没有表现出足以替代中文二次识别的优势。2025 中文 700M 大版与小版完整 CER 恰好同为 14.09%，逐句有 11 改善、14 退步、7 持平；相同均值不表示两者输出相同。

**英语不必先加第四个专用模型**：现有 SenseVoice 单模型已经能识别英语，本批 WER 9.59%，显著低于当前 Paraformer 的 37.46%。是否在日英状态下正确路由到它，是产品逻辑问题，不能靠把日语专用 Zipformer 标成“日英”解决。

建议先保留两个可供决策的方向，而不直接更改配置：①优先少模型、低占用：Paraformer 提供中文增量预览，同一 SenseVoice 复用中文定稿与日英识别；它能减少模型种类，但日语切换是资源取舍，不能宣传为准确率升级，而且 SenseVoice 本身不是原生流式。②优先单模型且接受较高占用：Fun-ASR-Nano 值得做下一步手机验证，但本轮还没有足够证据用它全量替代现有组合。若保留日语 Zipformer，继续独立定稿，不默认叠第二遍。

本轮没有找到同时满足“三语全面更准、原生流式、占用更低”的单模型。下一步产品决策仍应以真实口述／混语、低音量、长句、首字和定稿延迟、手机内存为依据；这里的比较没有完成这些验收。

## 复现与检查记录

- 模型对照 17 个配置全部完成，无失败配置；每个实际语言均使用同样 32 条固定录音。中文生产回放 64 次（两种分段配置），日语生产纠漏 64 次（两种精度），全部产出结果。
- Python 语法与基础评分行为检查通过；两种生产回放的 Kotlin 编译与 native 桥均通过。没有启动应用构建或 ADB。
- 语料初次数据服务接口返回错误，随后使用官方固定版本 TSV 与流式压缩包读取，只保留选中音频；没有把其他模型生成文字当作人工参考。
- 最初先比较裸模型，随后补入现有日语纠漏算法，日语排名明显改变；最终结论使用补测结果，没有用裸模型结果宣称现有应用更差。
- 审查数字格式后补充不含数字参考稿的统计，主表仍保留原始可复查 CER，没有静默改答案或删除失利样本。
- 模型搜索补充了 2026 中英 Zipformer 与 Fun-ASR-Nano 后才收束，没有仅用旧模型回答“有没有更新版本”。

最终评分命令：

```powershell
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/score_comparison.py `
  .codex-artifacts/speech-language-20261007/comparison `
  --additional-folder .codex-artifacts/speech-language-20261007/comparison-2026 `
  --additional-folder .codex-artifacts/speech-language-20261007/comparison-funasr `
  --production-zh .codex-artifacts/speech-language-20261007/production-zh/results.json `
  --production-ja .codex-artifacts/speech-language-20261007/production-ja/results.json `
  --output .codex-artifacts/speech-language-20261007/scores.json
```

