<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

**CyIME** 是 Cyletix 维护的 Android 输入法，基于 [Rime](https://rime.im/) 引擎，提供中文（五笔 / 拼音 / 九宫格 / 双拼）、日语假名、英文、手写与语音输入。

## 项目定位

- 对外身份：应用名 **CyIME**，维护者 **Cyletix**，安装包标识 `com.cyletix.cyime`，APK 命名为 `CyIME-<版本>-<abi>.apk`。
- 源码派生自上游 [Xime](https://github.com/ximeiorg/Xime)（作者 Kor1 / kingzcheung 及贡献者），继续遵循 [GNU GPL v3](LICENSE)。上游与第三方版权声明保留在 [LICENSE](LICENSE)、`app/src/main/assets/NOTICE.txt` 与 [TRADEMARKS.md](TRADEMARKS.md) 中。
- 为持续跟踪上游、减少合并冲突，源码 namespace（`com.kingzcheung.xime`）、插件 ID、`xime.yaml` / `xime.custom.yaml`、扩展市场与 index 协议保持不变，仅安装身份（applicationId）独立。取舍与影响见 [命名与身份记录](docs/cyime-identity.md)。

## 截图

<!-- 截图待补：把截图放入 docs/Screenshot/ 后在此插入，例如
<table align="center">
  <tr>
    <td><img src="docs/Screenshot/xxx.jpg" width="180"><br><p align="center">说明</p></td>
  </tr>
</table>
-->

## 功能特点

- **多种输入方案** — 内置雾凇拼音词库，提供五笔、拼音与混输方案，支持自定义（双拼、笔画、九键 / 14 键等），可从方案市场下载或无线导入
- **Rime 引擎** — 使用成熟稳定的 Rime（librime）引擎
- **丰富键盘布局** — QWERTY 全键盘、T9 九宫格拼音、九宫格笔画、手写、数字（含计算器）、符号与 Emoji
- **悬浮键盘** — 悬浮卡片样式，支持拖拽移动与半透明圆角
- **语音输入** — 本地离线流式识别，也可通过插件接入在线 ASR
- **AI 联想** — 基于 Transformer 的联想词预测（可选）
- **Material 3 界面** — 浅色 / 深色主题与多种配色方案
- **键盘与工具栏定制** — 键盘高度 / 位置调节、工具栏按钮自定义、按键音效与振动强度
- **滑动手势** — 光标移动、删除、符号等滑动手势
- **剪贴板管理** — 剪贴板历史、快捷发送与置顶，可通过插件与远端双向同步
- **实体键盘支持** — 连接物理 / 蓝牙键盘时显示浮动候选栏
- **插件扩展** — 通过扩展商店安装 Lua 插件（表情、剪贴板同步、在线语音、WebDAV 备份等）

## 系统要求

- Android 9.0 (API 28) 及以上

## 安装

### 从 Releases 下载

1. 在 [Releases](https://github.com/Cyletix/CyIME/releases) 下载对应架构的 `CyIME-<版本>-<abi>.apk`：
   - `arm64-v8a`：绝大多数现代手机
   - `armeabi-v7a`：旧款 32 位手机
   - `x86_64` / `x86`：模拟器
   - `universal`：包含全部架构，体积较大
2. 安装后在系统设置中启用 **CyIME**，并把它设为当前输入法。

> **从旧版（`com.kingzcheung.xime`）迁移**：安装身份已独立为 `com.cyletix.cyime`，系统视为新应用，可与旧版并存；旧版的数据与配置不会自动继承，需要重新设置。若 GitHub 下载不稳定，可自行构建（见下）。

### 插件（可选）

Lua 插件（`.xipk`）可在主应用「设置 > 扩展商店」中安装和启用：颜文字、表情包、在线语音识别（FunAsr / 火山等）、WebDAV 剪贴板同步与备份等。插件标识与市场协议沿用上游。

## 构建

```bash
# 克隆项目（包含子模块）
git clone --recursive https://github.com/Cyletix/CyIME.git

# 已克隆过则初始化子模块
git submodule update --init --recursive

# 调试包 / 发布包
./gradlew assembleDebug
./gradlew assembleRelease
```

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Rime (librime) · JNI (Native C++)

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)：先提 Issue、最小修改、commit 需 GPG 签名。

## 致谢

- 上游 [Xime](https://github.com/ximeiorg/Xime)：本分支的起点
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) · [onnxruntime](https://github.com/microsoft/onnxruntime)

## 许可证

[GPLv3](LICENSE)。上游代码版权归其作者所有，本分支的修改由 Cyletix 维护。"Xime" 名称、Logo 及其他品牌资产**不属于** GPLv3 授权范围，详见 [TRADEMARKS.md](TRADEMARKS.md)。
