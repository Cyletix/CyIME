# 全面屏手势下键盘底部留白（2026-10-31）

## 现象

手机（三星，手势导航，手势提示关闭）键盘最下一排下面空出约 50 dp 的深色条，比手势区高得多。截图里条内没有手势条，也没有「隐藏键盘」按钮。

## 原因

`ImeWindowInsets.extractBottomInset` 取 `navigationBars`、`mandatorySystemGestures`、`tappableElement` 的最大值。手势导航时系统给输入法窗口的 `navigationBars` 是整条导航按钮框（AOSP `navigation_bar_frame_height`，48 dp），为系统在键盘下面画返回/切换输入法按钮预留；按钮不画时这 48 dp 整条空着。截图量得：最下一排按键底边到屏幕底约 165 px（密度 3），即 48 dp 加卡片底边距。

## 规则

`imeBottomInsetPx`：

- 三键/两键导航（`Settings.Secure navigation_mode` 为 0/1；读不到时底部 `tappableElement` > 0）：整条保留，不变。
- 手势导航（`navigation_mode` 为 2；读不到时 `tappableElement` 底部为 0）且键盘下面没有系统按钮：只让出手势条，取系统 `navigation_bar_height`（手势模式下即手势条高度），上限 24 dp；三星「手势提示」关闭（`Settings.Global navigation_bar_gesture_hint` = 0）时上限 12 dp，避开屏幕圆角。
- 以下情况视为键盘下面有系统按钮，整条保留：本窗口里有系统画的 `android.inputmethodservice.NavigationBarFrame`（Pixel 等）；三星手势提示开着且 `navigation_bar_button_to_hide_keyboard` = 1；`smallestScreenWidthDp >= 600`（平板任务栏，手势模式下未实测前不缩）。

`tryGetVisibleNavBarHeightDp` 用同一规则，悬浮键盘最低位置、物理键盘浮条、收起的语音浮条、剪贴板展开和固定/悬浮切换都从这两处取值，跟着一起变。导航条背景仍由 `keyboardBackgroundBelowCard` 接着卡片铺，高度随之变矮。

## 检查

- 平板 SM-X800（Android 14，三键导航，1.15.14）adb 实测：`navigation_mode=0`，nav = tappable = mandatory = 90 px，日志 `pick=90`。新规则下仍为 90，不变。
- 手机手势模式读数未取到（手机不在 adb 上）。装机后看 `adb logcat -s ImeWindowInsets` 里 `gesture nav:` 一行，核对 `full`、`mode`、`hint`、`pick`。
- 云端只用 kotlinc 跑了 `imeBottomInsetPx` 的判定用例；完整编译、`ImeBottomSpaceTest`、渲染与真机均未做（未验收）。
