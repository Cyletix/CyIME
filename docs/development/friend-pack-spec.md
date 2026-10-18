# 朋友包要求

朋友包是给会用手机、年龄相仿的朋友装的 CyIME：和普通版一样好用，所有功能都在，只是模型全部打进包、装上不用下载。它**不是老人包**：没有老人包的大字、九键、改工具栏这些默认值。每次打朋友包都按本文核对，打包器检查能检查的部分，检查不过不出包。

用户于 2026-10-18 定下：不是所有人都想用日语，默认把日语关掉；字幕不要默认打开桌面独立图标；别的设置基本都用普通默认值。

## 怎么打

```
py -3 -B scripts\packager\cyime_packager.py build --preset 朋友包 --bump patch
```

预设在 `scripts/packager/config.json` 的“朋友包”：正式版、带离线模型（`-PbundleModels=true`，但不带 `-PelderEdition`）、九键用 `@best_public`、只出包不安装。

## 包里必须有（打包器检查）

| 项目 | 内容 |
|---|---|
| 九键词组模型 | 模型总览里的“当前最佳”**公开**模型（`@best_public`，现在是 v3）。用户的个人模型是用自己的聊天记录训的，只用于用户本人和家人的设备，朋友包不带；用户明确说要换成个人模型时，把预设改成 `@best_private` 并更新本节 |
| 语音 | Paraformer 中英流式＋ SenseVoice 校正 |
| 手写 | ochwpro |
| 联想 | predictive-text base 和 small |
| 声纹 | CAM++ 中文声纹模型（字幕分说话人） |
| 日语资源 | `assets/rime_japanese/` 保留，朋友想用可以自己在设置里打开 |

`assets/bundled-models/` 下是这六个目录：ochwpro、paraformer-zh-en-int8、predictive-text-base、predictive-text-small、sensevoice-multilingual-int8、speaker-campplus-zh。

## 包里不能有（打包器检查）

- 开发者功能：“手写训练模式”和“手写数据采集”入口不出现（`BuildConfig.DEV_TOOLS=false`，只有“装到我的设备”带）。手写输入本身照常（用户 2026-10-18 决定）。

- 私人模型或任何私人数据：九键模型的哈希等于 `@best_private` 时拒绝；录音、声纹、手写样本、用户词库一律不放。
- 模型总览里已淘汰的模型。

## 默认设置（与普通版相同）

| 项目 | 默认值 | 怎么保证 |
|---|---|---|
| 日语 | 关（语言管理里可打开） | 普通版默认启用的语言就是中文＋英文；朋友包不是老人包，日语资源和选项都在 |
| 字幕桌面图标 | 关 | 清单里 `CaptionLauncher` 是 `android:enabled="false"`，打包器检查；不走老人包的默认设置 |
| 其他 | 普通版默认值 | `BuildConfig.ELDER=false`，老人包专属行为（固定中文语音、去掉日语、大字九键等）全部不生效 |

字幕本身对所有版本都固定按中文识别。

## 每次打包后的核对

1. 打包器输出“朋友包检查通过”。
2. 模拟器（或朋友手机）全新安装：桌面只有 CyIME 一个图标，没有“字幕”；语言管理里日语存在但没打开；键盘是普通默认的样子。
3. 截图留档，写进 `docs/packaging.md` 的交付记录。
