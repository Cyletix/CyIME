# Paraformer 低音量回放

`replay.py` 在电脑运行生产 Kotlin 的 `SpeechDetectionLevel`、`SpeechSegmenter`，通过同步管道连接官方 sherpa-onnx 1.13.8 的 Silero VAD 和 Paraformer。流式文本实时回传 Kotlin，参与生产的语速／停顿计算；不是另写一份分段算法。`before` 使用原始 VAD、能量门限、不按停顿重置，重现 1.9.2；`after` 使用当前 Paraformer 配置。

依赖本地 Python 的 `sherpa-onnx==1.13.8`、NumPy、SoundFile，已有 JDK 与 Gradle Kotlin 编译器缓存。工具不下载、不连接设备、不读取麦克风，不修改原录音和模型。输出目录必须不存在。

```powershell
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/replay.py `
  --speech .codex-artifacts/prediction-speech-fixtures/zh.wav `
  --model D:/GitHub/ASRInput/models/sherpa-onnx-streaming-paraformer-bilingual-zh-en `
  --vad app/build/generated/speech-assets/speech/silero_vad.onnx `
  --java C:/Users/Administrator/.gradle/jdks/jdk-21.0.10+7/bin/java.exe `
  --output .codex-artifacts/speech-low-volume-new
```

固定语料为“开放时间早上九点至下午五点”，16 kHz 单声道。对比 6 档音量、大小声交替、轻声后静音，以及静音／白噪声／较大噪声／60 Hz 低频声／脉冲声，共 14 个输入。断言针对提前触发、保留前半句及第二句、自动按停顿结束、非语音不触发。报告保留全文，不能把触发回归通过当作每个字都准确。

`first_partial_audio_s` 是首次非空流式文本对应的已送入音频时长（100 ms 送音），不包含设备采音、加载和 CPU 耗时。桌面回放不能替代真实手机／平板的视频外放验收。

## 合成风扇样噪声诊断

`fan_noise_replay.py` 复用同一个 `SpeechReplay.kt` 的 `after` 路径和生产 Kotlin 检测增益／分段，将固定中文录音与固定种子的低频谐波、宽带气流样噪声混合。比较原音量和 0.03 倍音量、干净语音、20/10/5/0/-5 dB 信噪比、纯噪声及静音；随后将完全相同的 PCM 绕过 VAD 直接送入同一 Paraformer，区分不开段／截断与解码误识别。SNR 按原录音整句 RMS 定义，包含原有停顿；0 dB 不是“轻微噪声”。这不是用户的风扇实录，也不模拟 Android 采音处理或异步超时。

使用上面相同的 `--speech`、`--model`、`--vad`、`--java` 参数，将脚本换为 `fan_noise_replay.py`，另给一个新的 `--output` 目录。默认阈值 0.6 与当前 Paraformer 一致；`--vad-threshold .5` 仅覆盖回放参数，不能据此声称生产已修改。可重复 `--case <名称>` 缩小到已出现问题的对照，`--skip-direct` 仅在已有相同 PCM 直接解码结果时省略重复解码。报告保存输入音频、生产源码和模型哈希、完整文字、VAD 活跃帧、真实分段区间及 CER；`wall_s` 是本机同步回放耗时，不能当作设备实时延迟。

`--stationary-suppression` 是未进入生产的轻量频谱实验：`stationary_noise.py` 用 512 点 FFT、1 秒过去频谱历史和最小 0.25 倍增益估计持续噪声，最多 32 ms 算法延迟；同一处理后的 PCM 送入 VAD 和 ASR，不使用合成过程已知的噪声真值。2026-10-08 对照只出现部分改善，低音量 0 dB 在原阈值下仍不开段，部分 20 dB 结果反而漏字，**尚不满足生产接入标准**。保留原始 `.original.f32` 和完整实验报告，不能将这些结果表述为实际风扇问题已修复。

## 中日英模型对照

`prepare_comparison.py` 从固定版本 Google FLEURS test 集中，按压缩包顺序选择各语言前 32 个不同 prompt、时长 3–25 秒的录音，保留人工参考稿和音频哈希；只保存所选 WAV，不保留整个压缩包。数据来源及 CC-BY-4.0 许可见 [Google FLEURS](https://huggingface.co/datasets/google/fleurs)。这是固定小样本朗读试验，不是完整榜单或口语验收。

`compare_models.py` 每个模型独立进程、CPU 双线程、顺序运行。流式模型按 100 ms 送入音频，离线模型接收同一完整录音；不将离线计算耗时称为出字延迟。模型路径见脚本顶部，复用共享缓存；脚本不下载权重。需额外 psutil 记录整个 Python 进程 RSS，包含运行库，不能冒充 Android 模型内存。

```powershell
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/prepare_comparison.py --output D:/CyIME-Data/speech-lab/fleurs-new
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/compare_models.py `
  --manifest D:/CyIME-Data/speech-lab/fleurs-20261007/manifest.json `
  --output .codex-artifacts/speech-comparison-new `
  --models paraformer sense-auto sense-zh sense-ja sense-en zip-ja-int8 zip-ja-fp32 zip-ja-ctc zip-zh zip-zh-xl zip-en-kroko nemotron qwen3 zip-2026-stream-ctc zip-2026-stream-rnnt zip-2026-xl-ctc funasr-nano
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/score_comparison.py `
  .codex-artifacts/speech-comparison-new --output .codex-artifacts/speech-comparison-scores-new.json
