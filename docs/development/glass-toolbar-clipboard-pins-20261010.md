# 玻璃工具栏开启状态与剪贴板固定栏折叠（2026-10-10）

两项都是兼容修复，并入本轮 1.12.1，版本号不单独升。

## 玻璃效果下看不出哪个工具开着

用户反馈：黑色主题开玻璃后，工具栏每个图标后面都有同样的灰色底，分不出手写是不是开着。原因在 `ToolbarActionButton`（`CandidateBar.kt`）：玻璃模式下所有工具栏按钮都铺一层键帽底色（`glassControlBackground`，用来保证图标看得清），开启状态只在这层底色上混入 18% 的强调色。黑色主题的强调色接近灰白，混进去几乎看不出差别。

修复：玻璃模式下，开启的工具改为实心底色加反色图标。底色优先用强调色，如果强调色和键帽底色的对比度低于 3:1，就改用图标色（黑色主题下是浅灰白）；图标按底色亮度取黑或白。未开启的按钮和非玻璃模式都不变。语音保留原来的脉动光晕，不铺实心底，否则光晕会被盖住。

验证（本机 x86_64 模拟器 `emulator-5560`）：
- 新增 `GlassToolbarActiveStateTest` 2 项通过：黑色主题手写模式下，玻璃和非玻璃两种情况，开启的按钮填色与其他按钮的通道差都至少 60。截图在 `.codex-artifacts/glass-toolbar-20261010/`。
- 真实输入法：完整调试包，黑色主题加玻璃，宿主是 CyIME 设置页（深色）。修复后只有手写按钮是浅色实心底，其余是半透明键帽底。同时过了一遍 26 键、手写、表情、剪贴板、数字、符号页，没有发现其他看不清或错位的问题。未开启按钮的键帽底仍然保留，这是原有设计。

## 剪贴板固定栏在没有未固定内容时也折叠

用户反馈：“邮箱”分类里 4 条全部固定后，固定栏收起时只显示一行（2 条），下面明明是空的，另外 2 条却被藏起来了。原因：`clipboardBoardContent` 只要固定条目多于一行就折叠，没有看下面有没有未固定的记录。

规则：折叠固定栏只是为了给下面的未固定记录腾出空间。当前分类下没有未固定记录时，固定内容全部显示，也不显示展开/收起按钮；有未固定记录时照旧折叠成一行，按钮照旧可用。存储的固定集合和历史都不变。

验证：单元测试 `ClipboardBoardContentTest` 7 项通过，新增全固定不折叠的反例。设备测试 `ClipboardPinnedMotionTest` 原来用 6 条全固定作为折叠夹具，已加一条未固定记录，并改在右列取样（左列取样点会被下移的未固定卡片经过）。它和 `ClipboardSelectionPinsUiTest`、`ClipboardToolbarVisibilityTest` 共 12 项通过。

## 同批运行中与本次改动无关的失败

- `ClipboardBoardCompactTest.pinsLeadMixedRecordsByTimestampAndCornerLockStaysInsideBoundedPreview`：固定卡片角上的锁图标未显示。该用例只固定 1 条，新规则下与原来一样全部显示，判断与本次无关，未处理。
- `ToolbarUiTest.toolbarEdgesStayAlignedAndDefaultActionsFitPhoneAndTablet`：找不到“语音”按钮。用例没有开玻璃，判断与本次无关，未处理。

这两项都是推断，没有在不含本次改动的版本上对照复跑。另外，如果系统输入法设成了渲染专用调试包，真实输入法服务会在测试进程里因 `PluginManager` 未初始化而报错，跑设备测试前要先把系统输入法切回 LatinIME。

未打包安装：当前没有连接真机，按要求也不在两台手机上测试。

## 中文按键浮窗改用日语假名圆盘样式

用户要求：中文键盘的长按浮窗和滑动选字浮窗，改成和日语九键假名圆盘一样的样式，只改样式，不改行为。

