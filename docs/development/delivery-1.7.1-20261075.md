# 1.7.1 / 20261075 轻量验证包（2026-10-04）

用户要求安装并交付安装包，同时指定本次显示版本升为 1.7.1；安装序号从已交付的 20261074 递增为 20261075。此前 1.7.0 为本地验证包，GitHub 最新正式版经交付前检查仍为 1.6.1。本次沿用累计源码和轻量包范围，默认语音继续使用 Paraformer＋SenseVoice。

## 修正范围

- [物理空格选词](hardware-input-state-machine-2026-10-04.md)：组词时确认正常候选，上屏后的智能联想仍只由数字/点击确认，空格照常输入；回车原始编码及方向键移动正文保留。
- [键区比例](live-resize-and-vertical-spacing-20261004.md)：取消额外上下留白和固定手机小键帽上限，首尾行贴近键区边缘，行距不随高度膨胀，默认尺寸和实际绘制共用度量；数字、符号底栏和候选侧栏同步。保留现有尺寸调节范围，极端拉高时键帽仍会增高。
- 包含此前 1.7.0 / 20261074 的所有累计修正，包括大写拖动、按住高亮、剪贴板、悬浮移动条及语音开关。

## 开发检查

物理按键相关 56 项 JVM 检查及布局相关 66 项 JVM 检查通过，零失败、错误、跳过，主应用和 Android 测试源码编译通过；证据分别在 `.codex-artifacts/hardware-space-20261004/` 和 `.codex-artifacts/edge-aligned-keys-20261004/`。没有运行完整尺寸门禁或设备界面测试，交互及视觉仍待用户真机验收。

## 构建与安装

Release 构建成功（6 分 36 秒），轻量包已保留数据覆盖安装到 Samsung SM-X800（R52T800SKWZ）。设备实际 APK SHA256 与交付文件一致，设备回读版本为 1.7.1 / 20261075。未卸载、未清数据；未生成内置模型版；构建时尚未提交或发布，后续正式发布结果见下文。

- APK：`D:/CyIME-Data/deliveries/1.7.1/20261075/CyIME-1.7.1-arm64-v8a.apk`，80,908,559 字节；包名 `com.cyletix.cyime`，ARM64 Release，原发布签名。
- SHA256：`71da48087b5b227da6f4fb4711692d6bc4c5856d1834c0b4eee38cb7b363e3f7`。
- 累计源码：`C:/Users/Administrator/.codex/worktrees/hardware-input-state/CyIME`，基线提交 `3b3b5725ea74931cd9c1c3ba8ba00d25eeb0424d` 及累计未提交修改。
- 源码指纹：`419a89635490e5cc5f3203e3db269d3d006c1e9f3b8139e19e53ce97c6f33b22`，构建、安装和归档均一致；交付目录保存 950 个源码文件与 17 个子模块清单，以及源码状态、构建回执和安装状态。
- 过程日志：`.codex-artifacts/delivery-1.7.1-20261075/delivery.log`。Todo 仅移除本次两项补修的未安装提示，未勾选；实际输入及视觉效果仍未验收。

## GitHub 正式发布

用户已明确授权累计源码按功能提交、推送并公开发布。2026-10-04 10:08:37 UTC 正式发布 [CyIME 1.7.1](https://github.com/Cyletix/CyIME/releases/tag/1.7.1)，Release ID `402953090`，`draft=false`、`prerelease=false`，设为最新正式版。

- 累计修改拆为 16 个功能/交付提交，普通快进推送到 `main` 和 `codex/builtin-clipboard-sync`；未重写既有历史。
- 源码标签 `1.7.1` 指向 `8969e6d98b19f1b3f50c400353ab5d303ab4ba9e`，与 Release 的目标提交一致。
- 仅有一个安装包资产 `CyIME-1.7.1-arm64-v8a.apk`，资产 ID `609571040`；GitHub 返回的大小及 SHA256 与本地文件、已安装 APK 一致，未上传内置模型版。
- 提交没有改变已构建的源码内容：逐项核对归档的 950 个文件、17 个子模块及其修改文件；使用独立临时索引重放根目录补丁和 CMake 的 T9 补丁，确认完全还原构建时引擎源码。librime-predict 的工作文件与固定上游提交一致，其本地索引状态不影响源码内容；未修改该子模块。
- 构建回执保留当时的基线提交和源码指纹，没有伪造为提交后重新构建。归档目录新增 `publication-source-verification.json` 和 `github-release.json`，将实际 APK、构建快照与发布提交关联。
- 用户反馈当前比例“差不多了”并要求发布；这不代表此前所有功能逐项验收，不替用户勾选 Todo。