```

`score_comparison.py` 中文／日语使用 NFKC、去空白标点后的 CER；英文用已安装 Transformers 的 Whisper 英文正规化器（数字、缩写，未加载英美拼写映射）计算 WER。中文／日语数字和汉字／假名的书写差异仍会计错，因此另报告“参考稿不含任何数字字形”的敏感性分析。配对统计给出改善／退步／相同句数和 10,000 次逐句重采样的 95% 区间；小样本不支持全场景优劣保证。

`compare_refinement.py` 直接编译本仓库生产 Kotlin 分段、增益和文本拼接代码，经 Python 原生 Silero VAD／Paraformer／SenseVoice 比较：单模型 12 秒分段、开启校正后的 6 秒分段首遍、同一 6 秒段的 SenseVoice 定稿。单线程与当前产品配置一致，使用中文提示。同步回放只检查文本，不模拟 Android 异步队列、麦克风或超时。

同一脚本 `--language ja` 直接调用生产 `JapaneseZipformerDecoding`，对 INT8／FP32 都执行已有的时间戳空档检测、Silero 语音区间核对和递归重切。比较文本质量，不把同一进程加载两个精度版本的内存用作产品估算；也未模拟录音入口停顿和滚动预览调度。

```powershell
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/compare_refinement.py `
  --manifest D:/CyIME-Data/speech-lab/fleurs-20261007/manifest.json `
  --java 'C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot/bin/java.exe' `
  --output .codex-artifacts/speech-refinement-new
```

输出目录必须是新目录，不覆盖历史结果；单模型 worker 的 `--output` 为文件，仅供调度器使用。实际对照结论见 [2026-10-07 模型比较](../../docs/development/speech-model-comparison-20261007.md)。

评分可用 `--additional-folder <另批模型结果目录>` 合并不同模型、用 `--production-zh <中文 results.json>` / `--production-ja <日语 results.json>` 加入生产算法比较；重复模型会拒绝合并，不静默选较好的那次。

## 真机调优录音回放

语音设置“保存语音调优录音”打开后，`:asr` 进程把每次本地识别实际收到的 16 kHz PCM、文字变化时间线、分段边界与模型写入设备 `Android/data/com.cyletix.cyime/files/speech_tuning/`（最多 300 段或 500 MB，不上传）。`tuning_replay.py` 用 adb 拉到私有数据目录（不放仓库），以整段 SenseVoice 结果作伪参考，经 `compare_refinement.py` 用生产分段代码比较多种配置；`CYIME_REPLAY_MODES` 可写 `single`、`single-pause<毫秒>`、`refinement`、`refinement-pause<毫秒>`，`--gain` 可模拟更小音量。伪参考偏向 SenseVoice 定稿，必须结合逐段文字判断。

```powershell
& 'D:/GitHub/ASRInput/training/.venv/Scripts/python.exe' tools/speech_lab/tuning_replay.py `
  --serial R52T800SKWZ --data D:/CyIME-Data/speech-tuning/tablet `
  --java 'C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot/bin/java.exe' `
  --output .codex-artifacts/speech-tuning-<日期>
```