`SwipeBubble.kt` 的 `drawSwipeBubble` 是 26 键、14 键、九键、笔画、数字和常用符号页共用的浮窗。原来的浮窗是一个“键帽连着宽体”的形状，盖住被按的键；选中项是强调色圆角方块，文字也用强调色。现在改为：
- 浮窗悬在按键上方 6dp，单个字符是圆形，多项是胶囊形，背景色和阴影不变。
- 被按的键露出来，键的上沿有一个指向浮窗的小三角，用强调色 60% 透明度，和假名键一样。
- 长按选中项后面是浅色圆底，文字用正常颜色、加粗、字号大一档；其他项是 75% 透明度的普通字重。

没有改动的部分：触发方式、浮窗出现的时机、候选内容、选择和提交逻辑、删除键滑动的红蓝警示色、浮窗宽度的计算（长按菜单仍按原规则排列格子）。

验证（模拟器）：新增 `KeyPopupStyleScreenshotTest` 输出前后截图，旧样式是临时用 HEAD 版本的 `SwipeBubble.kt` 构建后截的，截完已恢复，并核对了文件哈希。对比图是 `.codex-artifacts/key-popup-style-20261010/compare.png`。`SwipeBubbleClippingTest`、`KeyboardSwipeIntegrationTest` 设备测试和 `SwipeBubblePlacementTest` 单元测试都通过。没有在真实输入法里逐个布局按住查看。

日语四向小字：这个设置已经存在（1.9.9 起），位置是 设置 → 布局与显示 → 假名方向提示，默认关，和中文 26 键角标的做法一致，本次没有改动。

## 中文拂动键改用日语四向圆盘

用户补充说明：要的不是只换浮窗外观，而是像日语假名那样，按住时弹出四个方向的圆盘，方向和原来一致。具体是 14 键双字母键左右拂动出字母，以及九键“拂动输入”模式（左、上、右拂动出字母，长按出数字）。

实现：把 `KanaFlickPreview` 的圆盘抽成共用的 `DirectionDiscPreview`，日语假名照旧调用，测试标签不变。`SwipeableKeyButton` 在九键拂动模式或 14 键字母对的键被按住时显示这个圆盘：中心是键面文字，四个方向的文字直接取自这次手势实际使用的映射，所以符号上滑、下滑方向的设置也会照样反映出来；拂动超过阈值时，对应方向出现浅色圆底，键上出现和假名键一样的方向小三角。显示圆盘时不再显示原来键内的移动文字。

没有改动的部分：拂动阈值、方向和字母的对应关系、长按出数字、点按、取消和提交逻辑。九键“长按”模式仍然用上一节的长按浮窗。

一个实现上的坑：最初从 `rememberUpdatedState` 的状态里读方向文字，每次重组都会写入新的手势对象，形成无限重组，测试报 `ComposeNotIdleException`。改为直接读本次计算出的手势对象。

验证（模拟器）：新增 `FlickDirectionDiscTest`，覆盖九键按住、左拂选中 A，以及 14 键右拂选中 w，提交结果和原来相同，截图在 `.codex-artifacts/flick-disc-20261010/compare.png`。`MergedKeyboardGestureTest` 原来断言键内移动文字是“w”，现在改为断言圆盘的右侧被选中；按住期间圆盘窗口里也有“qw”，所以抬手时改为定位键本身。另外 `GlideAndFlickGestureTest`（九键拂动）、`JapaneseKanaGestureTest`、`KanaPreviewLayoutTest`、`SwipeableKeyButtonGestureTest`、`KeyFlickEmphasisTest` 以及删除键相关手势测试，全部通过（59 项）。

## 2026-10-12 补充：分隔线上的展开箭头

- 固定内容折叠时，固定区和最近记录之间的分隔线中间显示“还有 N 条固定 ⌄”，整条线可点（至少 32dp 高）；展开后变成“收起固定 ⌃”。工具栏里的“固定 N”按钮保留。不能折叠时仍是原来的细线。
- 代码：`ui/menubar/ClipboardBoardView.kt` 的 `PinsFoldDivider`。
- 验证：`ClipboardPinsFoldArrowTest`（深浅色 × 玻璃开关，展开、收起各一次）及原有 `ClipboardPinnedMotionTest`、`ClipboardGridStabilityTest`、`ClipboardSelectionPinsUiTest` 在模拟器通过；截图在 `.codex-artifacts/clipboard-pins-fold-20261012/`。
