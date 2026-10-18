# 老人包要求

老人包是给听力不好的老人用的 CyIME：装上就能用，不用下载，界面简单，字大，字幕只出中文。每次打老人包都按本文逐条核对，打包器会自动检查能检查的部分，检查不过不出包。

用户于 2026-10-18 定下：老人不需要日语；字幕必须出中文；桌面要有单独的字幕图标；主题和输入方式按老人的习惯设好。

## 怎么打

```
py -3 -B scripts\packager\cyime_packager.py build --preset 老人包 --bump patch
```

预设在 `scripts/packager/config.json` 的“老人包”（旧名“离线完整版”仍指向它）：正式版、带离线模型（`-PbundleModels=true`，即 `BuildConfig.BUNDLED_MODELS`）、九键公开 v3 模型、只出包不安装。

## 包里必须有（打包器检查）

| 项目 | 内容 |
|---|---|
| 九键词组模型 | 公开 v3 模型 `t9_ngram-model-v3-ls-tri.c3.lm`（SHA256 以 `11cee2cbd87f42cb` 开头）。**绝不能放用户的个人模型或任何私人数据** |
| 语音 | Paraformer 中英流式（边说边出字）＋ SenseVoice（停顿后校正、加标点），只有这两个 |
| 手写 | ochwpro 手写模型 |
| 联想 | predictive-text base 和 small |

`assets/bundled-models/` 下只能是这五个目录：ochwpro、paraformer-zh-en-int8、predictive-text-base、predictive-text-small、sensevoice-multilingual-int8。

## 包里不能有（打包器检查）

- 日语：`assets/rime_japanese/`（jaroomaji 词库、日语和假名方案、日语 Lua）在构建时排除；`assets/` 下任何带 japanese、jaroomaji、kana、zipformer、`_ja` 的文件都算违规。
- 日语语音模型（Zipformer 日语）和其他不在上表的模型。

## 软件行为（代码保证，全部只在老人包里生效）

- 字幕：SenseVoice 固定按中文识别，不再“自动识别语言”（原因：口音重时被判成日语，2026-10-18 用户实测出日语）。这一条对所有版本的字幕都生效，字幕本来就只做中文。
- 键盘语音输入：中文键盘的 SenseVoice 也固定中文；英文键盘不变。
- 语言设置里没有日语，日语方案和假名布局都不出现（`InputLanguage.supported` 只有中文、英文）。

## 新装后的默认设置（只对全新安装生效，已有设置不改）

| 项目 | 默认值 | 理由 |
|---|---|---|
| 桌面“字幕”图标 | 开 | 老人点一下就进字幕页，不用找菜单 |
| 输入方式 | 九键拼音 | 国内老人最常用；不会拼音的点工具栏“手写” |
| 工具栏 | 语音、手写、字幕、拍照识别、表情、剪贴板、输入方式 | 常用功能一步可达；去掉光标编辑这类精细操作 |
| 主题 | 黑白（高对比） | 键帽与底色对比最强，字最清楚 |
| 候选字大小 | 24（普通版 19） | 看得清 |
| 键盘高度 | 屏幕长边的 40%（普通版 35%） | 键更大，不容易按错 |
| 语音模型 | Paraformer 流式＋SenseVoice 校正 | 两个都内置，装上就能用 |

实现：`DeviceDefaults.applyElderDefaults`。

## 每次打包后的核对

1. 打包器输出“老人包检查通过”。
2. 在模拟器（或老人手机）上全新安装：桌面有“字幕”图标；打开键盘是九键、黑白主题、字大；工具栏有字幕和拍照识别。
3. 打开字幕页说一段普通话，出的是中文。
4. 设置 → 语言里没有日语。
5. 截图留档，写进 `docs/packaging.md` 的交付记录。没有截图不能写“已核对”。
