# 中文九键拂动与日文假名键统一（2026-10-16）

## 排查方法

新增 `FlickComboSurveyTest`（只做截图与记录，不断言），在模拟器 `emulator-5560`（`wm size 1920x1600`、`density 240`）上用真实 `KeyboardView` 跑 20 组：中文九键（拂动关 / 拂动开 / 拂动开 + 方向圆盘）、日文假名键（圆盘关 / 开）× 手机固定、手机悬浮、平板固定、平板悬浮。每组按住 JKL 或な，依次截图：静止、按住、左拂一半、左拂到位、转向上、下拂、长按，并记录左拂 30dp、先左后斜上、长按的实际输出。截图与记录：`.codex-artifacts/flick-combo-20261016/`。

## 现在 / 应该

| 项目 | 改前 | 应该（本次实现） |
|---|---|---|
| 两套实现 | 中文九键拂动走 `SwipeableKeyButton` + `ExclusiveKeyGesture`；日文假名键 `KanaFlickButton` 自带一套手势循环 | 两者共用 `ExclusiveKeyGesture` 的四向拂动模式（`freeDirection`） |
| 触发距离 | 中文用“滑动触发距离”设置（默认 24dp）；日文写死 18dp | 都用“滑动触发距离”设置 |
| 中心判定 | 日文按键宽 30%–70% 当中心：平板固定键 252dp 宽，左拂 30dp 仍出な（实测） | 按手指移动距离判中心；回到按键正中（触摸容差内）也算中心，保留“偏一点按下、拂出去再回中心出あ段”的原有规则 |
| 换方向 | 中文锁轴：先左再斜上仍出 J；日文自由换向：出ぬ（实测不一致） | 都按松手时的主方向决定，可以中途改方向 |
| 换方向时的字 | 日文直接跳变（用户 10.11 反馈） | 旧方向的字缩回原侧，新方向的字从自己一侧长出（120ms），松手立即清空，与 26 键一致 |
| 按下样式 | 日文整键变灰；中文是主题色浅底 + 细边 | 都用主题色按住高亮 |
| 长按数字浮窗 | 九键拂动模式长按出“5”时是三格宽的长胶囊 | 单项长按浮窗为圆形（多项菜单不变） |
| 悬浮窗 | 手机 / 平板悬浮下键面、渐变、圆盘位置与固定模式一致，没有发现悬浮专属错误 | 不变 |

## 改动

- `ExclusiveKeyGesture.kt`：`KeyGestureActions.freeDirection`。开启时方向取当前主轴，不锁轴；松手在阈值内或回到按键正中为点按，阈值外且该方向无字符则什么都不输入；离开中心后整次触摸归这个键，不让给整键盘光标。26 键、14 键不开启，行为不变。
- `KeyButton.kt`：九键拂动分支开启 `freeDirection`；键内拂动字改用 `rememberKeyFlickFade` / `KeyFlickLabels`。
- `KeyFlickPreview.kt`：新增按方向的键内字渐变；手指所在方向直接跟手，离开的方向 120ms 缩回，手势结束全部立即清空。
- `JapaneseKanaKeyboardLayout.kt`：`KanaFlickButton` 改用共用手势和共用键内字渐变、主题按住高亮；上下两个方向的字仍从上下边中点出现。假名方向提示、方向圆盘、无障碍操作保留。
- `SwipeBubble.kt`：只有一项的长按浮窗按单字圆形计算宽度。

## 変換键改为無変換（用户 10.16 追加）

原来 変換 键四个方向都是 ↶ 撤回。现在只保留一个“無変換”角标在右上，和九键删除键的“清空”一样跟随角标滑动方向设置（默认下拉触发，开启“反向”后上滑触发），左右滑不再撤回。点按仍是 変換。数字模式下的运算符键仍是四向拂动。

## 验证（模拟器，`-PuiRenderingOnly=true` 调试包）

- 组合复测（改后截图 `after/`）：日文平板宽键左拂 30dp 由“な”变为“に”；先左后斜上，中文出 K、日文出ぬ，二者一致；九键拂动长按出圆形“5”浮窗；日文按下改为主题色高亮，换方向时旧字缩回、新字长出。悬浮与固定一致。
- `JapaneseKanaGestureTest` 12、`GlideAndFlickGestureTest` 6、`FlickDirectionDiscTest` 1、`SwipeableKeyButtonGestureTest` 23、`KeyFlickEmphasisTest` 1、`FlickTransitionFramesTest` 1、`MergedKeyboardGestureTest` 3、`KanaDirectionHintsSettingsTest` 1、`JapaneseLayoutActionsTest` 3、`FlickComboSurveyTest` 5 通过。
- `KanaPreviewLayoutTest.standardKeyboardPopupStaysAboveThePressedKey` 失败：标点键开启假名方向提示时“、”与“？”字框重叠 1px。该绘制代码本次未改，推断为此模拟器 1.5 倍密度下的既有问题，未处理。
- 测试改动：`JapaneseLayoutActionsTest`、`JapaneseImeWorkflowTest` 的無変換改为下拉；`KanaPreviewLayoutTest` 不再要求 変換 键弹圆盘。`JapaneseImeWorkflowTest` 需要真实 IME，未运行。
- JVM：`KeyFlickPreviewTest`、`JapaneseKanaInputTest`、`SwipeBubbleControllerTest`、`SwipeBubblePlacementTest`、`SymbolInputModeTest` 共 26 项通过。

