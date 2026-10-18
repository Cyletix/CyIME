# 代码结构与商店梳理（2026-10-12）

只做了查看和方案，没有改代码、没有构建、没有创建 Release。下面的数字都是在 `D:/GitHub/CyIME` 当前工作区（含未提交改动）里实际数出来的；标“推断”的是没有直接证据的判断。

## 一、结论先说

1. **上游 xime 商店现在对 CyIME 完全没用。** 上游索引里 12 个方案、8 个插件全部要求“App ≥ 2.3”以上，CyIME 版本号是 1.x，所以在商店里全部显示成灰色的“需 App ≥ 2.x”，一个都装不了。能装的只有我们自己写死在代码里的五笔86、五笔98 两项。应该断开。
2. **自己的商店用 GitHub 就够了，不用建网站。** 现有下载代码已经支持“多个镜像地址依次尝试 + 文件哈希校验”，只要把索引地址换成自己仓库里的一个文件，大文件放 GitHub Release 即可，改动量小。
3. **代码整体已经拆得比较细**（`service/` 下有 72 个小文件），没有必要大重构。真正该拆的是 5 个超大文件；该剪的是上游商店那一套、停用的长句模型残留、重复的候选栏和浮窗预览。
4. **特殊逻辑大部分已经集中了**：方案 ID 判断主要在 `InputProfile.kt` 一处，密码框判断在 `service/EditorInfoClassifier.kt` 一处。剩下值得统一的是三套候选栏样式、玻璃效果在各键盘里的分支。

## 二、现在哪些东西是从外部下载的

| 东西 | 下载地址写在哪 | 来源 | 现状 |
|---|---|---|---|
| 方案商店列表 | `app/src/main/assets/xime.yaml` 第 13–17 行（4 个镜像），代码缺省值在 `settings/XimeIndexSource.kt:49`、`settings/KeysConfigHelper.kt:558` | 上游 `ximeiorg/xime-index` | 全部不兼容，只靠 `CuratedRimeSchemes.kt` 里的五笔86/98 撑着 |
| 插件商店列表 | 同上（`plugins/index.yaml`） | 上游 | 8 个插件全部不兼容 |
| 手写模型、两个智能联想模型 | `model/BuiltinModelCatalog.kt:9–22`，`settings/SettingsPreferences.kt:525` | 魔搭 `bikeand` 账号（推断是上游作者上传的） | 能用，但别人删了我们就下不到 |
| 中文语音 Zipformer | `model/BuiltinModelCatalog.kt:26`（GitHub sherpa-onnx 官方），`speech/AsrModelManager.kt:27`（魔搭 `bikeand`） | 同一个模型写了两个地址 | 能用 |
| Paraformer、SenseVoice、日语语音 | `speech/SpeechModelCatalog.kt` | HuggingFace，地址固定到具体提交 | 能用，国内可能慢 |
| 九键语法增强（390MB） | `model/CandidateModelCatalog.kt:11` | GitHub `amzxyz/RIME-LMDG` 的 Release | 能用，这就是“GitHub Release 当商店”的现成例子 |
| 搜狗细胞词库 24 个 | `settings/CellDictionaryCatalog.kt` | 搜狗官网 | 能用 |
| 主题 | 无，全部在包里 | — | 不依赖商店 |

## 三、自己的商店怎么做（GitHub 方式）

### 做法

- **目录文件**放在 CyIME 仓库里，例如 `store/rimes/index.yaml`、`store/plugins/index.yaml`、`store/models/index.yaml`，格式沿用上游现在这套（代码已经能解析），只是版本要求改成 CyIME 自己的版本号。
- **大文件**（九键完整模型约 370MB、自制词库包、插件包）放到 GitHub Release，按内容分开打标签，例如 `models-t9-ngram-v1`、`dicts-2026.10`。单个文件上限 2GB，370MB 没问题。每个文件在目录里写上大小和哈希，下载后校验。
- **App 里**把默认镜像换成：
  1. `https://cdn.jsdelivr.net/gh/Cyletix/CyIME@main/store/`（国内通常能打开）
  2. `https://raw.githubusercontent.com/Cyletix/CyIME/main/store/`
  
  目录文件小，走 jsDelivr；Release 里的大文件 jsDelivr 不转发，只能直连 GitHub，国内可能慢或断，这一点要接受，或以后再加镜像。
