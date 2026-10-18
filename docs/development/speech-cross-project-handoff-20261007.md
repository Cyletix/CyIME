# ASRInput 与 CyIME Windows 语音组合交接

日期：2026-10-07。来源：CyIME Android 1.9.6 / 20261098。用户要求将这套组合和修复应用于原 ASRInput 与 Windows 端，并由各自已有对话实施。

## 要交付的效果

当前语言必须交给支持它的模型；切换语言后继续录音、保留前文，不再把日英键盘对的英语送进日语模型。明确区分主识别和第二遍识别，设置显示的组合必须与实际运行一致。

| 当前语言 | 主识别 | 第二遍识别 | 表现与边界 |
| --- | --- | --- | --- |
| 中文 | Paraformer 中英 INT8 | SenseVoice INT8 | 主识别持续预览，分段后第二遍识别 |
| 英文 | 同一 Paraformer 中英 INT8 | 同一 SenseVoice INT8 | 日英状态下也必须按英文路由；不得强制按日语或中文处理 |
| 日语 | ReazonSpeech K2 v2 Zipformer，优先约 153 MiB INT8，保留原版选择 | 无 | 离线解码与滚动预览，不能称为原生增量流式 |

SenseVoice 在本次组合里只承担中英第二遍识别，不擅自改成英文主识别，也不加到日语后面覆盖日语结果。第二遍是重新识别同一段音频，不是读取首遍文本进行逐字校验，不能保证每句更准。多语言 SenseVoice 使用 auto，避免中英切换时固定为中文而改坏英文。

这是明确语言切换的处理契约，不等于已经解决同一句日英自动混说。ASRInput 原有多选语言/auto 是另一种输入状态，迁移时必须检查、保留用户配置并说明其实际行为；不得将多选默认为日语，也不得以错误中文预览充当日语识别。不要借本次迁移擅自落地尚在讨论的“最优模式”菜单或重选模型。

## 必须移植的行为

1. 按当前语言和实际模型能力选主识别器；键盘对、界面语言、模型架构名都不能代替模型语言能力。不支持或文件缺失时明确报告，不静默换中文/日语模型或伪称已启用。
2. 中英选择相同组合时复用当前组合，语言切换不重开音频设备；日英切换时采集继续，工作线程完成旧段并切换不兼容识别器，保留已确认文字。不要承诺切换加载零等待，应记录实际等待及缓冲边界。
3. 每块音频与录音前缓冲保留采集时的语言/会话身份；切换后不得给旧音频重新贴上新语言标签。切换期间的缓冲需要有界，不无限累积。
4. 段身份检查需覆盖异步推理完成及 UI/提交回调执行时刻，旧段迟到的预览、结果和状态不能覆盖新段；定稿只提交一次。保持停止/取消/重启和异常释放语义。
5. “按语言配置”不等于每种语言都常驻一套模型。Android 当前只保留一个活动组合；中英共享同一模型身份，组合可包含 Paraformer 与 SenseVoice。ASRInput 的麦克风/桌面两路需要独立音频状态，不能简单照抄 Android 单路所有权；明确实测实例数与内存，不默认同时常驻三语主模型。
6. 日语保留已有停顿定稿、滚动预览和纠漏处理，不重新引入“攒满两秒才启动/固定 1.2 秒才刷新”。离线滚动重算有计算成本，不凭算法名承诺 0.3 秒出字。
7. 不破坏已有标点规范化、个人词库、提交目标、字幕和转写语义。规范化语言与声学模型语言分离，不能因中文标点口令把英文送进中文专用识别器。

## 来源与读取顺序

**来源主目录是 `D:/GitHub/CyIME`。大量改动尚未提交，不能只拉远端 main 或只看 HEAD 当成 1.9.6。** 原 Git HEAD 为 `1044aef6a16c7a3b2a284268cc2fa62046421f11`，实际构建源码指纹为 `983f547eff964cf6f4a06740a0f1838e6c76a3a061840ca2a5586b364883302e`。相关源码哈希清单：`.codex-artifacts/speech-language-20261007/handoff-source-manifest.json`。接收项目只读这些来源，不改 Android 工作区。

