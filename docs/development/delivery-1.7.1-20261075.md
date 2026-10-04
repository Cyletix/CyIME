# 1.7.1 / 20261075 轻量验证包（2026-10-04）

用户要求安装并交付安装包，同时指定本次显示版本升为 1.7.1；安装序号从已交付的 20261074 递增为 20261075。此前 1.7.0 为本地验证包，GitHub 最新正式版经交付前检查仍为 1.6.1。本次沿用累计源码和轻量包范围，默认语音继续使用 Paraformer＋SenseVoice。

## 修正范围

- [物理空格选词](hardware-input-state-machine-2026-10-04.md)：组词时确认正常候选，上屏后的智能联想仍只由数字/点击确认，空格照常输入；回车原始编码及方向键移动正文保留。
- [键区比例](live-resize-and-vertical-spacing-20261004.md)：取消额外上下留白和固定手机小键帽上限，首尾行贴近键区边缘，行距不随高度膨胀，默认尺寸和实际绘制共用度量；数字、符号底栏和候选侧栏同步。保留现有尺寸调节范围，极端拉高时键帽仍会增高。
- 包含此前 1.7.0 / 20261074 的所有累计修正，包括大写拖动、按住高亮、剪贴板、悬浮移动条及语音开关。

## 开发检查

物理按键相关 56 项 JVM 检查及布局相关 66 项 JVM 检查通过，零失败、错误、跳过，主应用和 Android 测试源码编译通过；证据分别在 `.codex-artifacts/hardware-space-20261004/` 和 `.codex-artifacts/edge-aligned-keys-20261004/`。没有运行完整尺寸门禁或设备界面测试，交互及视觉仍待用户真机验收。

## 构建与安装

Release 构建成功（6 分 36 秒），轻量包已保留数据覆盖安装到 Samsung SM-X800（R52T800SKWZ）。设备实际 APK SHA256 与交付文件一致，设备回读版本为 1.7.1 / 20261075。未卸载、未清数据；未生成内置模型版，未发布 GitHub Release，未提交 Git。

- APK：`D:/CyIME-Data/deliveries/1.7.1/20261075/CyIME-1.7.1-arm64-v8a.apk`，80,908,559 字节；包名 `com.cyletix.cyime`，ARM64 Release，原发布签名。
- SHA256：`71da48087b5b227da6f4fb4711692d6bc4c5856d1834c0b4eee38cb7b363e3f7`。
- 累计源码：`C:/Users/Administrator/.codex/worktrees/hardware-input-state/CyIME`，基线提交 `3b3b5725ea74931cd9c1c3ba8ba00d25eeb0424d` 及累计未提交修改。
- 源码指纹：`419a89635490e5cc5f3203e3db269d3d006c1e9f3b8139e19e53ce97c6f33b22`，构建、安装和归档均一致；交付目录保存 950 个源码文件与 17 个子模块清单，以及源码状态、构建回执和安装状态。
- 过程日志：`.codex-artifacts/delivery-1.7.1-20261075/delivery.log`。Todo 仅移除本次两项补修的未安装提示，未勾选；实际输入及视觉效果仍未验收。

## GitHub 发布准备

用户要求仅发布轻量版。已创建 Release 草稿（ID `402953090`，目标版本 `1.7.1`），仅上传 `CyIME-1.7.1-arm64-v8a.apk`；GitHub 返回的大小、SHA256 与本地交付及已安装 APK 一致。资产 ID `609571040`，状态 `uploaded`，未生成或上传内置模型版。

草稿：<https://github.com/Cyletix/CyIME/releases/tag/untagged-86f8e6e6fbcdbc24d44c>。发布说明保存于 `docs/1.7.1.md`。当前仍是草稿，尚未公开发布；累计源码未提交，现有发布入口要求已提交源码，需先取得 AGENTS.md 所要求的 Git 提交授权，再建立对应源码提交与版本标签。草稿当前 `target_commitish` 是构建基线 `3b3b5725ea74931cd9c1c3ba8ba00d25eeb0424d`，正式公开前必须改为包含累计源码的发布提交，不能把基线当成本次完整源码。
