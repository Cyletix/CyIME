# 符号页逐行加载与拆词空格显示（1.9.13）

1.9.12 平板交付后，用户反馈打开符号列表掉帧严重。本轮按兼容修复升为 1.9.13 / 20261105，同批加入 Todo 第 374 条“剪贴板拆词空格不显示为文字”。

## 符号页掉帧

`SymbolKeyboardLayout` 每个分类页用 `Column + verticalScroll` 一次组合全部符号。最大的几个分类有数百个符号：◓ 308、𝒲 302、ぁ 189、ɠ 178、⓵ 174。每个按键又带 `tolerantClick`、`keyGlow`（含材质绘制、`Animatable`、`pointerInput`）等 `composed` 修饰，打开或点分类时会在一帧内组合并测量全部按键；`animateScrollToPage` 跨页时，途经的每页也要整页组合。

改为与表情页相同的写法：按当前列数分行，用 `LazyColumn` 只组合可见行。分类、顺序、列数、行高、按键样式、最近使用记录与输出行为不变；英文分类仍走 `onSelectExact`。网格加 `symbol-grid:<分类ID>` 测试标签。

新增设备检查 `SymbolNavigationTest.largeCategoryComposesVisibleRowsAndStillReachesItsLastSymbol`：切到 308 个符号的分类后，已组合按键必须少于总数的 1/4，并且滚动后仍能显示最后一个符号。该检查只编译，尚未在设备运行。

## 拆词空格

`SplitWordsView` 原来把空白词块显示为文字“空格”。改为原样显示：普通空格换成不换行空格（避免被当作行尾空白而不计宽度），制表符显示为 4 个不换行空格，全角空格保持原样；含换行的空白块仍显示“换行”。上屏内容仍是原文，不受影响。新增 JVM `SplitPartLabelTest` 2 项。

源码中写入转义时，命令行工具把 `\u00A0`、`\t` 还原成了不可见字符；已改为 `Char(0x00A0)` 常量，并核对文件字节确认是转义文本。

## 检查

- 首次构建在沙箱中因 Gradle 客户端无法建立本机回环连接失败（`check-sandbox-loopback.log`）；改用交付入口相同的 `jdk.net.unixdomain.tmpdir` 设置后，Debug 与设备检查程序构建成功（`check.log`）。
- JVM：`SymbolGridGeometryTest` 4 项、`RecentUsageStoreTest` 5 项、`SplitPartLabelTest` 2 项通过（`jvm.log`、`jvm-split.log`）。
- 平板与手机均未连接 ADB，本轮没有设备检查、没有截图、没有帧耗时数据。符号页是否已不卡，需安装后用 `dumpsys gfxinfo` 对比 1.9.12 基线并由用户验收。

证据目录：`.codex-artifacts/symbol-lazy-20261008/`。

## 交付

主目录 `auto.cmd --build-only` 构建成功（7分13秒，`release-build.log`），入口完成签名、版本与源码核对。

- APK：`app/build/outputs/apk/editions/1.9.13/standard/release/CyIME-1.9.13-arm64-v8a.apk`，111153433字节。
- SHA256：`eaa80972ce2817d2070f952026734ed148d188a9c19f2c99cd09e4abe04bab2d`。
- 构建前后 ADB 均无设备，**未安装**。连接后用 `auto.cmd`（构建并安装，不用 `--install-only`）覆盖安装，核对版本与设备 APK，再做符号页帧耗时对比。
- 构建期间另更新了设备检查 `MenuSettingsShortcutTest`（菜单已无“主题与定制”，“输入选项”改名“方案选项”），只改测试，不影响 APK；设备检查程序重新编译成功（`check-2.log`），未在设备运行。
- 未公开发布、未打内置模型包、未提交 Git。

## 同轮其他事项

- Todo 全量核对见 [todo-audit-20261008.md](todo-audit-20261008.md)。
- Todo 已被用户移到 `D:/OneDrive/Notebook/Project/CyletixKeyboard/CyIME输入法 todo.md`，AGENTS.md 中的路径已更新。
- 按用户要求清理 14 个 Codex 隔离工作目录及失效的 `CyIME-release-1320` 记录，只保留主目录；删除前存档到 `D:/CyIME-Data/worktree-archive-20261008/`。删除已全部合入 main 的 10 个本地分支；保留有未合入提交的 `codex/frosted-glass`（2 个，10-03 玻璃材质初版）和 `backup-before-cleanup`（13 个，09-25 备份）。
