# CyIME：命名与项目边界

决策日期：2026-09-23。正式产品名称：**CyIME**；维护者：Cyletix。

## 已实施

- 应用名称、输入法名称、设置/关于/引导文案、无障碍 Logo 标签、APK 名称和发布工作流统一为 CyIME。
- Gradle 工程名称为 CyIME，展示版本从 **0.1.0** 起独立编号，内部 versionCode 为 20260928（保持递增以兼容覆盖安装）。
- 保留 `com.kingzcheung.xime` applicationId、namespace、原签名配置、`xime.yaml` / `xime.custom.yaml` 文件名及插件接口，便于已有安装与配置延续。品牌独立不要求包标识同时迁移。
- 保留 Xime、Kor1（kingzcheung）、贡献者与第三方声明及 GPL-3.0 许可证。独立命名不表示从零开发，也不改变 fork 来源。
- 恢复 Obsidian 中的历史研究入口，并建立 CyIME 项目入口。

## 三份资产分别管理

| 资产 | 当前位置 | 角色与建议 |
| --- | --- | --- |
| 活跃 Android 应用 | `D:/GitHub/Xime CyletixFork/Xime`，远端 `Cyletix/Xime` | 产品命名已为 CyIME；推荐远端最终改名 `Cyletix/CyIME` |
| 旧 Android 原型 | `D:/GitHub/CyIME` | 已存在，README 标为 AndroidIMEApp，未检测到 Git 仓库；须保留，不覆盖 |
| 2024 布局研究 | `D:/OneDrive/Project/Cyletix-Keyboard`，远端 `Cyletix/Cyletix-Keyboard` | 保留原名、提交历史与实验来源，作为 CyIME 研究前身 |

## 目录及 GitHub 迁移方案（本轮未执行）

建议最终将活跃源码放在 `D:/GitHub/CyIME`。迁移前先把当前同名旧原型完整归档到一个不存在的带日期目录，核对文件清单/哈希，然后停止相关 Gradle/IDE 进程，移动活跃仓库并更新 Codex 项目路径、IDE、脚本及本地构建路径。不要合并两套源码或用新项目覆盖旧原型。当前会话继续使用原工作目录。

已查询 GitHub：`Cyletix/Xime` 是 `ximeiorg/Xime` 的 fork；当前账户查询未找到 `Cyletix/CyIME`。建议重命名现有 fork，而非创建空仓库丢失历史。建议仓库描述：`CyIME — 基于 Xime/Rime 的 Android 输入法；中日英输入、手写、语音与可选布局研究。`

远端重命名后再更新 origin、README、关于页、下载地址和发布入口，核对 CI、Release 与相关集成。目前保留真实可访问的 `Cyletix/Xime` 链接，不提前指向尚不存在的仓库。远端名称/描述未修改；本次 0.1.0 继续提交至既有 fork，见 [版本记录](cyime-0.1.0.md)。

## 验收边界

2026-09-23 的命名整理仅改变名称和提供可选布局配置，不改变默认字母排列。0.1.0 后续修复的范围和验证见 [版本记录](cyime-0.1.0.md)。新的 APK 构建和检查结果记录于 PROGRESS.md；新名称的真机安装显示与旧数据升级仍需设备验收。

## 仍待独立品牌发布处理

本轮完成名称统一，应用图标仍是继承的 Xime 图标，未设计新 Logo。仓库 [TRADEMARKS.md](../TRADEMARKS.md) 明确将图标等品牌资源与源码许可区分。当前构建用于已有自用 fork 的验证；公开发布 CyIME 前应替换启动图标和工具栏 Logo，并审查包标识迁移与现有用户数据/签名兼容方案。历史版权和商标声明保留，不把改名等同于完成对外发行准备。

## 2026-09-24 仓库名称确认

GitHub 当前返回仓库正式名称 `Cyletix/CyIME`，所有者仍为 Cyletix，仍为 `ximeiorg/Xime` 的 fork；原 `Cyletix/Xime` 地址会重定向。0.1.2 同步 origin、README 和关于页入口。上文记录保留当时尚未改名的历史状态。

## 2026-09-25 applicationId 迁移（对外身份独立）

决策：对外身份与内部命名分离——安装身份改为 `com.cyletix.cyime`，源码 namespace 仍为 `com.kingzcheung.xime`，以保留上游同步能力。

- 已改：`app/build.gradle.kts` 的 applicationId（namespace 不动）；`AndroidManifest.xml` 中本应用组件由相对名（`.MainActivity` 等）改为全限定类名，避免 applicationId 与 namespace 不同时相对名解析结果随 AGP 版本漂移；`scripts/install-release.ps1`、`scripts/clear-plugins.ps1|sh`、`app/build-logic/tasks-plugin-dev.gradle.kts` 里的主机包名（插件 ID 列表不动）；androidTest 的应用包名断言与输入法组件 id；README（三语）、fastlane 元数据、`.github` 模板、AGENTS/CONTRIBUTING/SECURITY 的对外名称与仓库链接；内置无线导入网页（`web/` 与其 `assets/www` 产物的标题与文案，`xime.custom.yaml` 字样保留）；`RimeExportManager` 导出文件名、`KeysConfigHelper` metadata 默认 app_name。
- 有意保留（数据兼容面 / 上游同步面）：namespace 与源码包路径、类名（`XimeApplication`、`XimeInputMethodService`、`XimeIndexSource` 等）、plugin-core 包名与插件 ID、`xime.yaml` / `xime.custom.yaml` 文件名、扩展市场与 `index` 协议、SharedPreferences（`kime_settings`）/ Room 数据库名 / Keystore 别名（`xime_plugin_config`）/ 手写样本格式号等持久化标识、日志与 wake-lock 标签、LICENSE 版权与上游署名。改动这些会丢失用户配置或显著增加后续合并冲突。
- 影响：Android 视为新应用，可与旧 `com.kingzcheung.xime` 安装并存；旧版数据与配置不自动继承，输入法需在系统设置中重新启用。输入法组件 id 现为 `com.cyletix.cyime/com.kingzcheung.xime.service.XimeInputMethodService`。
- 验证：`processDebugManifest` 产物 `package="com.cyletix.cyime"`，组件类名仍指向 `com.kingzcheung.xime.*`，FileProvider authority 自动变为 `com.cyletix.cyime.fileprovider`（与代码中的 `packageName + ".fileprovider"` 一致）；`compileDebugKotlin` / `compileDebugAndroidTestKotlin` 通过。真机安装显示名、输入法启用与升级行为待设备验收。
- 上游截图（`docs/Screenshot/*`）与 fastlane 的图标 / 商店截图已删除，占位说明见 `docs/Screenshot/README.md` 与 `fastlane/metadata/android/README.md`；应用启动图标仍是继承的 Xime 图标，待替换。