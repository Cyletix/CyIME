# 模型与插件盘点和整理方案（2026-10-15）

只查看、没改代码。依据：`gh release view models`、代码目录（含未提交改动）、平板 R52T800SKWZ（1.12.37）外部私有目录。手机 R5CW13EYCSF 当时没连电脑，没查。正式包不可调试，App 内部目录（已下载模型所在处）读不到，所以“设备上已下载了哪些”只能从界面看，这里没列。

## 一、结论

1. **xime 的市场已经不用了。** 方案、插件商店的地址早已换成本仓库 `store/`（`assets/xime.yaml:14-17`）。插件目录 `store/plugins/index.yaml` 是空的，所以商店“插件”页显示“暂无插件”；方案只有五笔86/98。
2. **但有几个模型仍从上游作者的魔搭账号 `bikeand` 下载**：手写模型 `ochwpro`（只有这一个地址）、两个智能联想模型（主地址魔搭，我们的 Release 只是备用）、中文 Zipformer 语音的备用地址。账号被删就下不到。
3. **你自己训练、只能 adb 推的私有模型，界面里完全看不到**：个人手写模型、公式识别模型都只放在 `Android/data/com.cyletix.cyime/files/handwriting_models/` 下，不在任何目录或页面里。
4. **“模型管理”页只列已下载的模型，只能删**；下载要去商店“模型”页、联想页、语音页，九键模型只在商店“候选增强”里。所以同一类模型散在 4 个地方。
5. **剪贴板 Windows 同步插件已经内置进 APK，开机自动装上，不需要传 Release。**

## 二、每个模型从哪来

| 类型 | 模型 | 来源 | 界面在哪 |
|---|---|---|---|
| 文字识别 OCR | PP-OCRv5、Tesseract | APK 内置 | 本地模型页顶部一张只读卡 |
| 联想（离线词表） | LCCC 联想数据 | APK 内置 | 不显示 |
| 智能联想 | predictive-text small / base | 主：魔搭 bikeand（上游）；备：我们 Release | 商店模型页、智能联想页 |
| 九键词组 | 完整 v3-c3（203 MB）/ 精简 v4（105 MB） | 我们 Release `models` | 商店“候选增强” |
| 九键语法增强 | RIME-LMDG（390 MB） | 第三方 GitHub（amzxyz） | 商店“候选增强” |
| 九键长句（已停用） | t9_sentence | 本仓库旧提交 raw 地址 | 商店，只为方便删除 |
| 中文语音 | Zipformer zh | 主：sherpa-onnx 官方 GitHub；备：魔搭 bikeand | 语音页、商店 |
| 日语/多语语音 | ReazonSpeech、Paraformer、SenseVoice | Hugging Face（国内走 hf-mirror） | 语音页、商店 |
| 手写（通用） | ochwpro | **只有**魔搭 bikeand（上游） | 商店 |
| 手写（个人） | `ochwpro-personal.onnx` 7 MB | 你自己训练，adb 推送，平板有 | **不显示** |
| 公式识别 | encoder/decoder 共 34 MB | 你自己训练，adb 推送，平板有 | **不显示** |
| 细胞词库 | 搜狗等 | 搜狗网站 + APK 内置几份 | 词库管理 |

平板上另有手写训练语料（`handwriting_training/*.tsv`）和语音调参录音（`speech_tuning/`），都是个人数据。

**隐私：** 个人手写、公式、个人九键学习、以上语料和录音，都不能上公开 Release。

## 三、Release 现在实际有什么

`models` 标签下 8 个文件：

- 智能联想：`predictive-text-small-v1.0…onnx`（19.7 MB）、`predictive-text-base-v1.0…onnx`（36.4 MB）、`predictive-text-vocab-v1.0.json`、整合包 `cyime-models-prediction-v1.0.zip`（56 MB）、`NOTICE-models.txt`
- 九键：`t9_ngram-v3-ls-tri-c3.lm`（213 MB，App 用）、`t9_ngram-v4-ls-p5e-8-c3.lm`（110 MB，App 用）、`t9_ngram-v3-ls-tri.lm`（368 MB，**App 已不再引用**，和 c3 内容相同）

