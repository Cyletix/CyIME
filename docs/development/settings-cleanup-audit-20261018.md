# 设置清查（2026-10-18）

只读清查结果，标出已经没用、鸡肋、重复或说明有误的设置。「建议」一栏等用户选择后再动手；删除设置时旧的偏好键只忽略不清空，不丢用户数据。路径相对 `app/src/main/java/com/kingzcheung/xime/`。

## 一、已经没用（建议删除）

| 项目 | 现状 | 证据 | 建议 |
|---|---|---|---|
| 备份插件（WebDAV 云备份） | 备份页已只做本地导出。装了备份插件也无法启用，插件列表永远显示「未使用」，点启用只会跳到本地备份页 | `ui/settings/PluginsSettingsScreen.kt:275-282`；`settings/SettingsPreferences.kt:944-950` 的 `backup_plugin_id` 无人写入；`plugin/ExtensionManager.kt:271-285` 无调用方 | 删除备份插件分支和 `backup_plugin_id` |
| 搜索词「云备份」 | 指向本地备份页 | `ui/settings/SettingsSearch.kt:68` | 改成「本地备份」 |
| 完整备份里的插件配置 | 文件浏览里的「完整备份」仍打包 `plugin_configs`、`plugins.xml`，而备份页说明不含插件 | `settings/RimeExportManager.kt:99`、`settings/BackupManager.kt:261` | 两处统一（建议都不含插件） |
| 自定义短语旧页面 | 没有任何入口，功能已在词库管理里 | `ui/settings/CustomPhraseScreen.kt`（无引用） | 删除文件 |
| 九键长句增强（已停用） | 方案不再使用，但商店仍能下载 | `ui/settings/MarketHubScreen.kt:443` | 商店里隐藏，只在模型管理里留删除 |
| 智能联想的模型仓库 | 仓库地址对话框不再显示，相关下载函数无人调用 | `viewmodel/SmartPredictionSettingsViewModel.kt:46,206,259` | 删除 `prediction_model_repo` 相关代码 |
| 无人读写的偏好键 | `compact_mode`、`installed_market_ids`（只写不读）、`layout_pref_*`、`rime_assets_version` 等 | `settings/SettingsPreferences.kt:220,232,373,741,908` | 删除访问函数，旧键忽略 |
| 菜单旧项「固定内容」 | 排列页看不到；只在旧菜单配置里残留，排列保存一次就消失 | `keyboard/ToolbarButton.kt:59,89-92`、`keyboard/QuickActionLayout.kt:17-21` | 菜单也映射成剪贴板，删除该项 |
| 手写「查字」状态 | `isHandwritingLookup` 从不为真，相关分支是死代码 | `ui/keyboard/KeyboardView.kt:494,519,619,642,1319` | 删除死分支 |
| xime.yaml 的 `metadata` 段 | 解析但没人读，`app_version: ">=2.5.0"`、`generator: Xime…` 都是上游遗留 | `assets/xime.yaml:1-7`、`settings/KeysConfigHelper.kt:399` | 删除或只留一行说明 |
| xime.yaml 的 `dynamic` 配色 | 已不再提供，存了也会被改成柔和蓝 | `assets/xime.yaml:21,62-67`、`settings/SettingsPreferences.kt:510-516` | 删除 |
| 14/17/18 键、日文的 `button_layout` | 只读 qwerty 与 qwerty_en 的 | `assets/xime.yaml:273,301,332,366`、`settings/KeysConfigHelper.kt:1254` | 删除 |

## 二、会误导或实际不起作用（建议修）

