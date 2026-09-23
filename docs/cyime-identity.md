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