## 待办与边界

- 设置页“滑动触发距离”滑条目前只在 14 键滑选或九键拂动开启时可用，日语九键也用它后应常开；由设置合并线程处理（已转告）。
- 中文九键拂动由锁轴改为自由换向，属于用户能感觉到的手感变化。

## 方向圆盘恢复为默认开（用户 10.16 追加）

用户反馈日语拂动的浮窗圆盘和键上方向箭头不见了。原因是 10.11 把“拂动时显示方向圆盘”改成默认关闭，不是绘制失效。现在默认开：`KeyboardInputPreferences.flickDirectionDisc` 默认 `true`，读取改用 `flick_direction_disc_v2`（默认开），所以旧键里存的“关”会一次性恢复为开，之后用户手动关闭的保留；保存时新旧键都写。日文假名、九键拂动、14 键字母对按住时都弹出同一个圆盘并在键上显示方向箭头；圆盘换方向时高亮在 120ms 内渐变过去，不再跳变。`MergedKeyboardGestureTest` 改为显式关闭圆盘以继续检查键内渐变模式。

模拟器复测：`JapaneseKanaGestureTest`、`GlideAndFlickGestureTest`、`FlickDirectionDiscTest`、`MergedKeyboardGestureTest`、`SwipeableKeyButtonGestureTest`、`KeyFlickEmphasisTest`、`JapaneseLayoutActionsTest`、`KanaDirectionHintsSettingsTest`、`FlickComboSurveyTest` 共 55 项通过；圆盘截图 `.codex-artifacts/flick-combo-20261016/disc-compare.png`。

## 方向指示改为对角线分区（用户 10.16 追加）

按住拂动键时键上的方向指示原来是贴边的小三角；现在按方格对角线（过中心的 45° 线，与手势判定一致）把键分成四块，滑向哪边就填亮哪一块。只改 `KanaDirectionIndicator`，日文假名、九键拂动、14 键字母对共用。用户确认长按圆盘只是因为九键拂动开关没开，普通模式长按不改。模拟器 `JapaneseKanaGestureTest`、`FlickDirectionDiscTest`、`FlickComboSurveyTest` 18 项通过，截图 `.codex-artifacts/flick-combo-20261016/diag-indicator.png`（截图后把填色由 27% 提到 45% 不透明，未再截图）。

## 14 键圆盘相邻方向无缝切换（用户 10.16 反馈）

现象：14 键开启方向圆盘后，手指从一个方向直接滑到相邻方向切不过去，必须回中心停一下。原因：14 键字母对仍走 26 键的轴向锁定（方向一旦确定，要沿原轴回到中心才放开）；九键拂动和假名早已是自由换向。修改：`KeyButton.kt` 中 14 键字母对在开启圆盘时也用 `freeDirection`，关闭圆盘的键内渐变模式保持 26 键的锁轴手感。`FlickDirectionDiscTest` 增加九键“左→上”、14 键“右→下”不回中心直接切换的检查（同时给 QW 键补上角标滑动动作，否则该方向本就不可选）。模拟器：`FlickDirectionDiscTest` 通过；修改前同批 `MergedKeyboardGestureTest`、`JapaneseKanaGestureTest` 15 项通过。

## 九键拂动模式长按 1 出数字（用户 10.18 反馈）

现象：拂动模式下长按 2~9 弹出数字、松手输入，长按 1 没反应。原因：1 键（分词）用 `NineKeyButton`，长按出数字只接在 2~9 的 `T9DigitKey` 拂动分支。修改：`NineKeyButton` 加 `flickDigitLongPress`，只对 1 键开启；拂动模式下长按 0.3 秒（与 2~9 相同）弹出单项圆形“1”，松手执行该键原上滑输入；非拂动模式不变。`SwipeableKeyButton` 新增可选 `longPressTimeoutMillis`（默认沿用原菜单时长）。`:app:compileDebugKotlin` 通过；未上模拟器，未安装。

## PQRS / WXYZ 拂动方向（用户 10.18 要求）

四字母键原为左 P、上 Q、右 R、下 S；按用户要求改为左右 P/S、上下 Q/R（WXYZ：左 W、上 X、下 Y、右 Z）。三字母键仍为左、上、右。只改 `T9DigitKey` 的拂动字母顺序，圆盘标签由手势映射生成，自动一致。长按 1 出数字已随 1.14.4 / 20261189 装到平板；本项 `:app:compileDebugKotlin` 通过，未安装。
