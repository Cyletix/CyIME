# TODO 小项修复与平板批量检查

后续调整已随 20261042 安装平板：默认配色改为“黑白”（`pure_black`）。内置配置、配置缺失时的默认值及键盘／语音／设置初始状态统一；深色默认沿用当前选择，自定义深色配置仍优先。新安装使用黑白，旧主题选择和显示模式保留，不再自动将紫色或动态色迁移为柔和蓝；已移除 858 配色仍按原规则映射柔和蓝。8 项配色测试及应用／设备测试代码编译通过，平板升级保留原黑白主题；首次安装默认值未真机验收。日志：`.codex-artifacts/default-black-white-check.log`，最新交付见 [平板交付记录](keyboard-aspect-protection.md)。以下为此前交付历史。

2026-10-02。工作区 `D:/GitHub/CyIME`。开发及平板检查阶段未打包、安装；随后按用户“给手机装最新版”的授权生成 **1.4.0 / 20261039**，已覆盖安装至 SM-S9180，包含下列五项修复及此前 0 键左滑、候选栏滚动。平板仍为 **20261038**，不包含这些追加改动；没有提交或公开发布。

## 本轮源码改动（手机已安装，平板未安装；功能未验收）

| 项目 | 行为与实现边界 |
| --- | --- |
| 配色名称 | “纯黑”改为“黑白”；保留 `pure_black` 标识和配色，不丢旧选择。 |
| 浅色语言菜单 | 当前项使用 primary/onPrimary 背景文字对比，并显示对勾；行高、菜单宽度及触摸区域不变。 |
| 方向键动效 | 移除普通方向扇区禁用键帽缩放的分支；动效和炫光继续分别受原开关控制，扇区形状和触摸区域不变。 |
| 半屏边框吸附 | 固定键盘拖动左右边或角时，边缘距离屏幕中线 12dp 内吸附；保持对边、最小宽度和原始累计位移，继续细拖可以离开；浮动模式不变。 |
| 连续删除 | 长按与滑动共用手势处理，避免越过系统 touch slop 即取消长按；50dp 显示提示，80dp 停止重复并准备滑动动作，松手才清空或撤回；返回原位、取消均不补一次删除，松手停止排队重复，回调随重组更新。 |

相关文件：`KeyboardPalette.kt`、`LanguageKeyButton.kt`、`EditKeyboardLayout.kt`、`KeyboardResizeRect.kt`、`KeyboardResizeOverlay.kt`、`KeyButton.kt`、新增 `RepeatingKeyGesture.kt`。范围限于对应功能。

布局检查范围：边框吸附几何在密度 1、1.875、3 下覆盖左右边和四角、最小宽度、对边保持及脱离吸附；新增文字单行省略，菜单对勾给标题左右各预留 20dp。没有运行整套尺寸门禁；新菜单裁切、方向键视觉效果和新删除手势仍需安装后检查。

## 源码验证

相关 JVM 检查共 **55 项通过**：

| 测试类 | 数量 |
| --- | ---: |
| KeyboardResizeGeometryTest | 26 |
| KeyboardPaletteTest | 8 |
| T9CandidateNavigationTest | 6 |
| CandidateStripGeometryTest | 10 |
| RepeatInputTest | 2 |
| KeyGlowTimingTest | 3 |

应用及 Android 设备测试代码编译通过。新增设备测试覆盖长按前后轻微位移、清空松手触发、取消/返回原位、持续长按期间更新回调；**仅编译，未执行这些新设备用例**。日志：`.codex-artifacts/todo-batch-check.log`、`todo-repeat-check.log`、`todo-final-compile.log`。后一次 Gradle 测试覆盖了前一次默认 JUnit XML 目录，表中前 50 项依据首轮执行时读取的结果记录，不代表最终目录仍有全部 XML。

## ADB 批量检查：已安装的 20261038

