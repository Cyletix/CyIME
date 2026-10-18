# 2026-10-06 空格长按编辑拼音光标

目标：拼音尚未上屏时，长按空格滑动只调整拼音串中的编辑位置，不提交、清空拼音或移动应用光标；物理键盘左右键仍移动应用光标。空闲输入、英文补全，以及剪贴板／OCR／工具内部编辑框继续使用既有光标规则。

## 原因与修改

- 原空格回调仅在用户事先点开拼音编辑条时才被 `onPreeditKeyInput` 接管。普通组词直接进入 `moveEditorCursor`，在输入框内显示编码的模式下还会结束 composing、清空引擎。这是入口漏接，不是手势距离参数问题。
- 空格进入光标模式时，现在直接复用现有拼音编辑条。两方向移动都先检查组词状态；异步打开期间保存滑动步长，打开失败也不把这些步长传给应用。关闭、换会话及销毁时取消待处理请求。
- 拼音串内容不变时，`applyPinyinEdit` 只移动引擎 caret，不重复 `setInput`，避免重建已确认分段。
- 仅移动拼音 caret 时，候选与预编辑仍刷新，但 `updateUIWithResult` 不再写入应用 composing 区；确实插入／删除拼音时继续同步文本。
- 物理按键仍通过 `handleHardwareCandidateKey` → `moveHardwareCursor`，没有接入触摸手势回调。内部编辑框优先于自动拼音编辑，保留空格移动草稿光标的行为。

## 验证

- 本地 JVM 75 项通过：`EditorCursorTest` 40 项、`HardwareCandidateNavigationTest` 26 项、`PinyinEditBufferTest` 6 项、`PinyinMultiTapTest` 3 项。日志 `.codex-artifacts/space-preedit-checks.log`。
- Android 回归用例编译成功，未执行：从普通组词直接长按空格、输入框显示拼音时保护原文／应用光标，以及移动 caret 保留部分已选字。原手势测试先调用 `openEditor()`，绕开了本次实际失败入口，已移除这个前置条件；另增加输入框模式反例。
- 最后的输入框同步开关补修纳入 Release 构建；上述 JVM 用例验证的光标／编辑缓冲源码随后未变。它们不覆盖 Android 编辑框、手势打开时序或 JNI 实际选词行为。
- 构建时 `adb devices` 没有已授权设备，没有为此继续排查 ADB 或变更主机设置；未做真机手势与图像检查，未验收。没有修改键盘尺寸或新增一套编辑界面。

## 交付

本轮为兼容修复：1.9.5 / 20261097，继已交付的 1.9.4 / 20261096 递增。固定入口 `auto.cmd` 已完成轻量签名 Release 构建、版本／签名／源码指纹校验，最终状态为 `INSTALL-PENDING`，日志 `.codex-artifacts/space-preedit-delivery.log`。结束时设备列表仍为空，未安装、未真机验收，未提交 Git、未公开发布。

APK：`D:/CyIME-Data/deliveries/1.9.5/20261097/CyIME-1.9.5-arm64-v8a.apk`，111,030,121 字节；SHA256 `9d04e6d978a2bf83364501f9cf1b68c074e5effe87e49c0c6f53d893cc4a3a0a`。同目录保留 `build-receipt.json` 与 `delivery.json`；源指纹 `38dc5b2fad970bde111fd60ca7157f356fedb3c337e4eaeb46df3c58087b8848`。后续连接设备可由同一入口复用本包并核对实际安装 APK。
