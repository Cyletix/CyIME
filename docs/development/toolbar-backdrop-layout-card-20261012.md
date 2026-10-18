# 工具栏圆底、语言管理布局卡片、实体键盘拼音光标（2026-10-12）

## 改动

- 语言管理：只有一两个布局（英文 26键、日语两种）时卡片按“一行三张”的宽度算，不再撑满整行（`profileCardWidths`）。
- 屏幕键盘工具栏：
  - 菜单、收起、取消输入等按钮（`KeyboardToolbarButton`）和中间工具图标（`ToolbarActionButton`）的圆底只在按下或开启时出现；玻璃模式下也不再常显。
  - 圆底高 32dp，在 44dp 的栏里上下各留 6dp；窄格缩成圆，左右至少留 4dp（`toolbarPillSize`）。
  - 格子最窄从 48dp 降到 36dp，悬浮小窗里工具栏不再横向溢出、压住面板边框。
- 实体键盘：左右键在拼音里移动光标时，拼音条用竖线标出光标位置，例如 `ni| hao`（`markPreeditCaret`）。光标回到末尾时不标；显示与编码不一一对应的方案（如双拼）也不标。

## 验证

- 单元测试：除其他会话未完成改动带来的 3 个失败（`RuntimePreferencesTest` 2 个、`ClipboardPastePredictionTest` 1 个）外全部通过；新增 `InputTextPolicyTest.preeditCaret…`。
- 模拟器 UI 测试通过：`SoleLayoutCardSizeTest`（新增，360/900dp）、`ToolbarSlotSizingTest`（新增 260dp 悬浮小窗）、`FloatingEdgeMarginTest`（新增，平板悬浮 400/900dp）、`ToolbarNavigationConsistencyTest`（收起按钮改为平时无底板）、`ToolbarUiTest`、`HardwareBarStylesTest`、`GlassToolbarActiveStateTest`、`GlassPanelsSurveyTest` 两种玻璃主题。
- 模拟器开实体键盘端到端：nihao 后按左键光标进拼音并标出竖线、下键翻页、Esc 清空、屏幕键盘遇实体按键自动收回。
- 截图：`.codex-artifacts/sole-layout-20261011/`、`.codex-artifacts/toolbar-backdrop-20261011/`。

## 已知

- Rime 的左右键按音节跳（nihao 按两下到最前），不是逐字母。
- 悬浮窗左边距（约 11dp）略大于键间距（约 6dp），未压边；用户截图里的贴边情况在当前构建的最小与较大悬浮尺寸上都没复现。
