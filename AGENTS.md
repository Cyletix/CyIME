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
- 未经明确要求，不提交 Git、不安装 APK。
- 不覆盖或还原用户未提交改动。
- 测试通过不等于真机验收；未真机验证必须标明“未验收”。

## 导航

- 贡献规则：[CONTRIBUTING.md](CONTRIBUTING.md)
- 插件开发：https://ime.ximei.me/plugins/PLUGIN_DEVELOPMENT_GUIDE
- Compose / Android UI：`.skills/compose-expert/SKILL.md`