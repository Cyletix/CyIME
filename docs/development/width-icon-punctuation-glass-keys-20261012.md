# 全角图标、英文标点、玻璃功能键、语言菜单（2026-10-12）

## 改动

- 全角/半角图标改成与桌面版（CyIME-Windows `InputToolbar.cs`）一样的描边路径：半角是月牙，全角是满圆，随开关切换（`keyboard/WidthShapeIcons.kt`，`ToolbarButtonIcon(active=)`）。
- 英文输入时，空格两侧的逗号和句号都开着，两个键只输入 `,` 和 `.`：不显示角标，也没有滑动副输入（`spacePunctuationKeys(english=)`）。
- 玻璃模式下，功能键（Shift、删除/清空、!@#、123、语言、回车等主题功能键色的键）的材质透明度是字母键的一半（`glassFunctionKeyScale`）。原先玻璃把键帽色统一提亮，深色主题里功能键和字母键一模一样。
- 长按语言键的菜单底色取键盘自己的功能键色与字母键色的中间色，不透明，文字用主题文字色。对比度不足 4.5:1 时退回 Material 的 surface/onSurface。选中行向强调色偏一点，并加强调色细边。

## 验证

- 单元测试：除已知 3 个（`RuntimePreferencesTest` 2 个、`ClipboardPastePredictionTest` 1 个，来自 10-06/10-08 的未提交改动）外全部通过；新增英文标点用例。
- 模拟器：`GlassPanelsSurveyTest`（10 种主题模式）、`ToolbarUiTest`、`GlassToolbarActiveStateTest` 通过；新增 `LanguageMenuThemesTest`（4 主题 × 深浅 × 玻璃开关，16 张截图）、`WidthShapeIconTest` 通过；`LanguageKeyButtonTest` 的玻璃不透明用例按新菜单色更新后通过，其余 8 个是早已存在的失败。
- 截图：`.codex-artifacts/glass-keys-menu-20261012/`（`sheet-main.jpg`、`sheet-menu.jpg`、`width-icons.png`）。
