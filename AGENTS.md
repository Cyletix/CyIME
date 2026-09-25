# CyIME 输入法

## 项目简介
这是一个基于 rime 框架实现的安卓手机输入法，采用 kotlin + jetpack compose 构建。

## 快速开始
- 构建： `./gradlew assembleDebug --quiet`
- 测试： `./gradlew test`

## 构建环境（路径约束）
- 仓库路径**不能含空格**（例如 `Xime CyletixFork`）：原生 librime/Boost 构建对空格敏感，会在 CMake 编译器探测阶段失败。
- 不要用 `subst X: <仓库路径>` 之类的映射盘绕过：映射会让 CMake / Kotlin 缓存写入映射盘绝对路径（`X:\app\...`），
  即使之后删掉映射，构建仍会去碰 `X:\`（Kotlin 报 `different roots`、CMake 找不到 boost 源目录），
  表面现象就是"部署反复创建/使用 X 盘"。
- 已经用过映射的机器：先 `subst X: /D` 删映射，再删除 `app/.cxx`、`app/build/intermediates/cxx`、`app/build/kotlin`，然后在真实路径下重新构建。
- 推荐做法：把仓库放在不含空格的真实路径（例如 `D:\GitHub\CyIME`）。根 `build.gradle.kts` 检测到映射盘或含空格路径时会给出警告。

## 插件开发
- 清除插件数据： `./gradlew clearPlugins`
- 完全卸载主应用： `./gradlew uninstallApp`
- [插件开发指南](https://ime.ximei.me/plugins/PLUGIN_DEVELOPMENT_GUIDE) - 开发插件时必读

## 硬性规则（必须遵守，CI 会验证）
- 禁止自己提交代码
- PR 必须遵循最小修改原则
- 贡献前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)
- 禁止使用 `./gradlew clean`
- 必须使用中文回复
- 修改功能时，需要审查是否影响到其他功能。
- UI 要遵循material 3 设计
- 除非明确需要，否则不要自行安装apk
- 除非明确需要，否则不要自行提交git

## 工作规则
- 每次只做一个功能点
- 当前功能点端到端验证通过后，才能开始下一个
- 不要在实现功能 A 时"顺便"重构功能 B
- 当觉得有必要时，就添加单元测试


## 每次会话开始时（上班打卡）
1. 读 PROGRESS.md 了解当前状态
2. 读 DECISIONS.md 了解重要决策
3. 从 PROGRESS.md 的"下一步"部分继续工作

## 每次会话结束前（下班打卡）
1. 更新 PROGRESS.md
2. 跑 `./gradlew assembleDebug --quiet` 确认一致状态
3. 提交所有已完成的工作

## Jetpack Compose
For all Compose/Android UI tasks, follow the instructions in
`.skills/compose-expert/SKILL.md` and consult the reference
files in `.skills/compose-expert/references/` before answering.