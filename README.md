<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.en.md">English</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

**CyIME** 是 Cyletix 维护的 Android 中文输入法，基于 [Rime](https://rime.im/) 引擎。拼音（26 键、九键、14 键、双拼）、五笔、手写、语音都能用，还带字幕模式、剪贴板管理和电脑同步。

## 这是什么

- 应用名 **CyIME**，安装标识 `com.cyletix.cyime`，安装包命名 `CyIME-<版本>-<架构>.apk`。
- 源码派生自上游 [Xime](https://github.com/ximeiorg/Xime)（作者 Kor1 / kingzcheung 及贡献者），继续遵循 [GNU GPL v3](LICENSE)。上游与第三方版权声明保留在 [LICENSE](LICENSE)、`app/src/main/assets/NOTICE.txt` 与 [TRADEMARKS.md](TRADEMARKS.md)。
- 为了持续跟踪上游，源码包名 `com.kingzcheung.xime`、插件标识、`xime.yaml` / `xime.custom.yaml`、扩展商店和索引协议都没改，只有安装身份独立。取舍见 [命名与身份记录](docs/cyime-identity.md)。

## 功能

**打字**

- 26 键拼音：整句按聊天语料重新排序，句尾「吧 / 呗」、句首「哥 / 个」这类常见错位已修；候选可显示拼音或英文释义，按住 Shift 点候选直接上屏英文。
- 九键拼音：自带词组模型，长句逐键选词；拂动选字母、侧栏符号、长按「下一项」拖到候选上也能上英文。
- 14 键、双拼（小鹤等）、五笔、笔画、日语假名；方案可在设置里下载、导入、启停。
- 手写：中文、英文、日语、数学公式，支持 S Pen 压感；手写样本可备份、导入。
- 语音：本地离线流式识别（Paraformer 边说边出字，SenseVoice 停顿后校正、加标点），也可通过插件用在线识别。
- 英文：整词联想，中英切换不闪。

**字幕模式**

- 给听力不便的人用：打开后把周围的话实时转成中文字幕，自动按说话人分段，可以给说话人起名字；桌面有单独的「字幕」图标，图标颜色跟随主题。

**键盘**

- 固定 / 悬浮两种放置方式，可调大小、位置、透明度、底部留白，支持分体；悬浮键盘拖动流畅。
- 主题：浅色、深色、玻璃效果和多种配色，主题预览和实际键盘一致；桌面图标可跟随主题色。
- 工具栏和菜单可自定义；按键音、振动强度、手势预设（滑动 / 长按 / 英文滑行）可选，冲突项会说明被谁锁定。
- 连接实体或蓝牙键盘时显示浮动候选栏，支持快捷键切语言。

**剪贴板与同步**

- 剪贴板历史、固定、折叠、快捷发送；刚截的图会在候选栏提示「粘贴」。
- 与 Windows 上的 [CyIME Windows](https://github.com/Cyletix/CyIME-Windows) 扫码或局域网配对后，两边剪贴板双向同步。
- 本地备份与恢复；WebDAV 等同步通过插件接入。

**扩展**

- 扩展商店三页：输入方案、模型、插件。手写模型、联想模型、语音模型、声纹模型都可以在应用里下载，也可以用内置模型的完整版安装包。
- Lua 插件（`.xipk`）：颜文字、表情包、在线语音、WebDAV 备份等，插件标识与市场协议沿用上游。

## 安装

要求 Android 9.0（API 28）及以上。

1. 在 [Releases](https://github.com/Cyletix/CyIME/releases) 下载安装包：
   - **轻量版** `CyIME-<版本>-arm64-v8a.apk`：不带离线模型，语音、手写、联想模型按需在应用里下载。
   - **完整版** `CyIME-<版本>-full-arm64-v8a.apk`：内置手写、联想、语音和声纹模型，装上就能离线用，体积较大。
2. 安装后在系统设置里启用 **CyIME**，并把它设为当前输入法。

> 从旧版 `com.kingzcheung.xime` 迁移：安装身份已独立为 `com.cyletix.cyime`，系统当作新应用，可以并存，旧版数据不会自动继承。

## 构建

```bash
# 克隆项目（含子模块）
git clone --recursive https://github.com/Cyletix/CyIME.git

# 已克隆过则初始化子模块
git submodule update --init --recursive

# 调试包 / 发布包
./gradlew assembleDebug
./gradlew assembleRelease
```

日常出包用仓库根目录的 `packager.cmd`（双击打开图形界面，带参数是命令行），预设见 `scripts/packager/config.json`；版本规则见 [打包与版本规则](docs/packaging.md)。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Rime (librime) · JNI (C++) · onnxruntime

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)：先提 Issue、最小修改、commit 需 GPG 签名。

## 致谢

- 上游 [Xime](https://github.com/ximeiorg/Xime)：本分支的起点
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) · [onnxruntime](https://github.com/microsoft/onnxruntime) · [雾凇拼音](https://github.com/iDvel/rime-ice)

## 许可证

[GPLv3](LICENSE)。上游代码版权归其作者所有，本分支的修改由 Cyletix 维护。"Xime" 名称、Logo 及其他品牌资产**不属于** GPLv3 授权范围，详见 [TRADEMARKS.md](TRADEMARKS.md)。
