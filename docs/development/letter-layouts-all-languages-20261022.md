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

## Typewise 蜂窝布局（同日追加）

- 字母：`wetyio` / `qsrgukp` / `adfhjl` / `zxcvbnm`（6/7/6/7），照 Typewise 原图。短行错开半个键，Shift 和删除放在第三行两侧各半个键宽；底排沿用 CyIME 自己的 !@#、123、标点、空格、地球、回车。和其他内置排列一样进 `BuiltinLetterLayouts`，中文生成 `pinyin_typewise` 方案，英文和日文罗马字随中文启用后可选。
- 键盘：四行字母仍走原来的行组件（`KeyboardRowWithConfig`、第三行的 Shift／删除），只在 `isHoneycomb` 时改成 7 列、半键错位，并多出第四行字母；不分体。
- 键帽：字母键画成尖顶六边形（`HoneycombKeys.kt`），比所在格高三分之一、上下各伸进相邻行的缺口，所以六边形能咬合；触摸区仍是各自的格子（砖格），不重叠。键面文字收进六边形内；六边形字母键不画投影。
- Shift 和删除（用户追加）：画成以键盘左右边缘为中线的半个六边形，正好补上第三行两侧的半键缺口并与相邻字母咬合；图标收在中间一半高度里，删除键不再显示“清空”角标（上滑清空照旧）。底排不改。
- 上滑符号：蜂窝布局和 QWERTY 没有位置对应，符号跟着字母走（q 上滑仍是 1），不按位置换。改位字母强调对蜂窝布局不适用（没有可比较的原位置）。
- 截图：中文/英文/日文罗马字 × 4 主题 × 不透明/玻璃 × 手机/平板 48 张，加选择器 4 张，在 `.codex-artifacts/typewise-20261022/`，逐张看过。平板上格子更宽，六边形偏扁。

## Cyletix10 换成不动符号的排列（2026-10-23，用户同意替换）

- 原研究版 `qwdrf;jkyp / asetghliou / zxcvbnm` 把 `;` 放在首行 y 位；手机上首行要 10 个字母键，用户自己把 u 挪到 p 后面。现在内置 Cyletix10 改为 `qwbrtydfpu / aseoghjin / zxckvlm`：符号全部留在 QWERTY 原键，字母只在 26 个字母格里换，10/9/7 三行，名字不变。
- 来源：Cyletix-Keyboard `research/nosymbol-20261023`，用户自己的聊天按键流，在“同指连击、手指停在原处移动、回主行移动”三项相对 QWERTY 的比值里压最差一项，最多改 12 个字母、小指负荷不超过原 Cyletix10。比原研究版和用户手机修法在三项和小指上都更好，手机双拇指模型与 QWERTY 打平。数字都是模型结果，不是实打速度。
- 用户在手机上保存过的 Cyletix10 改版（`custom-keyboard-layouts.json` 里同 ID 的条目）仍优先于内置排列，不被覆盖。
- 验证：`testDebugUnitTest` 2085 项 0 失败；`LayoutGeometryGateTest` 的 Cyletix10 用例改为点首行末键 u（原先点首行的 `;`）。`compileDebugAndroidTestKotlin` 本轮失败在 `PluginSettingsNavigationTest`（另一线程的插件设置改动，与本改动无关），该用例未运行。截图：中英日 × 4 主题 × 玻璃开关 × 手机/平板 48 张，`.codex-artifacts/cyletix10-nosymbol-20261023/`，逐张看过。未打包、未安装、未真机验收。
