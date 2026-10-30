# 最小高度下图标键文字压图标、最小高度 228 → 244dp

2026-10-30。用户手机截图：九键调到最低高度时，右栏「清空」「重输」的文字和图标重叠一部分，建议挤的时候不显示文字；用户回复最低高度设得偏低、文字也没适配。

## 原因

- 图标键（`SwipeableIconKeyButton` 的上滑提示、九键和笔画的 `ResetKey`）把提示文字放在键中心上方固定距离（`KeyboardKeyMetrics.hintOffsetDp`，默认 14dp），键变矮时这个距离被夹小，图标大小却不变，文字就落到图标上。
- 228dp 面板：工具栏 44dp，每行 44dp，键面约 39dp。退格图标 20dp 的字形上沿在中心上方 7.5dp，「清空」10sp 字形下沿只在中心上方 6.5dp，重叠约 1dp，和截图一致。
- 悬浮为了够到方形按键吸附点可以低到 160dp，这时空格键上的方案名「拼音」被压成看不清的小点。

## 修法

- 新增 `KeyboardKeyMetrics.hintClearsIcon`：按图标字形约占图标框 75%、汉字字形约等于字号估算，文字字形下沿与图标字形上沿至少留 1dp，否则只画图标、不画文字。三处图标键都用它（含系统字号放大）：`SwipeableIconKeyButton`（26/14/九键/数字/符号/候选页的退格「清空」等）、九键 `ResetKey`、笔画 `ResetKey`（原先写死 48dp 阈值）。上滑等手势不变。
- `hintOffsetDp` 在矮键上贴顶的留白由 2dp 改 1dp（行高上方本身还有约 1dp 留白），默认高度的键不受影响（仍是 14dp）。
- 空格键方案名缩到 8sp 以下时不画，只留空格图标。
- 最小高度 `FLOATING_RESIZE_MIN_HEIGHT_DP` 228 → 244dp：44dp 工具栏 + 四行各 48dp（Material 最小触控高度）+ 8dp 边距，键面约 43dp，「清空」「重输」都放得下。固定与悬浮仍共用这个下限，不随屏幕变化（10-19 用户要求保留的规则）；悬浮方形吸附的 160dp 绝对下限不变，那里文字按上面的规则隐去。按键间距基准 `MINIMUM_KEY_BODY_HEIGHT_DP` 固定在原来的 184dp，所以其它高度下的键距不变。
- 试过 248dp（文字和图标间距更宽），但小手机横屏默认键盘被抬到 248 后剪贴板搜索结果区只剩 44dp（`ClipboardPanelMatrixTest` 要求 ≥48），退回 244。
- 已存的固定高度低于 244 时读取时夹到 244（`KeyboardHeightProfiles.fixed` 的 coerceIn），不会被重置成默认高度。

## 检查

- 出图 `IconHintFitRenderTest`（设 `CYIME_RENDER_OUT`、`CYIME_RENDER_STAGE`、`CYIME_RENDER_HEIGHTS`）：九键/26/14/笔画/数字/常用符号 × 手机、平板 × 竖横屏 × 228、160（改后另加 244）× 实色/玻璃 × 字号 1.0/1.3，另有全部 10 个主题 × 深浅色 × 实色/玻璃（九键、26 键）。改前 272 张、改后 368 张，在 `D:/CyIME-Data/eval/icon-hint-fit-20261030/`（before、after4，对比拼图 sheet-*.png、zoom-244.png、themes-rail-244.png）。
  - 改前 228dp：九键、26、14、数字的「清空」和九键「重输」压在图标上；160dp 更严重，空格「拼音」成小点。
  - 改后 228/160dp：只剩图标，无重叠；244dp：「清空」「重输」都显示，和图标之间有间隙；字号 1.3 时 244dp 也只留图标。平板横屏九键本来是紧凑模式不显示文字，前后一致。
  - 常用符号页改前一轮用的是 Symbol 状态，Robolectric 下画不出来，改成 CommonSymbol 后只有改后图。
- 单元测试：`KeyboardResponsiveSizingTest.iconHintDropsInsteadOfOverlappingTheIcon`（新增）、`KeyboardResizeGeometryTest`（下限改 244）、`ui.keyboard.*` 与 `settings.*Height*` 共 431 项通过。
- 未打包、未装机，真机未验收。
