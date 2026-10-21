# 重复、无效或做了一半的功能（2026-10-20）

只读排查，没改代码。和 10-18 的设置清理不重复。路径相对 `app/src/main/java/com/kingzcheung/xime/`。剪贴板相关的改动归剪贴板线程。

## 最重要的十条

| # | 用户在哪看到 | 实际情况 | 问题 | 建议 | 代码位置 |
|---|---|---|---|---|---|
| 1 | 剪贴板 › 长按条目 › 编辑，或「+」新增固定内容：「编码」框 | 编码存进数据库，但没有任何代码拿用户输入去匹配它；唯一用它的演示插件也拿不到数据 | 不起作用，和自定义短语功能重复 | 已按用户选择 A 处理（81df309f）：去掉编码框，旧编码不迁移，条目保留；转成自定义短语会让固定内容进候选、联想和备份，破坏隐私 | `ui/keyboard/QuickSendFormArea.kt:48,168`、`plugin/QuickSendHostApiImpl.kt:12`、`service/ImeKeyRouter.kt:454` |
| 2 | 词库管理 › 个人词库 | 读的是 `user_*.dict.yaml`，可应用从来不往里写；真正学到的词在 `*.userdb` | 几乎永远显示“暂无词条” | 改成读取真实的用户词库；做不到就删掉这个分页 | `settings/PersonalDictManager.kt:262-289` |
| 3 | 词库管理 › 自定义短语 | 顶部选方案，看起来每个方案一份；实际所有方案共用一个 `custom_phrase.txt`。新加短语后不触发部署，什么时候生效不确定 | 方案选择误导；对话框又叫「快捷短语」；空列表提示叫你“切到自定义短语标签页” | 这一页不显示方案选择；统一叫「自定义短语」；保存后立即让 Rime 生效；删掉没有入口的旧 `CustomPhraseScreen.kt` | `ui/settings/DictionarySettingsScreen.kt:279,318,356,369`、`viewmodel/CustomPhraseViewModel.kt:67-96` |
| 4 | 扩展商店 › 插件；插件管理「安装插件」；语音页、剪贴板同步页的「去市场」 | 商店插件目录是空的 | 所有安装插件的入口都通向空列表 | 上架现有插件（腾讯/火山/FunASR 语音、颜文字等），或在有插件前隐藏这些入口 | `store/plugins/index.yaml`、`ui/settings/PluginsSettingsScreen.kt:162` |
| 5 | 菜单里「悬浮」和「键盘调节」 | 两个入口都在默认菜单里，调节页里还有同一个悬浮按钮 | 重复 | 默认菜单去掉「悬浮」，已保存的菜单不动 | `keyboard/ToolbarButton.kt:80` |
| 6 | 文件管理器 › 导出 | 和「备份与恢复 › 导出备份」是同一份包；存到下载目录失败时临时文件被删，界面仍显示“导出完成” | 重复，且可能丢文件 | 删掉这个导出或只导出配置文件；失败时如实提示 | `settings/RimeExportManager.kt:54-63`、`ui/settings/RimeFileBrowserScreen.kt:294` |
| 7 | 插件管理里的表情、工具类插件 | 显示“已启用/未启用”，但任何地方都没有开关 | 默认关的插件永远打不开 | 在插件管理加整行开关，或去掉状态文字 | `ui/settings/PluginsSettingsScreen.kt:543-560` |
| 8 | 短信验证码条上的「原文」 | 打开剪贴板面板，但短信正文从没存过，验证码也不进历史 | 点了看不到原文 | 短信来源时不显示「原文」，或把短信原文暂存在内存里 | `ui/keyboard/CandidateBar.kt:811-824`、`clipboard/VerificationCodeSmsReceiver.kt:23-31` |
| 9 | 无痕模式下用表情、颜文字、符号 | 照样记进「最近使用」 | 无痕不彻底 | 记录前检查无痕状态 | `data/RecentUsageStore.kt`（`record`） |
| 10 | 短信验证码设置 | 藏在「剪贴板同步」页一个折叠按钮里 | 和同步无关，搜索到了也看不到 | 移到输入相关的设置页，直接展示 | `ui/settings/ClipboardSyncSettingsScreen.kt:196-198` |

## 其余

- **输入资源 › 已下载/导入** 与 **扩展商店 › 本地管理** 是同一个页面的两份拷贝；「方案市场」菜单又是扩展商店的第二个入口。建议只保留一处（`ui/settings/SchemaLocalScreen.kt`、`ui/settings/SchemaSettingsScreen.kt:463,518`）。
- **方案商店** 只有五笔 86/98，代码里写死一份、`store/rimes/index.yaml` 又写一份，而且可能 APK 已自带。建议只留一个来源，再决定上架新方案还是先隐藏（`settings/CuratedRimeSchemes.kt:58`）。未完全确认安装时会不会冲突。
- **词库管理** 顶栏的方案和「方案词库」分页的方案各选各的，经常对不上。建议共用一个选择（`viewmodel/PersonalDictViewModel.kt:20`、`ui/settings/SchemaDictBrowserScreen.kt:53`）。
- **剪贴板「更多」菜单**：「新增固定内容」和顶栏「+」重复，「关闭菜单」什么都不做（`ui/menubar/ClipboardBoardView.kt:464,475`）。归剪贴板线程。
- **拆词**：长按菜单叫「分词」，页面叫「拆词」；固定选中文字后返回会回到拆词页（`ui/menubar/ClipboardBoardView.kt:442`、`ui/keyboard/KeyboardView.kt:1846`，返回路径未完全确认）。归剪贴板线程。
- **拍照识别** 结果只能「保存」到剪贴板，不能直接上屏（`ocr/OcrReviewEditor.kt:10-16`）。可能是有意设计。
- **Windows 连接** 有两条路：扫码配对和内置的旧插件；页面自己说插件只为兼容旧版。建议隐藏旧插件（`clipboard/sync/WindowsDevices.kt`）。
- **「恢复以前屏蔽的候选」** 没有屏蔽内容时也显示，非九键方案还会被弹回第一页（`ui/settings/DictionarySettingsScreen.kt:83-85,149`）。
- **智能联想 › 用户学习数据**：「立即保存到本机」和「刷新统计」多余，后者什么也不做（`ui/settings/SmartPredictionSettingsScreen.kt:467-468`）。
- **颜文字「最近」为空** 时显示日文「まだ履歴がありません」（`ui/keyboard/KaomojiKeyboard.kt:55`）。
- **手写板提示** 说“在右上角菜单导入样本备份”，键盘上没有这个菜单，导入在设置 › 手写训练（`ui/keyboard/HandwritingKeyboardLayout.kt:255`）。
- **工具栏的全选/复制/粘贴/段首/段尾** 和编辑面板重复；默认不显示，可保留。
- **「手写」和「滑行输入」** 用同一个图标，英文 26 键菜单里分不清（`keyboard/ToolbarButton.kt`）。
- **关于 › 手写数据采集**（仅调试版）和手写训练模式重复，采集只存在内存里。
- **插件类别「预测」** 没有任何代码使用，装了也不起作用（`PluginCategory.PREDICTION`）。
- **没人用的代码**：`SettingsRoutes.SchemaDictBrowser`、`SettingsRoutes.MarketModel`、`RimeEngine.getCustomPhraseDictName`、`ui/keyboard/FloatingCandidateBar.kt`、`DocumentOcrActivity.open()`、`ClearClipboardConfirmOverlay`、`ClipboardView` 一批没用的参数。
- **需要真机确认**：编辑面板的撤销/重做靠发 Ctrl+Z，很多 App 可能没反应。
