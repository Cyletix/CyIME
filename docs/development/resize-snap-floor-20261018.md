# 调节尺寸：方形吸附被下限挡住、固定最低高度

2026-10-18。用户在手机 1.14.26 上反馈"特殊尺寸的吸附似乎完全失效了"，并要求"固定的最低尺寸太低了，应该高一些，让字母区高度和现在最大高度一致，也就是差一个工具栏的高度"。

## 吸附为什么不动

- 1.14.22→1.14.26 之间调节手势和吸附逻辑（`snapSquareKeys`、`snapFixedEdgeToCenter`、分体中缝吸附）没有改动，只有边框绘制换了样式。
- 用单元探针按手机几何（384dp 宽、2.75 密度）算：26 键方形按键点的面板总高约 202dp（含工具栏），而固定和悬浮的下限都是 228dp，方形点被挡在可拖范围之外，所以怎么拖都不会"咔"一下；九键的方形点约 385dp，在范围内。
- 所以不是算法坏了，是下限把 26 键的吸附点藏起来了（悬浮卡片差约 26dp，不是 1、2dp 的误差）。

## 修法

- `KeyboardSquareSnap.squareTotalHeightPx`：按当前宽度二分求方形点的总高度。
- `squareAwareFloatingMinHeightDp`：悬浮下限不能挡住方形点，方形点在默认下限之下时下限降到方形点下方 2dp，但不低于绝对下限 `FLOATING_RESIZE_ABSOLUTE_MIN_HEIGHT_DP`（160dp）；方形点在下限之上或没有方形点时下限不变。调节手势（`KeyboardResizeOverlay`）悬浮模式按此取下限。
- 已保存/拖出的悬浮高度按 `floatingStoredHeightBounds`（160dp 起）夹，默认高度仍按 `floatingResizeHeightBounds`（228dp 起）夹；`KeyboardHeightProfiles.floating/save` 和服务端 `resolveKeyboardSize` 都改用放宽范围，否则确认后会被夹回 228。
- 固定模式按用户要求：`keyboardHeightBounds` 的下限 = "屏高 45% 那档" − 工具栏 44dp（`KEYBOARD_TOOLBAR_HEIGHT_DP`），不低于 228dp。手机竖屏（915dp 高）上限 411dp，下限由 228 变为 367。横屏的上限是近乎整屏高（屏高减导航栏和拖条），**不能拿它推最低档**：1.14.27 第一版就是这么算的，平板横屏（625dp 高）最低被抬到 509dp，默认键盘撑满屏，整体回退；现在横屏也按 45% 档算，625dp 高的平板横屏最低 237dp、上限不变。固定模式 26 键的方形点（约 202dp）在竖屏下限之下，按用户的规则不可达，九键的 385dp 可达。
- 回归测试：`KeyboardResizeGeometryTest.floatingFloorNeverHidesTheSquareKeyDetentOnPhones`（方形点位置、放宽后的下限、从上方 4dp 拖顶边吸到方形点且不低于下限）和 `fixedMinimumIsOneToolbarBelowMaximumAndFloatingKeepsSharedFloor`。

## 验证

- `KeyboardResizeGeometryTest` 30 项通过。
- `:app:assembleDebug` 暂被别的线程未提交的 `SettingsMainScreen.kt` 编译错误挡住，模拟器复现截图待该文件修好后补；手机未装，未验收。
