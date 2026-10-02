# 设备自动适配基础：观察阶段

日期：2026-10-02。承接 [实施方案](device-adaptation-plan.md) 的 P0 源码核对和 P1 基础部分。**已有生产接线，但仍只观察建议，没有启用自动降级执行。**

后续更新：用户澄清优先完成首次安装后的模型分档，现已另行接入 [首次模型分档实现](device-model-defaults.md)。静态采集由共享 `DeviceCapabilityProbe` 提供，增加最高 CPU 频率；语音偏好桥接也读取持久化的自动默认。下文保留第一轮观察阶段记录，其中“不改默认”和 23 项测试仅指当时那一轮；最新默认与验证以首次模型分档文档为准。

## 本轮范围

10 月 2 日后续拆分：`RuntimeRequests` / `RuntimePolicyProposal` 新增独立 `keyGlow`，`animation` 只描述键帽运动，不再用 ENHANCED 代表炫光。桥接读取两个独立设置并兼容旧组合开关；恢复时按动效、联想、炫光逐项等待，仍只观察。最新默认和检查记录见 [首次默认与独立按键效果](device-model-defaults.md)。

新增 `runtime/adaptation/`：

| 文件 | 职责 |
|---|---|
| `RuntimeFacts.kt` | 设备事实、未知值、偏好来源、建议与观察快照；与 InputProfile 独立 |
| `AndroidRuntimeSignalSource.kt` | 一次采集静态能力，活动期间采集省电、低电量/充电、热状态、系统内存和动画许可 |
| `RuntimePreferences.kt` | 只读现有炫光、智能联想、语音设置；保存过的值标为来源未明，不猜测它是否为用户手动选择 |
| `RuntimePolicyResolver.kt` | 纯策略回放、原因、降级及恢复迟滞 |
| `RuntimeObserver.kt` | 观察循环、事件合并、快照代际、取消与监听释放 |

`XimeInputMethodService` 在窗口显示/开始输入视图时启动观察，在结束输入、结束输入视图、窗口隐藏和销毁时停止。采样在 IO 调度器运行，静态设备信息缓存；每 10 秒或系统事件到来时观察，不增加按键处理路径的系统读取。热回调只投递事件，不加载或卸载模型。

隐藏后取消定时采样并移除系统监听，丢弃迟到采样；再次显示保留之前的限制，但重新累计恢复窗口。仅保留最新观察快照，建议改变时写一条 `RuntimeObservation` 本地日志，不采集输入文本、音频、剪贴板或设备唯一标识。

