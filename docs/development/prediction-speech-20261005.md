# Transformer 与语音专项：2026-10-05

## 目标与范围

按用户要求继续 Todo，直接在唯一授权平板 SM-X800（R52T800SKWZ）调试 Transformer 联想和本地语音。目标是模型选择与实际运行一致、长上下文不失效、模型工作不堵住输入线程、语音开头能收录且旧会话不能影响新录音。保留用户模型、词库、布局、学习和设置；未提交 Git、未公开发布、未清除应用数据。开发和交付只使用 D:/GitHub/CyIME。

同批仍用 1.8.0，交付序号从 20261083 增至 20261084。前文“不打包”由本轮“直接 ADB 调试”及用户解锁设备的指令替代。既有紧凑选择器、表情和相册 OCR 源码随本轮一并验证、交付，不把旧包截图作为新源码证据。

## 已复现的问题及修正

### Transformer

- `NativeOnnxEngine.initialize` 在 JNI 已加载时丢弃原生返回值，模型路径不存在也返回成功。设备负例已复现；现在沿用真实返回结果。
- 两档本地模型的位置编码最多 64 token，原实现截取 64 个字符后还添加 BOS，实际可能送入 65 token；200 字输入在平板触发 Gather 索引 64 越界。现在按编码后的 token 限制总长度，保留 BOS 和末尾 63 token。
- 常驻 HOT 状态曾挡住显式卸载，选新模型时可能仍使用旧权重；卸载还通过 runBlocking 等待 IPC。现在立即失效可见状态，在 IO 上串行卸载/加载，按实际模型 ID 和连接身份确认结果，取消及旧连接掉线不能复活或清除新状态。
- 原生层拒绝空输入/非正 topK，验证输出类型和形状；NNAPI 失败转 CPU 时清空已释放的错误指针，避免失败路径重复释放。
- 模型加载失败但基础词频可用时，设置明确显示正在回退基础联想，不再以基础词频成功冒充神经模型成功。

### 语音

- 首次录音准备原来先等待绑定/预热。现在本地后端的准备阶段不等模型；实际绑定、加载和投递仍在音频工作线程，麦克风就绪即可收音，沿用有界队列保存开头。预热和真正会话共用后端，开始操作与预热收尾串行，卸载后的旧实例不能复活。
- IPC 音频投递错误原来被吞掉，可能留下“麦克风仍开着却不识别”的状态。现在传回录音拥有者的失败路径，释放麦克风并显示错误；取消先作废回调，再停止后台会话。
- 旧语音管理器的延迟 release 会作用于 AsrSupport 共享实例，可能在新管理器已开始录音时停掉它并禁用投递。会话停止已经由录音工作线程负责，因此本地共享后端的管理器 release 不再重复停止/清状态；显式关闭仍通过 releaseModel，空闲权重由服务回收。最终构建前检查发现后，主动停止尚未产出 APK 的构建，补该反例再走同一入口。
- 单连接样例成功后，组合回归出现一次日语空结果。继续用两个真实服务连接复现：旧连接在新连接开始后调用 stop/cancel/release，会清空新连接的识别。现在内部 AIDL 的音频、停止、取消和释放携带独立回调 Binder 身份，由会话拥有者在同一状态锁内核对；旧连接不能关闭或投递到新录音。内部私有 IPC 与 APK 一起更新，不改变用户配置/插件协议。原失败日志为 `.codex-artifacts/speech-ownership-before.txt`，不能用后来的单次重试成功替代修正。

### 验证中发现的 UI 问题

- 实际预览中方案卡片仍显示“中文26键”，原因是旧模式名称被直接用于方案标签。键盘内及设置内两处选择器现在共用方案标签：普通拼音显示“拼音”，双拼/五笔/外部方案保留具体名称；未修改键盘几何、底排或工具栏。
- 语音模型 UI 测试仍要求 Zipformer 排在 Paraformer 之前，与已经确定的 Paraformer 默认顺序相冲突。修正测试顺序，继续检验真实模型持久化与二次修正开关，不能只删除失败断言。

