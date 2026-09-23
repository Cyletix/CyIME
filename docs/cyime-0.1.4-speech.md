# CyIME 0.1.4：ASRInput 语音迁移

本轮只修改语音识别。键盘、配色、输入方案不变。

## 调查结论

用户提供的 `zipformer识别测试.txt` 几乎没有空格，且每大段末尾有疑似截断；它不是带参考答案的音频，无法据此计算字错率，也不能证明所有错词均来自模型。

原实现使用自写 Zipformer 解码器：整段录音没有停顿分段，删除了 SentencePiece 词边界标记 `▁`，最后只处理完整特征块。最后一块不够长时，句尾可能未解码。安装市场 Zipformer 只是安装权重，并不意味着运行的是 sherpa-onnx 官方识别流程。

## 迁移内容

- 官方 sherpa-onnx **1.13.8**，与 ASRInput 相同；保留现有 Zipformer 权重，支持 Paraformer 中英流式、SenseVoice 多语、Paraformer + SenseVoice。
- VAD、动态能量停顿估计、1024 ms 前缓冲、256/384/600 ms 自适应停顿，源自 ASRInput（MIT）。音频采集与推理分离，有界队列过载会报错，不静默丢掉录音。
- 单模型流式最长约 14 秒一段；SenseVoice/双模型至少等待 600 ms 停顿，6 秒后允许在较短低能量区切段，连续讲话最迟 8 秒切段。强制时长切段不向中文词中间插入空格；自然停顿分段用空格。
- Paraformer 预览和 SenseVoice 校正使用不同工作线程。每段使用独立编号；迟到校正只替换对应预览，取消和切换输入框后不再写入。双模型校正失败不把中英预览冒充日语定稿。
- 停止时排空已接收音频并处理尾块。工具栏立即恢复，文字只等待实际未完成工作；多语校正每段最长等待 5 秒，不固定等待。后台收尾仍允许用户正常键入。
- 标点与富文本标签统一清理，标点变为空格，句末不补标点。
- 新模型在“语音转文本 → 本地模型”中直接下载、选择，也加入模型市场。模型文件来自固定版本的官方导出，有 SHA-256 校验，不打包进 APK、不强制下载。两模型共约 455 MB；低性能设备可使用单模型。

移植参考：ASRInput `875ab0e3b93982adf49af0852043ef3302849c6a` 的 RealtimePipeline、EnergyPauseGate、TwoPassBackend、ParaformerStreamingBackend、SenseVoiceEngine。没有复制 Windows 的窗口、麦克风、键盘注入代码，也没有迁入声称可提升精度的任意文字改写。

## 验证

最终 JVM 定向检查 51/51 通过，覆盖分段、尾块、取消、迟到校正、空结果撤回、模型下载校验及安装。Android 四模式共 14 组实际音频回归通过（中/英/日及约 55 秒长句），另通过独立进程模型切换/停止/取消、设置选择保存和 3 项真实编辑框回写测试。声学录音停止后收尾耗时约 1–826 ms，均为本次模拟器数据。模拟器耗时不能当作低端手机的耗时；用户提供的视频原始音频与人工参考稿尚未提供，本轮不宣称测得整体准确率提升。


## 为什么不能只换模型

同一段公开日语样例，整段 SenseVoice 可以保留的「持って」，使用较短停顿分段后丢失。将多语停顿下限设为 600 ms、保留更长的起音缓冲后恢复；同时清除 SenseVoice 中文/日文的字间 token 空白，保留英文单词空格及真实停顿。多语模式不依据中英 Paraformer 预览字数估算日语语速。

中文样例在初次回归时，Zipformer 输出「开放时间」，Paraformer 输出「菜放时间」；保留完整起音缓冲后，Zipformer 和 SenseVoice 又输出「开饭时间」。这也表明音频切分会改变解码结果，不能只凭一次结果评判模型优劣。模型名或新旧程度不能直接代表所有录音条件下的准确率。双模型会增加内存和计算消耗，慢设备可单独选择 SenseVoice（停顿后出字）或 Paraformer（中英流式）。

## 使用

进入「语音转文本」，开启本地识别，在「离线语音模型」下载所需模型，再点对应名称选择。双模型下载按钮会依次补齐 Paraformer 与 SenseVoice；已有文件不重复下载。下一次录音开始生效。原来的 Zipformer 选择不被自动替换。

Paraformer 的中英预览不具备日语能力；双模型日语以 SenseVoice 校正为准，不能把未完成的中英预览当作最终结果。超时或设备跟不上会报告错误，取消之后不再回写文本。

官方运行库及接口：[sherpa-onnx Android](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html)、[v1.13.8 发布](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)。ASRInput MIT、sherpa-onnx Apache-2.0 与 Silero MIT 许可随应用保留；模型从对应官方导出仓库下载。


本地证据：`.gradle/cyime-014/final-acoustic-results.json`、`speech-final.log`、`speech-editor.log`、`model-settings.png`。第一次组合测试命令把编辑框测试包名写成 `ui` 导致该类未加载；已改为实际的 `service.StreamingVoiceTextTest` 单独补跑，未重复把已通过的声学用例计数。识别用例通过表示流程、停顿空格和语言输出满足断言，不代表逐字准确；长句专有名词、Zipformer 部分开头识别、日语个别词内停顿空格仍有改进空间。
