# 终端（SSH）输入框适配（2026-10-31）

## 现象与根因

Termux、ConnectBot 这类终端 / SSH 客户端的输入框 `inputType` 没有类别位：默认是 `TYPE_NULL`，Termux「按字符输入」写 `VISIBLE_PASSWORD | NO_SUGGESTIONS`（同样不带 `TYPE_CLASS_TEXT`）。它们挂的是 `BaseInputConnection`，编辑区永远是空的：

- `getTextBeforeCursor` / `getTextAfterCursor` / `getSurroundingText` 总是空串，`setSelection` 返回成功但什么也不做。
- 组合区文字：Termux 存着等 `finishComposingText` 一起发；ConnectBot 这类按键式连接收到就直接发给终端、清空，之后每次改写都会再发一遍。

CyIME 原来只把 `TYPE_NULL` 当“秘密框”（关英文联想和回删替换），其他路径照普通文本框处理：

| 操作 | 原来在终端里的结果 |
| --- | --- |
| 实体键盘方向键 | 一律交给 `EditorCursor.movePhysical`，设计上“从不把方向键发给宿主”；终端读不到文字，原地不动。**上键翻命令历史、左右改命令、vim/tmux 里移动全部失效** |
| 空格滑动移光标、编辑面板左右上下、行首行尾 | 先读文字判断“到头了”，读到空串就不发键；上下键只在文本内移动。同样全部无效 |
| 编辑面板 / 手势「撤销」 | 发 Ctrl+Z，终端里是挂起前台程序（vim、top 被挂起） |
| 编辑面板 / 手势「粘贴」 | `performContextMenuAction(paste)`，终端不接，什么也没有 |
| 输入成对符号（默认关） | `(` 补成 `()` 后光标回退无效，光标停在 `)` 后面 |
| 编码显示在输入框（默认候选栏） | 拼音写进组合区：按键式终端里每打一个字母就把整串再发一遍 |
| 语音临时结果 | 同上，写组合区，按键式终端里重复上屏 |
| Termux「按字符输入」 | 不是 `TYPE_NULL`，没被认成终端，也不算秘密框 |

## 修改

- `EditorInfoClassifier.isTerminalEditor`：类别位为 0 即终端；`isRestrictedEditor` / `isSecretEditor` 改用它，覆盖 Termux 两种写法。
- `TerminalEditor`（新）：当前输入框是否终端，`onStartInput` 写入；终端里的按键发送与编辑动作映射。
- 终端里（键盘自己的输入框如剪贴板搜索不算）：
  - 实体键盘方向键（含 Shift/Ctrl/Alt）原样转给终端；拼音组字中的方向键仍在拼音里移动，不变。
  - 空格滑动、编辑面板左右上下发真实方向键（上下即命令历史），行首/行尾发 Home/End；选择类动作按普通移动；进出选择模式、全选、剪切不发任何东西。
  - 撤销/重做不发 Ctrl+Z；粘贴直接上屏剪贴板文字；复制仍交给宿主。符号键按住当 Ctrl 的快捷键不变（Ctrl+C 等照常发给终端）。
  - 成对符号不补结尾、退格不两边删。
  - 「编码显示在输入框」在终端里改为候选栏显示，用户设置不变。
  - 语音临时结果不写组合区，整句识别完一次上屏。
- 普通输入框行为不变：所有分支都先判断 `isTerminalEditor()`。

## 未改

- 终端里不自动切英文：在 SSH 里写中文提交说明、聊天仍要中文；中文模式下照常组字、上屏用 `commitText`。
- 删词滑动、清空键依赖读回文字，终端里仍是无操作（不会误删）。
- 工具栏语音的流式上屏（`StreamingVoiceText`）改写已上屏部分时用 `deleteSurroundingText`，Termux、ConnectBot 都把它换成退格键，未改。

## 检查

- 单测：`EditorInfoClassifierTest`（新增终端 3 项）、`TerminalEditorTest`（新，4 项）。云端用 kotlinc 2.2.20 + Robolectric android-all 14 单独编译这两个源文件和测试，30 项全过；改动的其余文件未在云端编译（无 Android SDK），需 PC 上 `./gradlew assembleDebug` 和 `./gradlew test`。
- 真机：未验收。验收步骤：Termux（或 ConnectBot）里分别用屏幕键盘和实体键盘：上键出上一条命令、左右键改命令、Home/End；编辑面板粘贴；vim 里撤销按钮不再挂起 vim；中文模式打一句中文上屏。