## 验证证据

真实模型使用已安装文件。语音仅使用公开的中、英、日 WAV，不录制、存储或上传用户声音；麦克风释放测试真实打开 AudioRecord，但立即丢弃音频。公开素材固定于 [Sherpa SenseVoice 测试音频提交](https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07/test_wavs)。测试不覆盖设备模型文件；每项设置测试恢复原值。

- 初始缺模型假成功和长上下文越界：2 项均先失败，日志 `.codex-artifacts/prediction-native-before.txt`；修正后的真实两档模型、缺失路径及生命周期切换 3 项通过。
- 两档短句推理实测约 2～5 ms，长上下文约 10～19 ms；small → base → small 切换核对实际模型 ID 与真实输出，主线程释放实测 20 ms。数据 `.codex-artifacts/prediction-native-after.json`、`prediction-lifecycle-after.json`。这些是本平板样例值，不是所有设备延迟承诺。
- 首轮相关 JVM 回归 153 项通过，包含预测取消/剪贴板不触发预测、语音启动/语言/拥有者与输入框回调。后续会话隔离和方案标签修改另跑相关回归，见 `.codex-artifacts/speech-owned-build.log`。
- 单连接真实语音复测通过：Paraformer 中文、Paraformer＋SenseVoice 中文冷启动/常驻重开、英文、SenseVoice 日文、取消后重开、静音空输出、解绑后重新绑定。数据 `.codex-artifacts/speech-diagnostic-results.json`；常驻重开约 3～9 ms，冷加载约 1.6～4.2 s，未将冷加载写成即时完成。
- 解锁后真实麦克风 7 项通过：停止、取消、初始化阻塞、故障释放与重复开始等。表情 4 项、输入方式 6 项 UI 检查通过；语音设置旧顺序断言失败后单独修正并复测。最后会话隔离及 UI 复测结果在下方交付记录补充。
- 相册裁切的 2 项 Android 像素检查通过。本轮没有完整走系统相册选图→拖动缩放→识别→编辑保存，因此不宣称该闭环已验收。

### 过程中的错误

直接调用 Gradle 时曾复用缺 jlink 的 VS Code JRE，后改回已有完整 JDK/固定构建入口。锁屏时 UI 测试无法显示 Compose 树，用户解锁后重跑。一次先前 instrumentation 尚未退出就启动另一组，导致前组进程被终止；这是测试编排错误，此后等待完整结束才继续设备测试。前组“Process crashed”不作为产品崩溃结论，也不计通过。设备测试的结束码不单独代表通过，检查 JUnit 汇总与每项失败。

## 保留的未完成项

