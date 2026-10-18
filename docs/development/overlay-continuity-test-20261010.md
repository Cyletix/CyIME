# 覆盖层连续性测试预期更新（2026-10-10）

`KeyboardOverlayContinuityTest` 9 项中有 3 项失败（[10-09 记录](split-themes-quick-actions-20261009.md)），原因都是测试预期没跟上已经确认过的产品行为。本次只改测试，没有改产品代码。

- `clipboardKeepsOriginalToolbarSizeAndKeyboardGeometry`：剪贴板分类和操作行实测 36dp，测试仍要求 24dp。按 [输入一致性](input-consistency-20261006.md) 的约定，剪贴板与表情共用 32dp 分类项加上下各 2dp 留白，总高 36dp，所以预期改为 36dp。
- `symbolsKeepTheNumberUnderlayUntilClosingAndThenReturnToText`、`leavingSymbolsForAnotherToolStillReturnsToTextWhenTheOverlaySessionCloses`：1.9.14 起符号页按 [符号页直接换页](symbol-swap-glass-clipboard-20261008.md) 改为 `snap()`，打开后 80ms 内已经盖住数字页，因此“0”键区域的 7173 个像素都变了。原来断言“揭幕期间数字页仍可见”，现在第一项改为断言符号页立即出现，第二项去掉这条像素断言。数字页仍挂在下面（`number-zero` 位置不变）、不切到中文模式、关闭后回到文字键盘，这些断言都保留。

验证：本机 x86_64 模拟器 `emulator-5560`（`D:/CyIME-Data/ui-check-avd`），用 1.12.0 debug 包和 `-PuiRenderingOnly=true` 构建的测试包，通过 `adb -s emulator-5560 shell am instrument` 运行，结果 9 项全部通过。没有用 Gradle connected 任务，也没碰真机。

构建备注：这次 Gradle 一开始报 `Unable to establish loopback connection`（`PipeImpl` 连 Unix 域套接字失败）。设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\CyIME-Data\uds` 后可以正常构建。
