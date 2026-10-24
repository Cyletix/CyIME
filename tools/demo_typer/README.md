# 演示视频自动打字

在平板的 Obsidian 演示笔记里，经过 CyIME 键盘逐键打出 `demo.yaml` 里的文稿，配合录屏做演示视频。所有输入都是在键盘上的真实点按、拂动、滑行和手写笔画（monkey 脚本注入，和 `tools/ime_lab/key_latency_drive.py` 同一方式），不是 `adb input text`。语音用电脑 TTS 合成后从音箱放给平板麦克风；手写是用户本人在手写板上写一遍，用 getevent 记下原始笔迹后按原坐标和时间重放（重放是手指触摸，不带 S Pen 笔型）。

不改应用代码，不需要 Gradle。用 adb 前先看 `D:/OneDrive/Notebook/Project/Agent状态同步.md` 并登记平板占用。

## 文件

- `demo_typer.py`：驱动，子命令见文件头。
- `demo.yaml`：文稿和逐键脚本；每段的 `text` 是彩排比对用的期望文字。
- `layouts/<设备>.json`：各布局按键的屏幕坐标（当前旋转方向），格式 `{"rotation": 1, "common": {名字: [x, y]}, "layouts": {布局: {名字: [x, y, 手势?, 距离?]}}}`；九键布局加 `"_t9": true`（拼音自动换成数字键），假名九键加 `"_kana": true`（按点按/左/上/右/下拂动，浊音和小写用 `mod` 键）。
- 笔迹、TTS 音频、标定截图放在 `D:/CyIME-Data/demo-video/`，不进仓库。

## 流程

1. `devices` 看触摸屏和笔的节点、屏幕方向；`screenshot <布局>` 截图标定，`taps` 打印实际点到的位置用来核对坐标换算。
2. `record-ink <名字>`：用户在手写板上写一条，停笔 3 秒自动保存。
3. `tts` 生成语音片段（Windows 自带 Huihui / Haruka 语音，离线）。
4. 彩排：`clear` 清空演示笔记，`run --segment <段名>`，`check --segment <段名>` 读回笔记文件逐字比对；不对就改分词或换词。彩排和正式录制都开「无痕」，不改变词序，也不把演示文字学进词库。
5. 正式录制：用户开录屏后 `run`。