先读 [本轮修复与实际交付](speech-language-delivery-20261007.md)、[同语料模型对照](speech-model-comparison-20261007.md)，再按以下文件理解行为，不直接整包复制平台代码：

| Android 文件（均在 `app/src/main/java/com/kingzcheung/xime/` 下） | 作用 |
| --- | --- |
| `service/VoiceKeyboardLanguages.kt`、`service/VoiceRecognitionHandler.kt` | 当前语言路由、不中断录音的切换入口、文本规范化分离 |
| `speech/SpeechRecognitionManager.kt` | 音频语言快照、切换缓冲、旧结果隔离、最终文本衔接 |
| `speech/OfflineAsrBackend.kt`、`speech/AsrModelManager.kt` | 能力与配置匹配、SenseVoice auto、模型复用身份 |
| `speech/SpeechModelCatalog.kt`、`speech/SpeechModelSelection.kt`、`speech/SpeechLanguages.kt` | 精确模型来源/哈希、主模型和第二遍组合、语言范围 |
| `speech/ResidentSpeechSessionOwner.kt` | 当前组合生命周期与释放边界 |
| `speech/LocalSpeechSession.kt`、`speech/JapaneseZipformerDecoding.kt`、`speech/SherpaSpeechEngine.kt` | 日语解码、预览/定稿及纠漏路径 |
| `speech/SpeechDetectionLevel.kt`、`speech/SpeechSegmentation.kt` | 已有低音量 VAD 与分段；不要恢复被修正的能量硬拦截 |

可复用的行为测试：

- `app/src/test/java/com/kingzcheung/xime/speech/SpeechRecognitionLanguageSessionTest.kt`：日→英→日、单次采集生命周期、兼容中英不重建、旧结果隔离。
- `app/src/test/java/com/kingzcheung/xime/service/VoiceRecognitionHandlerFinishTest.kt`：英文侧正确起始、语言切换、结束及标点行为。
- `app/src/androidTest/java/com/kingzcheung/xime/speech/LocalSpeechServiceTest.kt`：`japaneseEnglishJapaneseUsesSupportedModelsAndRecognizesRecordedSpeech` 实际模型回放。
- `.codex-artifacts/speech-language-20261007/device-language-switch-results.json`：实际输出，不用“通过”掩盖错字。

模型本地目录：

- `D:/GitHub/ASRInput/models/sherpa-onnx-streaming-paraformer-bilingual-zh-en`。
- `D:/GitHub/ASRInput/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`。
- `D:/models/ASRInput/sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01`；核对所需 INT8/FP32 文件，不用目录名推断精度版本。
- 准确权重、固定 revision 与 SHA256 以 Android `SpeechModelCatalog.kt` 为准；读取已有共享目录，不复制到 worktree，不改共享权重。

## 两个接收项目

### 原 ASRInput

交给已有对话 **「优化 C# 实时语音识别」**，ID `01a0846b-a18a-7180-bef2-994cc1caa25a`，工作目录 `D:/GitHub/ASRInput`。不是另开训练任务；「查看流式语音训练方案」的既有研究可只读参考。

已检查当前 `ASRInput.CSharp/src/ASRInput.Desktop/Services/RecognitionModels.cs`：仍只有 two-pass/paraformer/sensevoice/nemotron，日语双模型被转到 SenseVoice，没有本次日语 Zipformer 组合。README 也仍解释日语会先显示错误中文。这些是需要实际更新的旧行为，不是已迁移成果。

接收方负责桌面模型工厂、语言配置/迁移、录音与双路会话、预览及一次定稿、相关菜单说明和测试。核心落点为 `ASRInput.CSharp/src/ASRInput.Core/Asr/`、`ASRInput.Desktop/Services/RecognitionModels.cs` 及 `ViewModels/MainViewModel.cs`，按实际调用核对，不只改标签。保留字幕与转写的原有区别、用户个人词库和历史设置。

本项目内写接收实施报告 `docs/speech-combination-20261007.md`，列变更文件及哈希、实测结果/反例、模型占用与延迟、实际交付状态。如需供 Windows 复用，提供明确的已验证文件清单，不能让对方复制仍在修改的半成品。

### CyIME Windows

