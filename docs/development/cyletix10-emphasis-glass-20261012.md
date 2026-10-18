# Cyletix10 黑键与按键编辑（2026-10-12）

## 原因

- 黑键：Cyletix10 预设默认开启“强调改位字母”，改位的 11 个键（d f j k y / e t l i o u）底色混入强调色，文字按这个底色取“可读色”，变成深色。玻璃材质按“文字比底亮还是暗”决定把键帽提亮还是压暗：深字 → 键帽压成近黑，深字落在黑键上。
- 改按键存不了：校验要求 Cyletix10 的排列与预设一字不差，任何交换都让“保存方案”变灰且没有提示。

## 改动

- `KeyButton.kt` / `CustomLayoutColors.kt`：玻璃下键帽材质按主题原文字色决定方向；强调文字按玻璃键面的代表色（`customLayoutGlassSurface`）取 ≥4.5 的对比。非玻璃不变。
- `CustomKeyboardLayouts.kt`：Cyletix10 按自定义布局的规则校验，可以交换、合并、拆分后保存。

## 验证

- 单元测试：新增 `cyletix10KeysCanBeRearrangedAndSaved`、`glassEmphasisKeepsLabelsReadableOnTheGlassSurface`；除已知 3 个外全部通过。
- 模拟器 `LayoutLegibilitySurveyTest`：7 个布局（26键、九键、14键、Cyletix10、假名、笔画、五笔）× 4 主题 × 深浅 × 玻璃开关，共 112 张截图逐张查看，所有键文字可读，Cyletix10 改位字母只以强调色文字和浅色调键底区分。截图在 `.codex-artifacts/layout-legibility-20261012/`。