设备：Samsung SM-X800，Android 14，序列号 R52T800SKWZ，横屏 2800×1752；包名 `com.cyletix.cyime`，上次更新时间 2026-10-02 11:46:45。交互限定 CyIME 自带测试框和设置页。

| 检查 | 实际结果 | 证据文件前缀 |
| --- | --- | --- |
| 动效、炫光开关 | 四种开关组合逐一读取并匹配，恢复原来的双开；未逐一验收渲染效果。 | effects-combination-0～3、effects-result.json |
| 自定义布局入口 | 独立页面可打开，显示 QWJRTK 编辑/复制/删除及新建入口；未修改布局。 | custom-layouts |
| 分类词库筛选 | 分类菜单可打开；医学筛选仅显示医学、中医中药两集；未下载或应用词库。 | dictionary-categories、dictionaries-medical |
| 布局面板 | “添加布局”入口和图标可见；从中文 26 键切到真实九键面板。 | cyime-mode-panel、t9-before |
| 九键下一候选 | 输入 64426 后首选“你好”，0 显示右箭头；点击后高亮“你敢”，正文仍空。 | t9-composing、t9-next |
| 空格确认 | 空格后测试框精确为“你敢”。 | t9-space-commit |
| 空闲数字 0 | 无待选内容时点击 0，测试框精确为“你敢0”。 | t9-idle-zero |
| 原地长按删除 | 持续按住 1100ms 后测试框为空；未验证本轮抗滑动改动。 | t9-hold-deleted |
| 手写开关 | 手写画板打开，再点关闭返回原九键，测试框仍空；未测试识别质量。 | handwriting-open、handwriting-return |

截图和页面树保存于 `D:/CyIME-Data/diagnostics/20261002-todo-batch/`。系统页面树不含完整 IME 节点，候选高亮、键帽和面板以截图核对，正文与设置开关读取页面树断言。

开始时检测到设备被切至 Gboard，排除该阶段作为 CyIME 功能证据；用户同意暂停两分钟后完成上述输入检查。结束已通知用户恢复操作，平板留在 CyIME 九键及空测试框。没有清除应用数据或覆盖用户词库。

本轮没有重新测高速输入性能、真实录音、首次设备分档、手写多字识别，也没有执行新代码的真机验收。下一小项是符号/数字的上滑与长按 0.3 秒输入选项；实体键盘入口、光标跟随及系统手写笔适配仍属后续任务。

## 手机覆盖安装：20261039

2026-10-02 13:15（日本时间），SM-S9180 / R5CW13EYCSF 从 1.3.20 / 20261033 更新为 1.4.0 / 20261039 普通 ARM64 Release 包；包体 470,837,585 字节，SHA-256 为 `9abbd0f3fd52da3681882c5c25197258301407c0f78743b17a65f7c04c21faf1`。手机实际 base.apk 哈希与交付一致；签名与原包一致，没有 TEST_ONLY 或 DEBUGGABLE 标记。

使用覆盖安装，未卸载、清数据或重置设置。安装前后应用数据 inode 与首次安装时间相同；默认输入法仍为 CyIME。MainActivity 启动返回成功，应用进程存活。这里只确认安装和启动，未重新进行本轮功能或性能验收；平板没有连接，未更新。

第一次构建使用 IDE ABI 注入参数，意外产生 testOnly 标记，被系统拒装，旧版保持不变；随后改用临时 Gradle ABI 配置重建并正常安装，未用 `-t` 绕过。最终日志：`.codex-artifacts/phone-20261039-release-build.log`、`phone-20261039-install-final.log`。Release 使用 2 GiB Java 堆、2 个工作线程；完整尺寸门禁未运行。

回执、安装前后包信息、旧 APK、最终签名及关键源码哈希位于 `D:/CyIME-Data/diagnostics/20261002-phone-update/`，最终回执为 `validation-20261039.json`。本次包来自含未提交改动的当前工作区，不以 HEAD 提交号代替完整源码范围。