| 项目 | 问题 | 证据 | 建议 |
|---|---|---|---|
| 26 键「滑行输入」 | 光标模式为「键盘滑动」或中文 26 键时开关无效，但仍可拨动；键盘菜单里同名开关还显示为“已开” | `ui/settings/LayoutInputSettingsScreen.kt:47-53`、`ui/keyboard/KeyboardLayout.kt:307`、`ui/keyboard/LayoutGestureMenuItems.kt:13`、`ui/keyboard/KeyboardView.kt:585` | 键盘滑动时设置页隐藏此开关；菜单项在无效时不显示 |
| 键盘菜单「声音振动」 | 只关声音，振动照旧（写的是没人读的 `vibration_enabled`，振动看 `haptic_mode`） | `ui/keyboard/KeyboardView.kt:536-540`、`service/FeedbackManager.kt:111,149` | 改为切换 `haptic_mode` |
| 「保持引擎常驻」与性能页语音释放 | 两处写同一组键，互相覆盖；说明「空闲 60 秒释放」与性能档位矛盾 | `ui/settings/SpeechToTextSettingsScreen.kt:207-222`、`ui/settings/PerformanceSettingsScreen.kt:116-122`、`settings/SettingsPreferences.kt:633` | 合并到性能页一处 |
| 按键动效／键内炫光 | 按键效果页和性能页各一份 | `ui/settings/KeyEffectSettingsScreen.kt:339`、`ui/settings/PerformanceSettingsScreen.kt:110` | 只留按键效果页，性能页档位带出即可 |
| 振动时长／振幅 | 选「跟随系统」时隐藏，但旧值仍生效；支持触感原语的手机上时长无效 | `ui/settings/KeyEffectSettingsScreen.kt:202-330`、`service/FeedbackManager.kt:166-205` | 跟随系统时不使用自定义值 |
| 假名方向提示 | 说明写“按住时显示方向圆盘”，实际圆盘归「拂动时显示方向圆盘」管 | `ui/settings/InputExperienceSettings.kt:86-98` | 改说明 |
| 候选注释内容（拼音/释义） | 候选注释选「隐藏」时仍显示，还会触发释义下载 | `ui/settings/LayoutDisplaySettingsScreen.kt:190-240` | 隐藏时不显示此项 |
| 每页候选词数 | 说明“需部署才能生效”已过时；滑块只能 20–50 | `ui/settings/LayoutDisplaySettingsScreen.kt:337,342`、`service/ImeSchemaController.kt:442` | 删说明；确认下限 |
| 分体键盘 | 也作用于 14 键，但只在 26 键页 | `ui/settings/LayoutInputSettingsScreen.kt:55`、`ui/keyboard/KeyboardLayoutState.kt:127` | 九键与 14 键页也显示 |
| 交换上下角标 vs 反转上下滑方向 | 一个换数据，一个换手势，效果重叠 | `ui/settings/QwertySymbolEditor.kt:144`、`ui/settings/InputExperienceSettings.kt:54` | 保留反转，去掉交换按钮 |
| 侧栏「恢复默认」 | 有旧 `fixed_symbols` 时恢复成旧共享列表，不是真默认 | `ui/settings/SideSymbolEditor.kt:117`、`ui/keyboard/KeyboardInputPreferences.kt:185` | 恢复真默认，旧键忽略 |
| `shadow.elevation: 0.5` | 解析成整数失败，实际用 1 | `settings/KeysConfigHelper.kt:1013` | yaml 写 1 或改成小数 |
| 新装时深色模式 | 写死为深色，覆盖 xime.custom.yaml 的 `dark_mode` | `settings/SettingsPreferences.kt:505` | 用 yaml 默认值 |

## 三、九键模型

- 目录里只有完整版（203 MB）和精简版（105 MB）两个 n-gram 模型，二者写同一个 `t9_ngram.lm`，换另一个前必须先删。
- 旧的未压缩 n-gram（约 370 MB）不在目录里，但老版本装过的副本还在 `rime/t9_ngram.lm`。模型管理看不到它，存储页把它算进不可清理的「方案与词库」，它还会挡住新模型安装。建议加一次性迁移：校验不符时识别为旧模型，在模型管理中显示「旧版九键模型，可删除」。
- 「九键语法增强」390 MB 仍在加载。方案注释说有 n-gram 时会代替语法搭配，所以对已装 n-gram 的人可能多余，需要回放确认后再决定要不要下架。

## 四、已顺手改的文字

- 主题页去掉“跟随系统动态配色”的说明，把“键盘需重启输入法”改成立即生效（`ui/settings/ThemeSettingsScreen.kt:204,251`）。
- 关于页隐私说明去掉“云备份”（`ui/settings/AboutScreen.kt:519`）。
