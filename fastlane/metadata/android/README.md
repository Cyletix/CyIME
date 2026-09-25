# 商店元数据（fastlane）

`title.txt` / `short_description.txt` / `full_description.txt` 已统一为 CyIME（2026-09-25）。

上游的 `images/icon.png` 与 `images/phoneScreenshots/*.jpg` 已移除：Xime 的名称、Logo 与其他品牌资产不在 GPLv3 授权范围内（见 [TRADEMARKS.md](../../../TRADEMARKS.md)），改用 CyIME 自己的图后再放回：

- `images/icon.png`：应用图标（512×512 左右，F-Droid 元数据要求）
- `images/phoneScreenshots/0.jpg`、`1.jpg` …：手机截图，编号须从 0 开始连续

补齐前，商店元数据仅有文字描述，不影响仓库构建。
