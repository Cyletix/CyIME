# iOS 渐变玻璃找回早期键色（2026-10-30）

用户对比早期截图（九键、深色、玻璃）与手机 1.14.144：现在的按键是一块块实心灰键，功能键反而比字母键暗。

## 原因

- 早期 iOS 渐变沿用黑白主题深色键：字母键 #1C1C1C、功能键 #353535。玻璃按 1/不透明度提亮键色（默认 20%）：字母键成 #8C8C8C@20%、功能键成白@20%，叠在模糊宿主上是淡灰半透明，功能键亮一档。
- 10-09 主题审计（`split-themes-quick-actions-20261009.md`）为了不开玻璃时按键和深色渐变底分得清，把深色字母键改成 #55555A、功能键 #3A3A3C。玻璃下 #55555A 被提到接近白，成了实心灰块。
- 10-12（`width-icon-punctuation-glass-keys-20261012.md`）玻璃下功能键材质透明度减半，原意是避免功能键与字母键提亮后一样；iOS 早期键色本来就分得开，减半后功能键反而更暗。

## 改法

只在开玻璃且按键不完全不透明时，`TransparentGlassTheme.glassKeyBase` 把 iOS 深色键换回 #1C1C1C / #353535 再提亮（按 1/不透明度，不走黑白主题近黑键的 3 倍），iOS 功能键不再减半。不开玻璃仍用 #55555A / #3A3A3C；浅色、其他主题不变。

## 检查

- 出图 `GlassEraRenderTest`（设 `CYIME_RENDER_OUT`、`CYIME_RENDER_STAGE`）：iOS 渐变、黑白、柔和蓝 × 九键/26 键/数字 × 深浅色 × 深色聊天页/浅色网页宿主，改前改后各 36 张，图在 `D:/CyIME-Data/eval/glass-era-20261030/`。逐像素比较：只有 iOS 深色 12 张变化，其余相同（柔和蓝一张差 1 级舍入）。
- 改后九键深色取色与用户早期截图对上：字母键 56 / 早期 55–57，左栏 (50,56,56) / 早期 (50,56,56)，功能键 72–78 / 早期 74–83。
- 单元测试：`FrostedKeyColorsTest`（新增 iOS 玻璃键色）、`GlassLegibilityTest`、`ShiftKeyColorsTest`、`FrostedTextSurfaceTest`、`FrostedGlassPreferencesTest`、`ui.theme.*` 共 71 项通过。
- 未打包、未装机，真机未验收。
