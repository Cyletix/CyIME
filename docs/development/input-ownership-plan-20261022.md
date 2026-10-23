# 输入归属统一计划（1.15 第 1 项，2026-10-22）

目标：让“字打到哪里去（宿主输入框 / 快捷发送 / 工具面板 / 剪贴板搜索）”“当前属于哪个输入会话”“现在能不能学习、能不能发给插件（密码、无痕、语音）”各只有一个负责的地方。面板之间只通过明确的切换函数换来换去。现有输入队列（`InputCommandQueue`）和会话过期检查（`InputReadiness` + `InputCommandOwner`）保持不动。

不承诺提速。目的是减少跨功能的串扰，比如最近的“剪贴板搜索关闭后密码框开始学词”（81df01b7）、“剪贴板搜索里实体 Tab 被插入”（67a2322c）、“发送后麦克风没关”（5db532d7）。

## 现状

### 字打到哪里：四个布尔值拼出来

目标由 `InputUIState` 里的四个字段拼出来：`showQuickSendForm`、`quickSendFormFocused`、`quickSendCodeFocused`、`toolPanelInputFocused`，剪贴板搜索再加 `clipboardSearchVisible`（它借用工具面板的输入框 `ToolPanelEditTextHolder`）。

“取当前内部输入框”的同一段 `when` 写了 5 份：

| 位置 | 用途 |
|---|---|
| `XimeInputMethodService.commitTextSilently` | 上屏 |
| `XimeInputMethodService.currentEditorCursorPosition` | 手写验证光标 |
| `XimeInputMethodService.replaceBeforeCursor` | 英文回删替换 |
| `ImeSchemaController.editCursor` | 光标移动 |
| `ImeSchemaController.moveHardwareCursor` | 实体方向键 |

另外有约 12 处单独判断 `quickSendFormFocused || toolPanelInputFocused`（`ImeKeyRouter.usesDirectFieldInput`、`ImeKeyRouter` 退格/组合结束、`ImeSessionController` 两处、`ImeTextCommit.pasteClipboardImage`、服务里的验证码填入、英文回删能力等）。

优先级不一致：上屏和光标代码是“快捷发送优先”，`ImeKeyRouter.routeKeyPress` 却先看 `toolPanelInputFocused`。两个同时为真时，字母进了剪贴板搜索/工具面板，选中的候选却上屏到快捷发送。

### 面板切换：各自改字段

| 动作 | 写在哪里 | 关掉了谁 |
|---|---|---|
| 打开快捷发送（新建/编辑/OCR 三处） | `ImeKeyboardCallbacks` | 工具面板、Overlay；**不关剪贴板搜索** |
| 关闭快捷发送 | `onHideQuickSendForm`、`openToolPanel`、`onWindowHidden`、`applyCompactMode` 四处 | 四处清理的字段各不相同：回车文案有的用 `editorEnterKeyText()`，有的写死“发送”，有的不还原；输入框句柄有的清有的不清 |
| 打开工具面板 | `openToolPanel` | 快捷发送；不关剪贴板搜索 |
| 打开剪贴板搜索 | `openClipboardSearch` | 工具面板、Overlay；**不关快捷发送** |
| 关闭剪贴板搜索 | `closeClipboardSearch`，由换输入框、`onFinishInputView`、实体键盘精简条、语音悬浮条、返回键等调用 | — |
| 焦点变化 | `onQuickSendFormFocusChange`、`onQuickSendCodeFocusChange`、`onToolPanelFocusChange` 直接改字段 | — |

所以“快捷发送开着时打开剪贴板搜索”（或反过来）能让两套标志同时为真，就是上面那个优先级不一致的入口。这一点是我从代码推出来的，还没在设备上复现。

### 能不能学习：四份“密码框”判断

| 判断 | 位置 | 用途 |
|---|---|---|
| `IncognitoState.manual / sensitiveEditor / clipboardSearch` → `active` | 全局对象，服务在 `onStartInput`、开关剪贴板搜索、设置变化时写 | 引擎学习开关（`applyIncognito`）、预测、手写模板、最近使用、剪贴板历史、同步、英文记忆 |
| `PluginEventDispatcher.sensitiveInput` | `onStartInput` 自己再算一遍 | 插件事件；与 `manual`、`clipboardSearch` 合起来正好等于 `IncognitoState.active` |
| `editorSecret` / `editorRestricted` | 服务字段，`EditorInfoClassifier` | 英文联想、回删替换、上屏后预测 |
| `EditorFieldPolicy.Kind.PASSWORD…` | `editorInputPolicy` | 字段字符约束、锁定原生输入、语音按钮是否可用 |

