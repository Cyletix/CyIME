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
- 固定模式按用户要求：`keyboardHeightBounds` 的下限 = 上限 − 工具栏 44dp（`KEYBOARD_TOOLBAR_HEIGHT_DP`），不低于 228dp。手机竖屏（915dp 高）上限 411dp，下限由 228 变为 367；横屏上限随屏幕，下限同样减 44。固定模式 26 键的方形点（约 202dp）在这个下限之下，按用户的规则不可达，九键的 385dp 可达。
- 回归测试：`KeyboardResizeGeometryTest.floatingFloorNeverHidesTheSquareKeyDetentOnPhones`（方形点位置、放宽后的下限、从上方 4dp 拖顶边吸到方形点且不低于下限）和 `fixedMinimumIsOneToolbarBelowMaximumAndFloatingKeepsSharedFloor`。

## 验证

- `KeyboardResizeGeometryTest` 30 项通过；`:app:assembleDebug` 通过。
- 模拟器 emulator-5560 按 393×698dp（`wm size 1080x2340`、`density 440`，接近手机）装新 debug 包复现：
  - 悬浮、26 键、卡片拉到全宽（360dp）：顶边一路往下拖，卡片停在 218dp（含拖条），正是该宽度的方形点；松手离方形点 7dp 时留在 225dp 不动，离 4dp 时吸回 218dp，吸附带 6dp 的规则没变。
  - 固定模式：顶边往下拖，停在 273dp（该屏上限 314dp 减 44dp），原来可以拖到 228dp。
  - 手机未装，用户未验收。