- **以后加新词库**（比如明日方舟词库更新）：改目录文件、往 Release 传文件即可，不用发新版 App。

### 分两步

1. **最小一步**：把上游镜像地址从默认值里去掉，换成上面两个自己的地址；目录里先放五笔86/98 和九键完整模型三项。九键完整模型也可以先照“九键语法增强”的写法直接写进 `CandidateModelCatalog.kt`，连目录文件都不用。
2. **第二步**：把现在写死在代码里的模型清单（`BuiltinModelCatalog`、`CandidateModelCatalog`、`CellDictionaryCatalog`、`CuratedRimeSchemes`）挪进目录文件，商店页只读一处。

### 要你先定的

- 上游的模型（手写、联想，在魔搭 `bikeand` 账号上）要不要复制一份到自己的 Release。复制前需要确认这些模型的许可证允许转发；不复制就继续用上游地址。
- 仓库里 `plugins/` 下已经有 14 个插件的源码（Lua 写的），其中只有 `cyime-windows-sync` 内置进了包。要不要把其中能用的打包放进自己的商店。
- 九键完整模型上传 Release 仍等你说“传”。

## 四、建议拆分的大文件

| 文件 | 行数 | 混在一起的内容 | 怎么拆 |
|---|---|---|---|
| `service/XimeInputMethodService.kt` | 3709 | 输入法生命周期、提交上屏、预测、语音、剪贴板、物理键盘 | 已经有 70 多个小文件承接了一部分，继续把物理键盘和语音的部分挪出去 |
| `ui/settings/MarketHubScreen.kt` | 2236 | 方案商店、插件商店、模型、细胞词库、本地导入 | 按标签页拆成 4 个文件；如果去掉上游商店，自然变小 |
| `service/ImeKeyRouter.kt` | 2012 | 所有按键的分发 | 按“触屏键盘 / 物理键盘 / 日语”分三个文件 |
| `ui/keyboard/KeyboardView.kt` | 1883 | 面板切换、候选栏、工具栏、悬浮和分体容器 | 把候选栏区和工具栏区拆出 |
| `settings/KeysConfigHelper.kt` | 1763 | 按键配置、商店地址、配色方案 | 商店地址和配色各自独立 |

建议：不专门安排一轮重构；以后哪个线程要改这些文件里的某块，就顺手把那块拆出去，避免和正在进行的界面、九键工作冲突。

## 五、可以剪掉的冗余

| 项 | 证据 | 建议 |
|---|---|---|
| 上游方案、插件商店 | 20 项全部不兼容 | 换成自己的目录（见第三节），上游解析代码保留复用 |
| 五笔86/98 重复 | `assets/rime/` 里已带 `wubi86.*`、`wubi98.*`，商店里又列了一遍下载 | 二选一：要么包里不带改成下载，要么商店里去掉 |
| 九键长句模型残留 | 已停用，模型不进 APK（`app/build.gradle.kts:172`），但 33.7MB 的 `t9_sentence.onnx` 仍在 Git 里，`RimeEngine.kt:1236` 和模型管理里仍有入口 | 等九键线程确认电脑回放不再对照它后，删掉代码入口和仓库里的模型文件 |
| 没人用的悬浮候选栏 | `ui/keyboard/FloatingCandidateBar.kt`（105 行）全项目只有定义、没有一处调用 | 直接删 |
| 同一个语音模型两个下载地址 | `BuiltinModelCatalog.kt:26` 与 `speech/AsrModelManager.kt:27` | 留一个 |
| “跟随系统动态配色”主题 | 你已决定拿掉；`ui/theme/DynamicThemes.kt`（107 行）和 `xime.yaml` 里的 `dynamic` 条目 | 界面线程正在处理，不重复安排 |

