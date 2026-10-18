# 日英语音切换补修与 10.6 待办核对（2026-10-07）

本轮首先交付日英语音修复。用户已自行安装 `1.9.5 / 20261097 / security-20261007`，实际手机也报告该版本，因此下一修复包使用 `1.9.6 / 20261098`，不覆盖同号产物。唯一连接设备为 SM-S9180（R5CW13EYCSF）。全部源码、检查和构建仍在 `D:/GitHub/CyIME`。

## 原因与交付边界

- 旧 `VoiceKeyboardLanguages.recognitionLanguage` 把日语布局的英文状态归到日语，绕过英文模型选择；只支持日语的 Zipformer 不会因此获得英语能力。
- 原测试还将“英文侧启动日语模型”写成预期，掩盖了产品错误。改为验证实际英语选择、日英双向切换和一次录音的生命周期。
- 现有未交付草稿已改为按当前语言选模型、SenseVoice 自动检测语言，并在录音中由音频工作线程更换不兼容模型；此前的定位和草稿不等于已安装修复。
- 本轮补齐原草稿的检查：音频块携带采集时的语言，前缓冲不把旧语言音频重新标成新语言；已退出识别段的迟到主线程回调不能覆盖下一段结果。旧段完成后保留文字，继续采音；同一个兼容组合可继续使用。
- 保留手机现有英文和日语选择，使用用户实际选择的英文模型进行回放。曾提出直接改用 SenseVoice，被用户明确否定；没有执行该配置变更，测试也不再覆盖英文选择。日语 Zipformer 不支持英语，不可把键盘切换成功说成模型支持英语。
- 之前把 Zipformer 架构的多语言模型范围误说成当前 ReazonSpeech 模型的能力，是错误承诺；[该模型官方说明](https://huggingface.co/reazon-research/reazonspeech-k2-v2)将其定义为日语识别。当前手机日语仍选 `zipformer-ja-reazonspeech-int8`，中英均选 `paraformer-sensevoice`：Paraformer 主识别、SenseVoice 第二遍识别；不会改成 SenseVoice 单独主识别。
- 将文本规范化与声学识别语言分开：中英键盘对保留原有口述中文标点处理，但英文音频不得因此送到仅支持中文的模型。此补修不改变声学模型、音频缓冲和已验证的模型切换路径。

## 检查记录

- 第一条手工 Gradle 命令的 `-Pkotlin.compiler.execution.strategy=in-process` 未加引号，被 PowerShell 拆成错误任务名；已正确引用重跑。固定交付入口使用参数数组，无此问题，不新增临时交付入口。
- 首次相关 JVM 回归 138 项、5 项失败：两个原有语言预期仍按旧错误映射／强制语言缓存书写，三项预热测试未给新增驻留设置提供 SharedPreferences。更新测试夹具并按用户语言行为契约纠正预期，没有跳过失败。
- 复跑相关 JVM 回归 138 项全部通过，包含日→英→日单一麦克风、旧结果隔离，以及兼容中英模型不重新启动的反例。日志 `.codex-artifacts/speech-language-20261007/delivery-check.log`。
- 真机公开音频回放通过：按手机原配置依次启动日语 Zipformer INT8、英文 Paraformer＋SenseVoice、日语 Zipformer INT8；配置前后完全相同，未修改用户英语选择。三次模型加载约 4.38／3.25／3.58 秒，测试不采麦克风；连续录音生命周期由前述真实管理器 JVM 回归覆盖，不能把两项合称为真实口述验收。
- 英文样本参考为 “However, due to the slow communication channels, styles in the west could lag behind by 25 to 30 year.”，设备输出为 “However due to the slow communication channels Sts in the West could lag behind by 25 to 30 years I”。路由和有效英语输出检查通过，仍有错词及多字；没有宣称准确率问题已解决。完整结果 `.codex-artifacts/speech-language-20261007/device-language-switch-results.json`。
- 设置界面两项设备检查首跑一项失败：测试误将校正“关闭”与对话框“关闭”按钮当成唯一节点，已将测试定位限定在对话框，不改产品绕过检查。首项的独立语言选择与不兼容模型过滤已通过；已查看测试宿主中渲染的设置页。图中配置为测试例子，测试结束后恢复原配置，不能当成用户当前选择。
- 设置界面两项设备检查复跑全部通过，日志 `.codex-artifacts/speech-language-20261007/device-speech-settings-final.log`；渲染证据 `.codex-artifacts/speech-language-20261007/device-model-settings.png`。测试结束后读取持久设置，确认恢复原有中英 `paraformer-sensevoice`、日语 INT8 选择。
- 最终文本规范化补修后，49 项 Handler 检查通过；正式包重新构建。此前设备音频回放和设置渲染针对相同声学路径及界面，不把它写成最终 Release 的麦克风实测。日志 `.codex-artifacts/speech-language-20261007/final-check.log`。

## 实际交付

2026-10-07 手机报告更新时间 09:15:59，主目录固定入口已保留数据覆盖安装 **1.9.6 / 20261098 Release** 至 **SM-S9180（R5CW13EYCSF）**。安装入口确认设备 APK 哈希一致，再读取系统包信息确认版本、非 Debug 属性及已安装状态。未公开发布，平板未更新，真实口述未验收。

- 固定留档：`D:/CyIME-Data/deliveries/1.9.6/20261098/CyIME-1.9.6-arm64-v8a.apk`。
- 文件大小：111,040,073 字节。
- SHA256：`1823c818c26050ab4dc148522564a203e16267d32a253f20400d6e00f261f7d3`。
- 同目录保留构建回执 `latest.json` 和安装／验证边界 `delivery.json`；旧版本和本轮未安装草稿均未覆盖。
- 构建与安装日志：`.codex-artifacts/speech-language-20261007/delivery-release-final.log`、`delivery-install.log`。
- 尺寸整套门禁按已有指令未运行；已有检查不等同用户验收，也不代表模型错词已消除。

## 待办核对

用户的复选状态保持不变，不能用本轮装包把整份 10.6 列表勾选完成。

- 拼音空格光标、低音量检测等已有未安装修改随累计源码交付，仍需执行其对应检查；用户已安装 1.9.5 不代表这些功能已验收。
- 14 键展开候选目前仅九键接入拼音侧栏，存在明确入口缺口，待补修与验证。
- 剪贴板全页面下拉、短距离／惯性展开、固定内容动画、菜单宽度、图片分类及纹理仍有待实现项，不随语音回归宣称完成。
- 上下滑符号已有方向锁，但用户反馈未通过，需从真实键盘入口复现，不以单个独立按键测试代替。
- SuperDisplay 共存问题遵从用户之前的停止指令，不在本轮重新修改主机服务。