问题：Release 说明只写了 368 MB 的旧文件，没写 c3、v4 两个实际在用的；九键模型的许可也没写清（应按 CC BY-NC-SA 注明）。其余版本标签是 App 安装包，和模型无关。

## 四、插件：哪些是你改过的

`plugins/` 下 14 个插件源码：

- **你自己做的**：`cyime-windows-sync`（Windows 剪贴板同步），已内置进 APK。
- **你改过的上游插件**：`typing-stats`、`webdav-backup`。
- **原样上游、没改过**：ai-reply、ai-translate、ai-write、funasr-asr、tencent-asr、volc-asr、kaomoji、meme-bunny、quick-send-demo、webdav-clipboard-sync、ximed-clipboard-sync。
- 除 Windows 同步外，**其他插件都没打包、也没上商店**，正式版只能自己导入 .xipk。

## 五、整理方案

### 1. 模型管理：一个页面，按类型列全部模型

- 页面改名统一叫“模型管理”（现在入口叫“模型管理”，页标题叫“本地模型”）。
- 按类型分组：九键词组、智能联想、语音（中文/日语）、手写、公式、文字识别、细胞词库入口。**没下载的也列出来**。
- 每个模型一行，写清：来源标签（内置 / CyIME / 第三方 / 个人·仅本机）、大小、是否在用；可以下载、切换版本、删除。
- 每个类型的具体设置（联想强度、语音二次校正、九键用完整版还是精简版）从这一组直接进入，不用再跑到“智能”里找。
- 个人模型（手写个人、公式）也显示在对应类型下，标“个人·仅本机”，支持从本机导入，不提供下载。
- 商店里的“模型”页去掉，或者直接跳到这里，保证只有一个地方管模型。

### 2. 手写训练并入模型管理

是的，建议放进去：在“手写”这一组下放“训练个人手写模型”，包括现在藏在“关于”页里的手写采集。主页“智能”组里的“手写训练模式”入口去掉。这些页面现在归“手写数据导出和训练”线程，等它收尾后再挪，避免两边同时改。

### 3. 本地模型页的版面

现在乱，是因为说明文字分散在三处：OCR 卡、线路卡，还有列表上面的红色警告；而且“下载线路”和“从本机导入”这两件不相关的事挤在一张卡里。改法：

- 上面放模型列表，按类型分组；OCR 只是“文字识别”组里的一行。
- 页面最下面放一个“下载设置”区，只放下载线路，说明写一次。
- 从本机导入放到每组里的“导入”按钮，或者右上角菜单。
- 红色“使用本地模型有代价”的警告改成一行灰字，放在真正相关的地方（比如大模型下载前的确认里）。

### 4. 插件页

- 和模型一样合成一页：上面“已安装”，下面“可安装（CyIME 商店）”，每个标来源（内置 / 商店 / 文件导入）。商店里的插件 Tab 合进来，不再两处。
- 剪贴板同步类插件只在“剪贴板同步”页管理，插件页里点进去也跳到那里。
- 上商店：把你改过的 `typing-stats`、`webdav-backup` 和确认能用的 `webdav-clipboard-sync` 打成 .xipk，放到新的 Release 标签 `plugins`，登记到 `store/plugins/index.yaml`。没改过的 AI、语音插件要先在 CyIME 上核对能不能用，再决定上不上。
- Windows 同步插件维持内置，不必再传 Release。以后想不发新版 App 就更新它，再一起放进商店。

### 5. 模型来源收归自己

- 手写 `ochwpro` 复制一份到我们的 Release 当备用，联想模型改为我们 Release 优先、魔搭备用，摆脱上游账号依赖。复制前先核对 ochwpro 的许可。
- 更新 `models` Release 说明：补上 c3、v4 两个文件和九键模型的许可。
- 删除不再使用的 368 MB 旧九键文件（删了不能恢复，等你同意）。

## 六、要你定的

1. 上面的页面结构（模型一页按类型列全、手写训练并入、插件一页）同不同意。
2. 插件商店先上哪几个：建议 typing-stats、webdav-backup、webdav-clipboard-sync。
3. Release 里 368 MB 的旧九键文件删不删。