- 这轮修复现有 Transformer 运行链路，没有训练/导入新的模型。small/base 当前是单 token 后续预测，仍会产生“夸”“关”等片段；“补全整个后续内容”未完成，不能以有输出就勾选 Todo。
- 中文公开样例仍有“开放”被识别成“开饭”，纯 Paraformer 还有尾字不全；识别准确率、用户实际说话、长段落与复杂环境仍未验收。
- [清简项目的神经重排记录](https://github.com/qingjian-team/qingjian/blob/main/docs/notes/neural-rescoring.md)提供候选重排、延迟触发及保留个性词频的方向；其训练目标与现有后续预测不同。本轮未擅自替换中文解码排序或导入其权重，后续应先建立同语料准确率/延迟对照。
- OCR 黑画面实拍复验、旋转/倾斜/分块文字识别保留用户未通过记录；编辑盘最小尺寸未在本轮重新设计。手机功能未验收，公开 Release 未发布。

## 最终设备复测与交付

相关 JVM 回归 **133 项、0 失败/跳过**，其中拥有者隔离11项；不与前次153项重复相加。平板批次 **18 项全部通过**：真实语音3、真实麦克风7、语音设置2、输入方式6。旧连接干扰用例先失败再通过，日志 `.codex-artifacts/speech-owned-device.txt`。语音样例在 `.codex-artifacts/speech-owned-results.json`：常驻重开19/8 ms，中文收尾313～357 ms；模型加载与识别准确率仍按上面的限制描述。

补修旧管理器延迟释放共享后端后，语音及输入框回调 **118 项 JVM 回归通过**，日志 `.codex-artifacts/speech-shared-release-build.log`；新增反例要求延迟 release 后仍能投递 PCM 并正常结束当前会话，不能仅检验“不崩溃”。最后设备复测 **11 项全部通过**，含真实模型4项（新增延迟管理器释放、旧连接错误投递被拒绝）和麦克风7项，日志 `.codex-artifacts/speech-shared-release-device.txt`；该部分替代前次重叠结果，不重复累加通过数量。

实际查看最终渲染 `.codex-artifacts/prediction-speech-ui-final/`：280/760宽的最小面板均保留两排可见选择，管理卡片可横滑到达；“拼音”和“小鹤双拼”标注正确。2倍字号的横滑方案列表与语音设置中模型名、选中圆点、二次校正开关已检查。另查看 `.codex-artifacts/emoji-final.png`，单操作栏下三行表情完整显示。测试图来自设备实际 Compose 渲染，不是手绘示意。整套尺寸门禁未运行。

固定入口 `auto.cmd` 最终成功构建并保留数据覆盖安装 **1.8.0 / 20261084 轻量签名 Release** 到 SM-X800。设备包名、版本、非 Debug 标志及实际 APK SHA256 均已核对；安装后设置页冷启动成功。构建耗时2分6秒，未运行整套尺寸门禁，未打内置模型包，未发布 GitHub。

APK **88,715,687 字节**，SHA256 `303bff61cacb1fa649c8ee3053c2228cf294fc7778dee6c5f19f2f7fb030d81a`；固定副本 [CyIME-1.8.0-arm64-v8a.apk](D:/CyIME-Data/deliveries/1.8.0/20261084/CyIME-1.8.0-arm64-v8a.apk)，回执 [delivery.json](D:/CyIME-Data/deliveries/1.8.0/20261084/delivery.json)。构建日志 `.codex-artifacts/prediction-speech-84-final-delivery.log`。当前 APK 对应构建时的源码指纹 `723e5299ae2c6c93fc9250e435e612fe3221c226ddf0ed909caa77dd789e8b8d`；本安装回执是在构建后补记的文档，没有重写原始构建回执或冒称重新构建。

最终原始模型数据另存 `.codex-artifacts/prediction-native-final.json`、`prediction-lifecycle-final.json` 和 `speech-service-final.json`。最后一轮常驻语音重开18/8 ms、中文二次识别收尾314～358 ms；再次观测到预测释放6 ms。清理了仅由本轮创建的 `prediction-eval` 与 `speech-eval` 测试目录（约510 MB），正式模型/设置未删除；USB屏幕常亮恢复原值0。Todo保持用户原有复选状态，写明平板已安装与准确率/完整续写等未完成边界。

2026-10-05 用户追加“手机也装一下”：唯一在线手机为 **SM-S9180 / R5CW13EYCSF**，通过主目录 `auto.cmd --install-only --serial R5CW13EYCSF` 将原 **1.7.1 / 20261075** 保留数据覆盖更新为同一份 **1.8.0 / 20261084** 轻量 Release。未重构建、未卸载、未清数据；再次读回实际版本及设备 APK SHA256，与上述平板用包完全一致。安装日志 `.codex-artifacts/phone-20261084-install.log`，独立回执 `D:/CyIME-Data/deliveries/1.8.0/20261084/phone-installation.json`；手机没有自动运行功能回放，保留未验收状态。
