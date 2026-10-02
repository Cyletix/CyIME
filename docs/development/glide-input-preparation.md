# 滑行输入准备与离线基线

日期：2026-10-03。状态：**原型与接入准备，未接入 Android，未真机验收**。

用户要求尽可能多准备和初步修改，同时不干涉其他会话。本轮只新增 `tools/glide_lab/` 与本报告，并按仓库要求在 Notebook 当天台账增加一条。现有应用源文件、Gradle、版本元数据、词库、九键、主题及共享开发报告均不改；没有创建／切换分支、提交、打包、安装或发布。独立编译只读已有缓存、写入自己的新结果目录。目标继续属于现有未发布1.4.0批次，不消耗交付 versionCode。

## 可行性判断

可以逐步实现可靠的滑行输入；现在不能声称达到 Gboard。轨迹经过的字母与用户意图并非一一对应：弯角会被削平、相邻键会误碰、重复字母可能只经过一次，同一形状可能对应多个词。需要空间容错、词典、上下文、候选纠错和低延迟协作。

Google 2017年公开的 Gboard 架构结合空间模型、词典和语言模型，以束搜索解码；其神经空间模型使用 LSTM/CTC，并讨论从纠正交互获得训练信号。这是历史公开说明，不代表已掌握2026年产品完整实现。[Google Research](https://research.google/blog/the-machine-intelligence-behind-gboard/)

SHARK² 将轨迹形状、绝对位置和语言上下文分开评分，讨论重采样、首尾剪枝和相似词形歧义；适合作为可解释基线的思路参考。论文公开不等于提供可复制代码或数据许可。[作者托管论文](https://pokristensson.com/pubs/KristenssonZhaiUIST2004.pdf)；另有 [Google 手势识别 LSTM 论文](https://research.google/pubs/long-short-term-memory-neural-network-for-keyboard-gesture-recognition/)。

本轮采用原创 Kotlin 几何基线作为可复现起点，不训练神经模型。先测哪些情况能召回正确候选，再决定词表索引、搜索和学习模型的投入。

## 上游核验与复用判断

以下链接固定到本轮核验的 commit，后续上游变化需重新确认。

| 项目 | 本轮查到的事实 | 可借鉴范围 |
|---|---|---|
| [HeliBoard](https://github.com/HeliBorg/HeliBoard/blob/415c45f15c47de3de74eeeb9fdb8e56e46d1389a/README.md) | README 的滑行依赖外置闭源库；项目 GPL-3.0、部分 AOSP 文件另有许可 | 触摸与候选交互，不作为开放识别器直接移植 |
| FlorisBoard [Classifier](https://github.com/florisboard/florisboard/blob/fe1241f4921b3eae923571ff7a3e113a7e10d677/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt) / [Manager](https://github.com/florisboard/florisboard/blob/fe1241f4921b3eae923571ff7a3e113a7e10d677/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTypingManager.kt) | 统计分类器整类被注释；manager 的 classifier 为 Unit，候选更新直接返回；相关文件 Apache-2.0 | 设计参考，不能当即插即用实现 |
| AnySoftKeyboard [Detector](https://github.com/AnySoftKeyboard/AnySoftKeyboard/blob/742bf8817aae1a808be6c5fa72bb10f0182ff659/ime/app/src/main/java/com/anysoftkeyboard/gesturetyping/GestureTypingDetector.java) / [许可](https://github.com/AnySoftKeyboard/AnySoftKeyboard/blob/742bf8817aae1a808be6c5fa72bb10f0182ff659/LICENSE) | 有实际词轨迹预计算、词首索引、拐点／方向比较、端点容错与词频融合；Apache-2.0 | 优先作为以后对照实现，需解耦 Android/Rx/词库；没有与 Gboard 等效的证据 |
| [OpenBoard](https://github.com/openboard-team/openboard/blob/c3772cd56e770975ea5570db903f93b199de8b32/README.md) | README 将 glide typing 列为 TODO，并拒绝引入闭源 Google 库 | 不作为现成识别器选型 |
| AOSP LatinIME [工厂](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/127336e9f29d69607eab55982324b210279ae8c5/native/jni/src/suggest/policyimpl/gesture/gesture_suggest_policy_factory.h) / [初始化](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/127336e9f29d69607eab55982324b210279ae8c5/native/jni/src/suggest/policyimpl/gesture/gesture_suggest_policy_factory.cpp) | 工厂指针初始化为0，没注册实现则返回0；文件 Apache-2.0 | 扩展接口，不是公开完整的 Google 手势识别器 |

代码、词表、词频、模型和训练集的许可必须分别记录，不能因项目代码开放就推断资源均可分发。英文可进一步评估 ESDB／原 SCOWL，但它是拼写词表，不是现成上下文模型；不同词表级别及部分来源有附加声明。[固定版权文件](https://github.com/en-wl/wordlist/blob/1e5b7d3a72f47a71da5d28686c1dd4b397178485/Copyright)、[项目](https://wordlist.aspell.net/)。本轮没有下载或引入这些资源，未选定许可与质量均满足需要的中文真实轨迹集。

## 英文和中文需要两条解码路径

英文：轨迹 → 多个拼写候选及空间代价 → 词频／上下文重排 → 整词候选与确认。需明确未知词、缩写、人名、大小写、撤销纠正、自动空格和标点交互；实验阶段先展示候选供确认，不默认自动上屏。

中文全拼：轨迹 → N-best 拼音及音节切分 → 汉字词组候选 → 空间与语言分数融合。`xian` 的边界、`shijie` 的同音词均可能歧义；先硬选一条字母串再交给 Rime 会丢掉可能的正确答案。本轮中文样例只将手写词条标签挂在编码上，**没有经过 Rime**，不能称中文滑行已实现。

后续先验证 Rime 隔离探测能力：候选试算不能修改用户当前组合、选中位置、学习库或上下文；不能把多个探测串轮流注入活跃会话。需要可恢复／独立的无学习探测会话及 owner 校验，再约定分数尺度与重复汉字候选合并规则。Rime 候选权重可否可靠取得、批量查询开销、跨音节拼音候选生成均尚未验证。九键同键多字母会增加另一层歧义，不把26键实验扩展为九键支持；本轮未改九键排序，因此不运行或占用其回放任务。

按 `InputProfile` 的语言、方案、布局与明确能力启用，不新增“滑行语言”，不凭 schema ID 猜测，不把英文／拼音的识别器用于双拼、五笔或日语。最初产品接线宜限定完整英文26键与明确资源；中文单独通过解码评估后再开放。用户若优先中文，共用几何不变，后续先完成拼音 N-best 与隔离 Rime 探测。

## 已读实现与接入契约

以下为当前工作目录的观察，其他会话会继续修改，接线前必须重读符号与行为。

| 边界 | 现状与以后必须遵守的契约 |
|---|---|
| 字母键盘根层 | `KeyboardLayoutScreen.kt` 调用 `KeyboardLayout.kt::KeyboardLayout`，没有独立 Latin26KeyboardLayout；根层适合仲裁连续轨迹 |
| 单键 | `ExclusiveKeyGesture.kt` 在 down 即消费事件，50dp上下／左滑、60dp横移取消点击；长按定时器会直接执行。子键在 Final pass 看到父层消费时取消点击并清理反馈，可复用取消契约，但要早于动作执行取得独占 |
| 符号 | `SymbolInputMode.kt` 的300ms长按可能立即上屏；已经产生动作后不能再把同一次触摸转为滑行 |
| 整键盘光标 | `KeyboardView.kt` 的 KEYBOARD 光标模式60dp水平激活，与滑行直接冲突；实验阶段明确互斥并显示原因，不改掉用户设置 |
| 空格与功能键 | `SpaceKeyButton.kt` 支持光标、重复空格与语音；保留现状。轨迹只能从字母键起始；Shift滑选、退格、空格、语言键、多指均排除 |
| 系统取消 | `KeyboardLayoutScreen.kt::swipeCancelEpoch` 处理 ACTION_CANCEL；新轨迹和结果必须随取消代际失效 |
| 几何 | `KeyButton.kt` 记录 boundsInRoot，但触摸边缘与可视内容不等宽；需要稳定键实例ID、字符中心、触摸矩形、根坐标、单位尺度与 geometryRevision 的不可变快照 |
| 已有邻键几何 | `LetterGeometry.kt` 是 Char→Rect，无 remove／版本；分体布局的重复g、v会覆盖，不可直接当滑行快照。首阶段排除重复／合并／缺失键位与分体，而后单独建模 |
| 候选 | `CandidateState.kt` 的 engineRevision 属于Rime；负索引candidateActions属于插件。滑行必须有显式来源／request identity，不能冒充这些候选 |
| 英文联想 | `ImeKeyboardCallbacks.kt` 的 pendingEnglishText 假定文字已上屏且选词时回删；不适合承载尚未提交的滑行词 |
| 提交 | 在用户操作时捕获 `ImeKeyRouter.captureInputContext()` 的 InputCommandOwner，经 `postRimeJob()` FIFO 与主线程统一 submitText 边界；解码结束时不能重新捕获当前输入框冒充原owner，不能通过可合并UI队列提交 |
| 事务 | 提交成功后再清理候选，保留宿主拒绝、内部编辑器重定向与事件通知；不绕开 XimeInputMethodService 的统一提交路径 |

建议以后状态机：`Idle → Tracking → Claimed → Decoding → Preview → Commit/Cancel`。Tracking阶段仍允许点按与现有短滑，达到经验证的跨键／位移条件才进入Claimed；一旦其他手势胜出、多指或系统取消则进入Cancel，本次不能重启滑行。Claimed只消费自己的指针，Preview与Commit之间仍验证会话。

异步请求至少包含 `requestId + admission + inputSessionId + InputProfile快照 + geometryRevision + text/contextRevision + swipeCancelEpoch`。新键、新滑行、光标／选区变化、语言方案布局变化、清空、面板切换、取消、部署、隐藏和新输入框使旧请求失效。即使已取消协程，发布候选、点击候选和实际提交三个边界仍核验身份；后台任务可能忽略取消。敏感／密码输入框不激活，也不记录轨迹。

## 实现与资源边界

本轮的 [实验说明](../../tools/glide_lab/README.md) 给出命令、数据协议和限制。核心实现保留传入布局的绝对位置，以keyUnit统一尺度，按弧长重采样；不逐次产生被滑过的字符事件。模板离线预计算，重复相邻字符折叠，DTW与端点匹配后加入小幅先验。布局／词表构造时检查边界，查询时拒绝坏轨迹；结果只携带候选，不提交任何文本。

词表先验为0～1的示意代价，不能写成真实词频。当前全表比较不适用于大词典；下一阶段在同语料协议下对照词首／末候选桶、前缀树束搜索、模板缓存、重采样和转角特征。需同时看召回损失与延迟，不能只缩小词表得到好看的速度。

## 分阶段验收

1. **离线基线（本轮）**：固定玩具词表与原始合成轨迹；验证数学／数据边界、重复字母歧义、同码候选保留、变换不变性和回放可复现性。通过不等于识别质量。
2. **真实采样与几何适配**：独立练习页、明确同意后记录轨迹；导出需带语言、方案、布局快照、时间单位、目标词、参与者／会话匿名ID与资源版本。按参与者或会话划分训练／调参／留出，避免近重复泄漏；不默认采集日常输入。
3. **关闭状态的实验接线**：重读相关实现，单独完成手势仲裁、会话有效性、取消、候选事务及布局版本；增加针对性 Compose／owner 测试后，由用户决定何时打包与真机验证。当前仓库引用的 Compose skill 路径缺失，本轮不改 Compose，因此不依赖该缺失文件。
4. **语言解码**：英文词表许可及排序；中文 N-best 拼音、隔离 Rime 探测、汉字组合及语言分数融合分别验证。共享几何不能替代各语言验收。
5. **质量提升**：取得有许可的真实轨迹后对比上下文重排、个性化和必要的CTC等模型；始终保留几何基线作回归对照。

| 测试分组 | 必须记录／核对 |
|---|---|
| 短词、近形词、重复字母、首尾偏移、削角、抖动、停顿、稀疏采样、未知词 | Top-1／Top-3／Top-5、目标完整名次、拒识、错误激活、每词纠正次数 |
| 英文／中文 | 英文词错误率；中文拼音召回／切分正确率与最终汉字错误率分开；不混合报告 |
| 性能 | 抬手到候选的P50/P95、初始化时间、实际词表规模、内存、设备温度；电脑小词表耗时不能代替手机 |
| 点按与旧手势 | 快速点按不吞字、不重复；符号、长按、Shift、空格光标／语音、删除、系统取消、多指仍正确 |
| 动态界面 | 横竖屏、缩放、悬浮、重绑定、大字体、键盘隐藏；轨迹层不改变高度，不被裁切、不盖住交互区；分体另建支持条件 |
| 生命周期 | 识别未结束时切框、切语言／方案、移动光标、清空、重新部署，旧候选不能出现或上屏；宿主拒绝不丢待选状态 |

可复用现有回归资产：`SwipeableKeyButtonGestureTest`、`KeyButtonGestureTest`、`KeyboardShiftStateTest`、`InputCommandOwnerTest`、`InputReadinessTest`、`InputCommandQueueTest`、`CandidateCommitTransactionTest`。本轮未改其负责的产品链路，未运行这些测试；不将它们写成通过。布局没有变化，本轮没有运行尺寸门禁或设备验收。

## 本轮验证记录

2026-10-03 实际运行结果：独立 Kotlin 2.4.20 编译通过，核心 **30组检查通过**；Python 回放工具 **12项测试通过**。英文合成回放14/14符合预期（10条词路径、4条不应激活），拼音路径11/11符合预期（7条词路径、4条不应激活）。同形／同码样例采用预期集合，不能把该结果解释为已经正确区分歧义。

结果目录：`.codex-artifacts/glide-preparation-20261003/`，包含 `compile.log`、`checks.log`、逐样例 `replay.tsv` 与 `report.json`；报告记录实际源码、样例及编译器依赖SHA-256。首次独立编译与回放完成，无需重试或变更样例预期；工具边界审查时修复了Unicode换行分隔符、超大数值和Top5统计边界，相关回归已覆盖。

真实触摸、实际词库规模、上下文、中文Rime候选、会话切换、布局裁切／交互区域与真机延迟均**未验收**。没有APK、安装、发布或Git提交；未运行T9回放、应用全量构建及尺寸打包门禁。
