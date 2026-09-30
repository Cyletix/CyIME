# CyIME

Android 输入法，基于 Rime，Kotlin + Jetpack Compose。

## 命令

- 构建：`./gradlew assembleDebug --quiet`
- 测试：`./gradlew test`
- 清插件：`./gradlew clearPlugins`
- 卸载：`./gradlew uninstallApp`

## 约束

- 仓库路径不能含空格。
- 禁止使用 `subst` 映射盘。
- 禁止 `./gradlew clean`。
- 每次git提交只提交一个功能修改。
- 最小修改，不顺手重构无关代码。
- 修改前先读相关实现。
- 修改后检查相关功能影响。
- 未经明确要求，不提交 Git。
- 每次开发修改完成后，生成 Debug 包，有设备连接 ADB 时通过 ADB 覆盖安装到连接设备（install -r），保留数据。
- 不覆盖或还原用户未提交改动。
- 测试通过不等于真机验收；未真机验证必须标明“未验收”。
- 尺寸验收是发包门禁：使用 `scripts/build-apk.ps1` 打包（连接模拟器/设备），必须通过 `scripts/verify-layout.ps1`；失败不得发包、发布 Release 或以旧测试报告代替。新增面板、弹窗和布局必须补入尺寸场景，检查文字真实高度、裁切、交互区域和极端尺寸。正式发布使用 `scripts/publish-release.ps1` 校验包与验收记录。

## 导航

- 贡献规则：[CONTRIBUTING.md](CONTRIBUTING.md)
- 插件开发：https://ime.ximei.me/plugins/PLUGIN_DEVELOPMENT_GUIDE
- Compose / Android UI：`.skills/compose-expert/SKILL.md`