`IncognitoState.sensitiveEditor` 和 `PluginEventDispatcher.sensitiveInput` 是同一个值算两遍；81df01b7 的问题就出在“谁写回这个值”。

### 会话身份：两处换代

`inputReadiness.newEditorSession()` 加 `inputSessionId = System.nanoTime()` 在 `onStartInput` 和 `clearInputState` 各做一遍，前后夹着关剪贴板搜索、结束语音、清 T9、清候选、`editorInputPolicy.begin`、更新无痕等步骤，顺序靠服务里的代码位置维持。`editorInputPolicy`、`lastEditorIdentity`、`KeyboardInputSession` 各自再记一份“是不是同一个输入框”。

## 计划（每步单独编译、测试、提交）

### 第 1 步：一个类型表示“字打到哪里”（纯重构，看不出变化）

新增 `service/InputTarget.kt`：`Host`、`QuickSend(code: Boolean)`、`ToolPanel`、`ClipboardSearch`，由 `InputUIState` 推出来（沿用现有“快捷发送优先”），再给一个取内部输入框的函数。把上面 5 份 `when` 和 12 处布尔判断换成它。`ImeKeyRouter.routeKeyPress` 的判断顺序这一步不动，保持原样。加单元测试固定推导结果。

### 第 2 步：面板只通过切换函数换（需要你点头的地方在这里）

新增一个面板切换的负责者（暂名 `InputPanelTransitions`），提供 `openQuickSend(新建/编辑/OCR)`、`closeQuickSend()`、`openToolPanel()`、`closeToolPanel()`、`openClipboardSearch()`、`closeClipboardSearch()`、焦点变化。打开任何一个都先用同一套清理关掉另外两个；四处关闭快捷发送合成一处。之后 `InputUIState` 的这些字段只有它写。

会让你看得出来的变化只有一条：快捷发送开着时打开剪贴板搜索，会先把快捷发送关掉（反过来也一样）。现在两者能叠在一起，叠在一起时字和候选会去两个不同的框。

### 第 3 步：学习和插件许可只有一个来源（看不出变化）

服务里加一个许可负责者，输入是“手动无痕 / 当前输入框敏感 / 剪贴板搜索开着”，统一写 `IncognitoState` 并同步引擎学习开关。`PluginEventDispatcher` 不再自己算敏感，直接读同一个结果。`editorSecret`（英文联想、回删替换）和 `EditorFieldPolicy`（字符约束、语音可用）是“输入框能做什么”，不是“能不能学”，保留原样，只在文档里标明分工。`IncognitoState` 继续作为各模块跨线程读取的入口，读的地方不改。补测试：密码框里开关剪贴板搜索、搜索中换到密码框/普通框、搜索中切手动无痕。

### 第 4 步：输入会话开始和结束各一个入口（看不出变化，改动最大）

把 `onStartInput` 和 `clearInputState` 里关于会话的步骤收进 `beginEditorSession(attribute, restarting)` 和 `endEditorSession()`：换代（`InputReadiness` + `inputSessionId`）、关面板、结束语音并释放麦克风、字段策略、学习许可，按固定顺序执行。`InputCommandQueue`、`InputCommandOwner` 的检查保持原样。这一步会改动 `XimeInputMethodService.kt` 的 `onStartInput`，需要先和改语音悬浮条的线程错开。

实际做法（保守版）：`newInputGeneration()` 统一做“换代、丢弃待上屏候选、结束语音输入会话（释放麦克风）”，`onStartInput` 和 `clearInputState` 都只调它；`beginEditorField(attribute, restarting)` 一处读输入框属性，得出学习许可、补全限制和字段策略。`inputSessionId` 换值的位置没挪：`onStartInput` 里它在语音收尾（`restoreAfterVoiceFinish`）之后，提前会让收尾时投递的任务落进新会话，属于行为变化。以后新增“换输入框时要清掉的东西”放进这两个函数。

## 不做的事

不改输入队列和会话过期检查；不改 `IncognitoState` 的读取方；不改快捷发送“表单开着但没聚焦时，退格删表单里的字”这类现有行为（第 1 步原样保留，要改另外问）；不声称提速。

## 协作

`XimeInputMethodService.kt` 有语音悬浮条线程在改，剪贴板文件有剪贴板线程在改。每步动这两处之前先经协调会话确认，只提交自己的改动（`git commit -- <路径>`）。Gradle 编译和测试先经协调会话排队。

## 进度

- [x] 第 1 步（e1b3ab45）
- [ ] 第 2 步：快捷发送的打开/关闭/焦点已收成一处（4051637c，看不出变化）；快捷发送与剪贴板搜索互斥这条等确认
- [x] 第 3 步（ac590aec，第 2 步等确认时先做）
- [x] 第 4 步（e20181dc，保守版，见上）