主题方面：代码里的内置配色现在只有 4 个（`ui/theme/KeyboardPalette.kt`），加上动态配色 1 个，没有大量残留的旧主题代码。

## 六、可以统一的特殊逻辑

- **候选栏不止一套**：触屏用 `CandidateBar.kt`（1101 行），物理键盘用 `HardwareKeyboardCandidateBar.kt`（276 行），另有 `InlineSuggestionView`（系统内联建议）和展开候选页 `CandidatePage.kt`。你之前说的“候选界面几种样式混着出现、有的不显示拼音”和这里有关。建议候选内容（拼音串、候选项、序号、注释）只写一份，各处只负责摆放位置。界面线程正在改物理键盘候选，适合由它一起做。
- **联想引擎**：`association/` 下有两个功能相近的 ONNX 封装（`OnnxAssociationEngine`、`NativeOnnxEngine`），可以合并成一个。九键线程正在做新的联想表，等它定型后再合，避免撞车。
- **模式判断**：九键、手写、日语假名主要靠 `isT9Schema`、`isHandwritingSchema` 这类真假开关判断，约 150 处，集中在 `ui/keyboard/KeyboardView.kt`、`service/ImeKeyRouter.kt`、`service/ImeSessionController.kt`。这是 `InputProfile` 还没覆盖到的主要部分，文档 `input-profile-architecture.md` 里也列为待迁移。适合随大文件拆分一起慢慢收。
- **按键预览浮窗有三套**：`KanaFlickPreview.kt`、`KeyFlickPreview.kt`、`SwipeBubble.kt`。你已定“浮窗都用日语那种圆形样式”，统一后可以合成一套。
- **玻璃效果分支**：全项目约 159 处判断，`KeyButton.kt` 一个文件就 28 处，各键盘布局里各有 5–12 处。建议把“玻璃下按键长什么样”收到主题层算好，键盘只拿结果用。你反馈的“玻璃下功能键没区分”“符号页不跟玻璃”这类问题，根源就是每个键盘各写一遍。
- **方案 ID 写死**：除 `InputProfile.kt` 本身外还有约 59 处，主要在 `settings/CyimeInputDefaults.kt`、`ChineseSchemas.kt`、`ui/keyboard/KeyboardLayoutScreen.kt`。数量不大，按项目规则以后新代码走 `InputProfile` 即可，不必专门清理。
- **密码框判断**：主要集中在 `service/EditorInfoClassifier.kt`，但输入法主文件、按键分发、无痕模式里还各有几处自己判断。以后碰到时改成统一调用它即可，不必专门清理。

## 七、那 3 个失败的单元测试

这次按要求没有跑测试，下面是读代码得出的原因（推断，未运行确认）。两处都是测试没跟上后来的改动，不是输入法本身的毛病。

- `RuntimePreferencesTest` 的 2 个：测试还认为“默认开语音二次纠错”。后来默认语音改成了 Paraformer（`model/DeviceDefaults.kt:53–61` 会把旧的默认值一律换成 Paraformer），Paraformer 不带二次纠错，所以结果是“关”，和测试期望对不上。改法是按现在的默认值更新测试期望。
- `ClipboardPastePredictionTest` 的 1 个（`nextRealTypedCommitStillSchedulesPrediction`）：正常打字上屏时，代码新加了一步读取“当前输入框是否只收按键”（`service/XimeInputMethodService.kt:3555` 的 `editorInputPolicy.directInput`，推断来自 10 月 11 日密码框修复），测试的模拟环境没准备这个值，于是中途报错。改法是在测试里补上这个值。

## 八、建议的顺序

1. 换掉上游商店地址，建自己的目录文件（小改动，需要你同意）。
2. 你说“传”之后，九键完整模型上传 Release，并在目录里登记。
3. 候选栏三合一、浮窗统一、玻璃分支收拢，并入界面线程现有任务。
4. 九键线程收尾后，清理长句模型残留。
5. 大文件拆分随后续改动顺手做，不单独开工。
