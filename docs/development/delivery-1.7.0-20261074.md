# 1.7.0 / 20261074 轻量验证包（2026-10-04）

按用户明确要求，完成本轮后直接构建轻量正式包并 ADB 覆盖安装到 SM-X800（R52T800SKWZ），保留配置和已下载模型。未生成内置模型版，未发布 GitHub Release，未提交 Git。

## 本次累计更新

- [大写键拖动](shift-slide-20261004.md)：离开按键才显示主题直线，松手输入单个大写字母，回到大写键/空白/越界取消，不触发目标键长按及符号手势。
- [上一轮键区与反馈](held-flick-and-panel-alignment-20261004.md)：行距增幅压缩但保留底排位置；九键数字页对齐，删除及14键原位渐变和静态按住高亮，符号页长按持续删除。
- [悬浮横条与语音开关](floating-bar-and-speech-switches-20261004.md)：移动条在实际底部空白居中，调节前后共用同一条，语音开关沿用二次校正的主题配色。

## 交付核对

- 文件：`D:/CyIME-Data/deliveries/1.7.0/20261074/CyIME-1.7.0-arm64-v8a.apk`
- 版本：1.7.0 / 20261074；包名 `com.cyletix.cyime`；轻量 Release ARM64；80,908,539 字节。
- SHA256：`c2003b7d75511f0284d03750126a91ae138f8567dccdf2d1ccf8e0cf7276ba20`；ADB 安装成功，设备 APK 哈希一致，设备回读版本一致。
- 累计源码目录：`C:/Users/Administrator/.codex/worktrees/hardware-input-state/CyIME`。
- 基线提交：`3b3b5725ea74931cd9c1c3ba8ba00d25eeb0424d`，含累计未提交修改。
- 构建源码指纹：`a1adfdcb8cfba3fd24bd058660464ab5bee491bf3aef3d9f9217f09ba8b72649`；构建、安装和归档时均核对一致。
- 交付目录保存 `source-manifest.json`（950 个文件、17 个子模块）、`source-status.txt`、`build-receipt.json`、`delivery-state.json`。

## 验证边界

19 项大小写状态、目标命中和玻璃配色单元检查通过，生产界面用例编译通过；此前本批尺寸/反馈及悬浮检查分别通过 46 项和 49 项。完整 Release 构建、签名、应用身份、设备架构与 APK 哈希检查通过。日志在 `.codex-artifacts/shift-slide-20261004/`。

未运行完整尺寸门禁或设备仪器测试；本包交给用户实测，**交互与视觉效果未验收**。仅更新本批已安装条目的状态提示，未替用户勾选 Todo。