交给已有对话 **「启动 CyIME Windows 项目骨架」**，ID `01a0ee7f-0df7-7483-ab9a-ad9b05fc58b4`。该对话登记 cwd 是 Android，但实际目标必须为 `D:/GitHub/CyIME-Windows`，不要误写 Android。

已检查 `speech/CyIME.SpeechHost/VoiceOptions.cs` 与 `VoiceBackend.cs`：仍是 Paraformer/SenseVoice/dual；非 SenseVoice 模式仅允许 zh/en/auto。`speech/README.md` 的占位程序描述已落后于实际代码，不能用它判断未实现任何语音功能。

接收方负责 `VoiceOptions` 配置迁移、`VoiceBackend` 工厂、`Recognizer`/会话切换、`settings/CyIME.Settings/SpeechModels.cs` 和 `SpeechSettings.cs` 的真实绑定；检查 `speech/ASRInput.Core/Asr/` 中 vendored 源码与原 ASRInput 的差异，按已验证边界同步。现有来源记录在 `third_party/speech/ASRInput-PROVENANCE.json`，移植后更新来源、修改说明和许可，不能继续写未修改。

遵循 Windows 仓库既有约束：TSF 输入路径不换成 SendInput，后台串行构建/检查，不自动开麦、不接管桌面；本次授权是实施与验证，不自动扩大为安装/公开发布。在项目内写 `docs/SPEECH_COMBINATION_20261007.md` 并更新 Windows 自己的 Todo，保留用户复选状态。

## 两端必须自行完成的检查

| 场景 | 判定标准 |
| --- | --- |
| 日语侧切英文再切回 | 实际模型分别为日语 Zipformer / 中英组合 / 日语 Zipformer；英文必须输出英语，不只是验证枚举或按钮切换 |
| 中英来回切换 | 同配置可复用，不固定把英文按中文二次识别；采集不中断、已完成文字保留 |
| 切换中仍有音频及迟到回调 | 无错语言重标、无旧结果覆盖、无丢首段或重复上屏，停止/取消能释放资源 |
| 日语第二遍处理 | 不加载 SenseVoice 修正日语；INT8 与原版能力范围一致 |
| 旧设置/缺模型/不支持语言 | 迁移可追溯、用户设置不丢失；缺文件明确失败，不静默改变组合 |
| 空白/噪声/低音量/短词/长句 | 记录实际输出和耗时；错误样本保留，不通过修改参考答案让测试变绿 |
| 内存与延迟 | 记录进程实例数、加载/首字/停顿定稿/切换等待，分别命名，不把桌面 RTF 当交互延迟 |
| 各平台的输入契约 | ASRInput 双音源不串段；Windows 焦点及 TSF 目标不乱、定稿只提交一次；配置界面与实际引擎一致 |

优先复用已有本地 WAV：`D:/CyIME-Data/speech-lab/fleurs-20261007/`，运行入口与固定参考稿见 `D:/GitHub/CyIME/tools/speech_lab/README.md`。各项目按自己的实现跑，不能直接引用 Android 测试数量作为本项目通过。

Android 当前证据：相关 138 项 JVM 检查、最终 49 项 Handler 复查、手机实际模型日英日回放、2 项设置检查通过。手机回放是三个后端顺序会话，连续采集切换另由管理器 JVM 测试覆盖；尚无真实麦克风混语验收。英文回放仍有错词和多字，不能继承“英语效果已解决”的结论。模型对照只支持中文第二遍有收益，日语无稳定收益；不说明所有场景最优。

## 交接与实施状态

本文件为交接契约，写好文档/发送消息不代表两端已改完。接收方各在自己的对话中继续实施并汇报；本对话只维护交接回执，不覆盖接收方源码，不代替用户验收。

| 项目 | 对应对话 | 当前状态 |
| --- | --- | --- |
| ASRInput | 优化 C# 实时语音识别 | 2026-10-07 已发送实施任务，发送工具确认目标对话 ID；实施与验收待该对话报告 |
| CyIME Windows | 启动 CyIME Windows 项目骨架 | 2026-10-07 已发送实施任务，发送工具确认目标对话 ID；实施与验收待该对话报告 |
