# 界面语言（简体 / 繁體 / English / 日本語）

2026-10-31 起步。用户反馈「有人看不懂 base 和 small」，顺带提出多语言用户：设置里加繁体、英文、日文界面。

## 已实现

- 选择：设置首页「系统」组里的「界面语言 · Language」，单选框：跟随系统、简体中文、繁體中文、English、日本語。语言名永远用各自的文字写。
- 机制：只用 Android 13 起系统自带的应用语言（`LocaleManager.applicationLocales`，`settings/AppLanguage.kt`）。系统保存，整个进程（设置页和输入法服务）一起生效；系统设置 › 应用 › CyIME › 语言 改的是同一个值（`res/xml/locales_config.xml`）。
- Android 12 及更早：没有应用语言，界面跟随系统语言，这一行点了打开系统语言设置。不自己包 Context 换语言：包出来的 Resources 配置固定，旋转后键盘拿到旧方向。
- 默认「跟随系统」。系统语言不是这四种时显示简体中文（`res/values` 是简体）。
- 文字：`res/values`（简体）、`values-b+zh+Hant`（繁體，zh-TW / HK / MO 都用它）、`values-en`、`values-ja`。新加文字四个一起加。
- 已翻译：设置首页全部入口、状态字（已设置、已连接、· 玻璃等）、「界面语言」本身、智能联想页的模型选择（名字、怎么选、已下载／使用／去商店），系统输入法列表里的「拼音」，通话字幕无障碍服务的名字和说明。
- 设置搜索仍按中文：翻译过的行用 `searchKey`（中文原名）定位，搜中文词照样能跳到。

## 暂不公开

翻译补齐前，「界面语言」一行只在开发者包（`DEV_TOOLS` 或 debug）里出现，公开包隐藏。系统语言是繁体、英文、日文的用户在公开包里也会自动看到已翻译的首页（跟随系统）。

## 还没做（下个版本）

- 其余设置页（约 2,900 条写在 Kotlin 里的中文，主要在 `ui/`、`ui/settings/`、`model/`、`clipboard/`、`speech/`）逐页搬进 `strings.xml` 并翻译，按页交付，每页四种语言一起。
- 键盘里的文字（菜单、剪贴板、提示条、工具栏名字）。
- 商店／模型管理里的模型名和说明（`BuiltinModelCatalog`、`SpeechModelCatalog` 是不带 Context 的目录，需要改成按 id 取资源）。
- 设置搜索的英文、日文关键词。
- 全部翻完后去掉「界面语言」的开发者包限制，公开。

## 检查

- 单测：`AppLanguageTest`（语言标签归类）。
- 渲染：`AppLanguageRenderTest`（`CYIME_RENDER_OUT=<目录>`）出设置首页四种语言各一张，看标题、说明有没有挤爆截断。原有 `SettingsHomeRenderTest`、`SettingsWidthRenderTest`、`SettingsHandwritingRenderTest` 加了 `zh-rCN`，Robolectric 默认 en-US，不加会渲染成英文。
