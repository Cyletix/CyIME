# 模拟器手势回归两项失败的处理（2026-10-09）

1.11.0–1.11.1 在本机 x86_64 模拟器（`emulator-5560`）上跑既有手势回归 16 项，2 项失败，见 [同日开发记录](split-themes-quick-actions-20261009.md)，日志 `.codex-artifacts/glide-flick-20261009/emulator-regression.log`。本次只判断原因并改这两项测试的预期，没有改产品代码。

## MergedKeyboardGestureTest#leftRightLettersRespectToggleDistancePreviewAndCancellation

失败：第 139 行期望 `[w, q, q, w]`，实际 `[w, q, qw, w]`。

结论：测试预期过时，产品行为正确。

- 触发距离调到 48 dp 后，横拖 30 dp 没过阈值，`detectExclusiveKeyGestures` 按点按处理（横向位移小于 60 dp 取消距离）。
- 14 键启用时，点按 qw 经 `MergedLetterInput.tapValue` 送出合并键 `qw`，服务层按合并键交给 Rime；只有左右滑出的 `q`、`w` 才是确定字母。这是“点按保留双字母歧义”的现行约定，`MergedLetterInputTest` 按此断言。测试写于该约定之前，仍按点按出首字母断言。
- 修改：第 3 项预期由 `q` 改为 `qw`，末尾累计列表同步改为 `[w, q, qw, w, symbol:1]`。关闭左右滑选后，70 dp 横拖超过取消距离不提交，下滑出 `1`。这两步原先因第 139 行先失败从未执行，本次结论来自代码推断，需重跑确认。

## KeyboardSwipeIntegrationTest#actualLongPressPopupKeepsTheOriginalTouchStreamAndCommitsUppercaseExactlyOnce

失败：选中大写 Q 后提交正确（只有一次 `literal:Q`），但 `key-choice-popup` 仍在语义树中。

结论：测试时钟造成，不是产品缺陷（代码分析，待模拟器复核）。

- 松手路径：抬起事件进入 `detectExclusiveKeyGestures` 的长按分支，提交选中项后 `break`；`finally` 调用 `onPreview(SwipeState())`，经 `processSwipeState` 到 `SwipeBubbleController.update`，长按状态被同步清成 `SwipeState()`。长按气泡不走按压气泡的 60 ms 滞留。下一次重组时 `rememberSwipeBubbleDrawData` 返回 null，`SwipeBubbleOverlay` 不再组合 Popup。
- 测试为精确控制 400 ms 长按，设了 `mainClock.autoAdvance = false`。按 Compose `MainTestClock` 的约定，此时等待空闲不推进时钟、不出帧，待执行的重组也不算未空闲，所以断言时看到的仍是松手前那一帧的 Popup。提交在指针回调里同步执行，因此出现“已提交但 Popup 仍在”。
- 真机每个 vsync 都会出帧，松手后的下一帧即移除 Popup。同一手势一直由按下时的键盘窗口接收，Popup 是独立的不可聚焦窗口，不会截走这次手势。
- 修改：断言前加 `rule.mainClock.advanceTimeByFrame()`，只推进一帧。如果松手没有清状态，再多帧 Popup 也不会消失，所以这项断言仍能抓住真缺陷。

## 验证状态

按用户选择只交补丁：本次未构建、未在模拟器运行，两项修改 **未验证**。复核时只用 `emulator-5560`，不跑 `connectedAndroidTest`，不碰平板 R52T800SKWZ：

1. `gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PuiRenderingOnly=true`
2. `adb -s emulator-5560 install -r -t` 依次安装本次生成的 `CyIME-*-debug-x86_64.apk`（`app/build/outputs/apk/archive/<时间戳>/debug/`）和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
3. `adb -s emulator-5560 shell am instrument -w -r -e class com.kingzcheung.xime.ui.MergedKeyboardGestureTest,com.kingzcheung.xime.ui.KeyboardSwipeIntegrationTest com.cyletix.cyime.test/com.kingzcheung.xime.ui.UiRenderingTestRunner`

未改产品代码，未打包、未安装、未提交。
