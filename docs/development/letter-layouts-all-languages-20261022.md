# 字母布局三种语言共用，新增 Colemak（2026-10-22）

用户：Cyletix10 只能在中文里用，英文和日文没有；Colemak 也没加进来。

## 规则

- 中文里启用的字母布局（QWJRTK、Cyletix10、Colemak、用户自建布局）在英文直接输入和日文罗马字的布局选择里同样出现。选择器、缩略图、键盘组件、布局名称都是同一份 `CustomKeyboardLayout`，不复制、不改名。
- 中文的布局仍是各自的拼音方案（`pinyin_cyletix10` 等）；英文和日文只多存一个“该语言用哪种字母排列”（偏好 `letter_layout_en` / `letter_layout_ja`，`settings/LetterLayouts.kt`）。选了只换按键排列，语言、方案、引擎不变：英文仍是直接输入，日文仍走 `japanese` 罗马字方案。
- 判断入口只有一个：`KeysConfigHelper.letterLayout(schemaId, ascii)` 读当前组合（`InputProfiles.current`，已套上该语言的排列）的布局。键盘行、键宽、分体、候选几何、改位字母强调都从这里取，原来五处“英文就不用自定义布局”的判断已收掉。
- 上滑数字和符号按物理位置保留（与中文自定义布局同一个 `gestures()`）：日文第三行第 7 个键始终是“ー”，Colemak 把 i 放在 QWERTY 的 l 位上，i 的上滑就是原来 l 位的“-”。
- Colemak：`qwfpgjluy;` / `arstdhneio` / `zxcvbkm`，`,./` 留在底排，和 Cyletix10 同样的 10/10/7。构建时与 QWJRTK、Cyletix10 一起由 rime_ice 生成 `pinyin_colemak.schema.yaml`；内置排列集中在 `settings/BuiltinLetterLayouts.kt`，加一个就三种语言都有。默认不启用，和 Cyletix10 一样在“方案资源”里打开。

## 不能映射的

- 合并键布局（14 键、带双字母键的自建布局）不提供给英文和日文：直接输入一个键只能出一个字母，罗马字也没有拆合并键的解码。选过的布局后来被改成合并键或被删除时，自动回到该语言自己的 26 键。
- 假名九键不是拉丁布局，不受影响。
- Cyletix10、Colemak 的“;”键在日文里输入的就是“;”（日文方案标点表没有它），不是假名。

## 验证

- 单元测试：新增 `LetterLayoutsTest` 5 项、`SchemaForLayoutTest` 1 项；`testDebugUnitTest` 全量 2070 项，0 失败，24 跳过。`compileDebugAndroidTestKotlin` 通过（`ChineseSchemasTest`、`ChineseDefaultsImeTest` 改为按内置排列集合检查）。
- 截图（Robolectric 原生绘制，`LetterLayoutRenderTest`，设 `CYIME_RENDER_OUT` 才出图）：Cyletix10、Colemak × 中文/英文/日文罗马字 × 透明玻璃、黑白、薰衣草紫、柔和蓝 × 不透明/玻璃 × 手机 400dp / 平板 900dp，共 96 张，另有英文、日文的布局选择器 4 张，逐张看过：按键排列与中文一致，改位字母强调、上滑提示位置一致，文字可读。图在 `.codex-artifacts/letter-layouts-20261022/`。
- 未打包、未安装、未真机验收。