Android 边界依据：[PowerManager 热状态与省电接口](https://developer.android.com/reference/android/os/PowerManager)、[ValueAnimator 动画许可](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled())。热接口放在 API 29 分支；API 28 保留未知热状态。内存从 ActivityManager 采样，不把旧 trim 回调当作唯一压力信号；[ComponentCallbacks2 文档](https://developer.android.com/reference/android/content/ComponentCallbacks2)说明多项旧等级已停止通知。

## 建议的含义

输出命名为 `RuntimePolicyProposal`，刻意与未来已执行的 `EffectiveRuntimePolicy` 区分。每项保留请求值、建议值、原因。`neuralPrediction.proposed=true` 只代表当前压力允许继续检查请求，**仍需语言、资源、预算与所有权校验，不表示模型就绪或已经运行**。

- low-RAM 或内存不足 4 GiB：保守估计；4～8 GiB：标准估计；8 GiB 及以上：仅有资格继续测量。核数不授予增强资格，刷新率没有被用作算力指标。
- 缺失、零、异常内存读数及不可用系统接口保留未知。采样失败来源保存在快照中；不把读失败记为正常。
- 系统禁止动画：立即建议 OFF；严重发热或系统低内存：立即建议减少动效、暂停新的神经增强。
- 省电、未充电时低电量、中度发热：至少两个相隔 10 秒的连续观察才普通降级。重复广播不算额外窗口。
- 正常信号连续稳定 30 秒后，一次只恢复动效或神经增强中的一项；两次恢复至少相隔 30 秒。超过 15 秒的采样空档重新计时。API 28 可以恢复基本动画，但未知热状态不授予增强资格。
- 未取得模型质量、延迟及内存成本证据前，二次校正与投机预热建议保持暂缓。当前未接性能采样，不能将这些值解释为模型速度的测量结论。
- 已关闭功能保持关闭。此桥接不写任何偏好，不迁移 AUTO/MANUAL，也不改变当前模型选择或语言、布局、主题。

此次没有执行适配器，因此当前炫光、联想、语音预热及模型常驻行为仍沿用现有实现。上述门槛是可回放初值，不是真机校准结论。没有修改布局，高度、裁切和触控区域没有新增变化。

## P0 接入清单与真实缺口

| 链路 | 当前入口与所有权 | 下一阶段要守住的边界 |
|---|---|---|
| 初始默认 | `DeviceDefaults.initialize/supportsRefinement`、`BundledModelInstaller` | 现有 6 GiB/4 核推荐仍存在；P1 不改默认或伪造用户选择来源 |
| 联想启动 | `PredictionManager.initialize`、`AssociationManager.initialize`，成功后 `keepWarm/startWarmup` | 轻量先验和个人学习应可独立于 ONNX；暂停不能变成关闭用户学习 |
| 联想恢复 | `AssociationManager.predict` 在断线后重新 `load`；`PredictionManager.getChineseAssociations` 可直接初始化 | 后续所有请求及重连都要经过有效策略，防止暂停后立即重载 |
| 设置及回收 | `SmartPredictionSettingsViewModel`、服务设置监听、`ModelRuntime.unload/trim` | HOT 缓存和活跃引用必须分离；旧 trim 未筛选活跃引用，不能直接接新压力事件 |
| 本地语音预热 | `VoiceRecognitionHandler.initialize`、服务 keep-alive 设置监听、`SpeechToTextSettingsScreen` → `AsrSupport.warmup` | 多入口须收口；预热只允许持有模型，不能获取麦克风 |
| 本地语音加载 | `SpeechRecognitionManager.preload`、`OfflineAsrBackend.initialize/start` → `AsrInferenceClient` | `initialize` 的绑定成功与权重就绪不是同一事实；异步加载需真实回执 |
| 本地语音回收 | `AsrSupport.releaseModel`、`OfflineAsrBackend.release/releaseModel`、`:asr` 60 秒空闲释放 | `release()` 当前只重置会话并保留绑定；`releaseModel()` 才释放/解绑；常驻标志须与真实权重状态分开 |
| 原生语音收尾 | `AsrInferenceService.retireSession`、`LocalSpeechSession.awaitIdle` | 退出中的工作线程仍占有权重，不得把取消协程当成内存已归还 |
| 手写 | `HandwritingEngine.initialize/ensureEngineReady/release`，落笔自愈、结束输入视图释放 | 当前字的任务归属不能被联想预算抢占；不要在策略里调用带文件迁移副作用的模型检查 |
| 跨进程 | `:inference` 和 `:asr` | 当前没有统一实例代际、租约或加载峰值账目；本轮不声称已建立内存预算 |

`InputLatencyTrace` 当前是 Debug/Perfetto 可选路径，结束在 Compose 绘制提交，不是屏幕实际呈现；本轮没有拿它产生手机 P95/P99。现有 `LocalSpeechModelsTest` 可使用本地 `speech-eval` 的 zh/en/ja/long WAV，但样本是可选外置资源，未检查它们的来源/哈希，不能称为已冻结的基准集；其总耗时含模拟送音等待，也不能直接当纯解码 RTF。

本轮未运行设备或模型测速。模型冷启动、驻留/峰值内存、RTF、校正准确率、屏幕帧耗时及观察器开销均为**未知**。P0 的基线测量/固定语料、P1 的显式偏好迁移、完整资源清单和性能汇总仍待接入；不把观察基础完成写成 P0/P1 全部完成。

## 验证

针对性 JVM 测试覆盖：内存边界、未知/API 28 信号、已有偏好来源和只读性、用户关闭、系统动画、低电量/充电、重复事件、危急降级、分项恢复、采样空档、隐藏停止、迟到结果与快速重开。

最终 Kotlin/Java 编译通过，3 个新增测试类共 **23 项通过，0 失败、0 错误、0 跳过**：纯策略 14 项、观察生命周期 6 项、偏好兼容与只读性 3 项。结果与源码哈希保存在 `.codex-artifacts/device-adaptation-validation.json`，日志为 `device-adaptation-tests.log`。

构建输出隔离在 `.gradle/device-adaptation-app-check` 与 `.gradle/device-adaptation-plugin-check`。语音依赖准备任务使用哈希匹配的已有缓存，没有下载模型；跳过词典、Rime 清单和原生准备任务，只验证 Kotlin/JVM 范围，不验证原生引擎、词典部署或整包。验证使用本机完整 JDK 21 和一次性构建进程，避开编辑器精简 JRE 缺失 jlink 的环境问题；未修改项目 JDK 配置。

没有打包、安装、发布或 Git 提交，没有恢复尺寸打包门禁；Android 系统监听的实机行为、耗电、发热、实际输入流畅度及用户场景均**未验收**。

下一步先为 ModelRuntime 和跨进程加载/释放建立可确认的所有权，再接真实模型资源及性能证据，最后让功能消费有效策略；不要直接把本轮建议布尔值接到现有卸载函数。
