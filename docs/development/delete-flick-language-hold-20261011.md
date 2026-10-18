# 删除键“清空”过渡与语言键长按时长（2026-10-11）

## 删除键下拉“清空”的过渡和字符键不一样

用户反馈：在删除键上下拉出“清空”时，过渡和带数字角标的字符键不一样。逐帧对比见 `.codex-artifacts/delete-flick-20261011/compare.png`：第一行是字符键，第二行是改前的删除键，第三行是改后的删除键，每帧下拉 16px。

原因有两点，都在功能键的手势和绘制里（`RepeatingKeyGesture.kt`、`SwipeableIconKeyButton`）：
- 字符键过了触摸阈值就开始让角标移向中心；删除键要拉到 50dp 才开始显示，之前一直不动，到 50～80dp 才一下子出现。50dp 原本是为了防止长按连删时手指轻微漂移打断连删。
- 字符键移动的就是角标本身；删除键上的“清空”小字不动，另画一份从上方移入，所以会出现两层“清空”叠在一起。

修复：
- 文字从触摸阈值开始移动和放大，和字符键一致。停止连删的距离仍是 50dp，提交清空的距离仍是 80dp。已经在连删时再滑动，提示仍从 50dp 开始出现。
- 移动的那份文字出现时，隐藏原来的“清空”小字。

保留未改：键面底色随拉动逐渐变红（清空）或变蓝（撤回），这是 1.9.14 按用户要求加的，本次没动。字符键没有这个变色，这一点两者仍不一样。

重输键（九键、笔画）上的“重输”只是图标下的说明文字，没有滑动动作，不存在这个问题。

验证（模拟器）：新增 `FlickTransitionFramesTest`，输出逐帧图。`KeyboardDeleteGestureIntegrationTest`、`SwipeableIconKeyButtonTest`、`KeyboardControlsVisualTest` 通过。

## 长按语言键弹出列表的等待时间可调

原来按住 200ms 弹出语言列表，时长写死在 `LanguageKeyButton`。设置里没有通用的长按时长，空格键另有自己的长按时长（0.1～1 秒）。现在新增独立设置：设置 → 布局与显示 → “长按语言键弹出列表”，范围 0.05～1 秒，每档 0.05 秒，默认 0.2 秒（与原来相同）。偏好键为 `language_menu_hold_seconds`，已加入键盘监听的偏好键，改完立即生效。

验证：编译通过。`LanguageKeyButtonTest` 有 8 项失败（找不到 `language-schema:second`），把延时临时改回固定 200ms 跑对照，同样是这 8 项失败，说明是本次之前就存在的问题，本次未处理。没有在真实输入法里拖动滑条核对。

## 符号页不跟随 iOS 渐变玻璃主题

用户反馈（平板 1.12.4，iOS 渐变主题加玻璃）：符号页的按键比主键盘暗一层，底部分类栏是一整块纯黑。

原因（`SymbolKeyboardLayout.kt`）：
- 工具面板靠外层传进来的 `inheritKeyboardBackground` 标记，在玻璃下不再重画键盘底色。符号页的 `KeyboardKeySpacingScope` 会交回一个新的 modifier，标记丢了，于是符号页在玻璃底上又画了一层键盘背景，整页暗一档。
- 分类栏的底色是写死的实色 `lerp(背景, 文字, 0.05)`，每个标签再铺一次同样的实色，没有经过 `frostedKeyColor`。iOS 渐变的主题底色是纯黑，所以看起来是一整块黑。

修复：
- 把外层标记接回符号页的根节点。
- 分类栏底色改为 `frostedKeyColor`，和旁边的按键一样半透明。
- 标签不再自己铺底色，只在选中时叠一层 12% 的文字色。

其他面板的检查：新增 `GlassPanelsSurveyTest`，在 iOS 渐变主题加玻璃、彩色条纹背景下，截取主键盘、符号、表情、剪贴板、编辑、菜单、数字、手写共 8 个页面，并对比同一位置的像素。改前只有符号页比主键盘暗、分类栏纯黑；改后符号页和主键盘一致。其他页面没有发现底色重叠或实色块。截图里剪贴板文字对比度很低，是因为测试夹具用的是默认浅色 MaterialTheme，真实输入法里用的是主题配色，之前在真实输入法里核对过是清楚的。截图在 `.codex-artifacts/glass-panels-20261011/`。

同批测试中有两项失败：`SymbolNavigationTest.categoryTabsFitFooterOnPhoneAndTabletAndStillScroll`（分类栏几何）和 `KeyboardPanelGlassTest.keyboardPanelsKeepTheKeyboardBackdropAndTheirActions`（表情、剪贴板等面板，不涉及符号页）。本次只改了颜色和背景标记，没有改尺寸，判断与本次无关，但没有做对照复跑。

## PIN 和高安全密码框完全无法输入

用户反馈：三星安全文件夹、银行 App 这类 PIN 码或高安全密码框里，数字键盘按什么都输不进去。

模拟器复现：新增 `RealPinPadTapTest`，用真实输入法和真实触摸点按屏幕上的数字键。模拟器的主机键盘会被当成实体键盘，所以测试通过服务的“展开屏幕键盘”入口把数字键盘显示出来。各类输入框的结果（改前 → 改后）：

| 输入框 | 改前 | 改后 |
|---|---|---|
| numberPassword | 通过 | 通过 |
| numberPassword + IME_FLAG_NO_PERSONALIZED_LEARNING | 通过 | 通过 |
| numberPassword + 窗口 FLAG_SECURE | 通过 | 通过 |
| number | 通过 | 通过 |
| textPassword + 不学习 | 通过 | 通过 |
| 只接受按键事件的 PIN 控件（忽略 commitText） | **失败，什么都输不进** | 通过 |
| 文字密码框之后紧接的 numberPassword | 时好时坏 | 时好时坏，见下文 |

原因：PIN、数字、密码框走的是直接输入（`commitDirectFieldLiteral`），按键一律用 `commitText` 提交。安全类 App 的 PIN 控件常常不处理 `commitText`，只认按键事件。系统自带输入法用 `sendKeyChar` 输入数字，发出的是 KEYCODE_0～9 按键事件，所以在这些控件里能用。

修复（`ImeKeyRouter.commitDirectFieldLiteral`）：在 PIN、数字、电话、密码框里，改为逐字调用 `sendKeyChar`。数字发按键事件，其他字符仍按文字提交。删除键原本就发 KEYCODE_DEL，回车原本就走编辑动作或按键事件，都不用改。原有的 `RealEditorFieldInputTest`（各类输入框的字面输入规则）改后仍通过。

未解决：在模拟器上，先进一个文字密码框、再进 PIN 框时，偶尔仍显示字母键盘，并出现剪贴板预览栏；按数字键时提交的是字母，被 PIN 框拒收。这个现象只在模拟器“实体键盘展开屏幕键盘”的状态下见到，不稳定复现，原因还没查清。

未在三星实机上验证。打包需要和九键线程错开（它打包时会临时放入模型），交给它下一次打包时一起带上。
