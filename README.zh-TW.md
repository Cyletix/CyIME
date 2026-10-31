<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.en.md">English</a> · <a href="README.md">简体中文</a>
</p>

**CyIME** 是 Cyletix 開發維護的 Android 輸入法，基於 [Rime](https://rime.im/) 引擎，從 [Xime](https://github.com/ximeiorg/Xime) fork 而來。中文拼音、雙拼、五筆、筆畫、倉頡、注音，日語、英文、西班牙語，手寫、語音、拍照識字都在本機完成，另有給聽障人士用的實時字幕。

## 下載與安裝

要求 Android 9.0（API 28）及以上，安裝包只有 ARM64。

1. 在 [Releases](https://github.com/Cyletix/CyIME/releases) 下載安裝包，兩個包源碼相同，只差帶不帶離線模型：
   - **輕量版** `CyIME-<版本>-arm64-v8a.apk`：不帶語音、手寫、聯想模型，需要時在「擴展 › 模型」下載。
   - **完整版** `CyIME-<版本>-full-arm64-v8a.apk`：內置 SenseVoice 語音、手寫、聲紋、聯想模型和九鍵詞組模型，裝上就能離線用。邊說邊出字的 Paraformer 不內置，需要時在應用裡下載。
2. 安裝後在系統設置裡啟用 **CyIME**，並把它設為當前輸入法。
3. 以後在「設置 › 關於 › 檢查更新」更新，會下載和現在同一種（輕量／完整）的安裝包交給系統安裝。

> 帶谷歌服務的手機（三星、海外版機型等）從 2027 年起安裝未在谷歌登記的應用要先開一次「高級安裝」，國行手機和 adb 安裝不受影響，見 [谷歌開發者驗證與安裝](docs/android-developer-verification.md)。

> 從舊版 `com.kingzcheung.xime` 遷移：安裝身份已獨立為 `com.cyletix.cyime`，系統當作新應用，可以並存，舊版數據不會自動繼承。

## 功能

下面寫「默認關」的功能要在設置裡自己打開。

**中文**

- 全拼 26 鍵、九鍵、14 鍵，詞庫用霧凇拼音；九鍵可下載詞組模型和語法增強模型，長句逐鍵選詞。
- 雙拼：內置小鶴、首道、自然碼、智能ABC、拼音加加、微軟雙拼，可以複製修改或新建自己的方案；每個雙拼方案都有 14 鍵版本，小鶴、首道和自建方案另有九鍵版本（默認關）。
- 五筆 86（隨包）、五筆 98（可下載）、筆畫、倉頡五代、注音大千鍵盤，在「語言與方案」裡啟用。
- 繁體輸出（按臺灣用字，默認關）、模糊音（默認關）、生僻字（默認關）、中英混輸（默認關）、中文滑行輸入（默認關）。
- 候選可顯示拼音或中英釋義；26 鍵能糾正多打、少打的字母，糾錯候選不搶第一位。
- 詞庫頁：自己的短語、導入 TXT／搜狗／QQ／Rime 詞庫、刪掉的候選、查詞；帶動漫、遊戲、編程、數學等分類詞庫（默認關）。

**其他語言**

- 英文：整詞補全、滑行輸入。
- 日語：羅馬字 26 鍵和假名鍵盤，詞庫來自 rime-jaroomaji。
- 西班牙語：帶 ñ、長按出重音字母、詞表補全（默認關）。

**手寫、語音、識字**

- 手寫：中文、英文、日語（假名和漢字）、數學公式（識別成 LaTeX）；中文、英文、日語一次可以寫好幾個字，支持 S Pen。
- 語音：本地離線識別，新裝默認 SenseVoice（帶標點、整句定稿）；下載 Paraformer 後邊說邊出字；另可下載 Zipformer、X-ASR、日語模型。長按空格按住說話。也可以填自己的在線識別接口，測試通過才會用。
- 拍照識字：在剪貼板裡拍照或選圖，離線識別出文字。

**字幕**

- 把周圍的話實時轉成字幕，支持中文、英文、日語；自動分說話人，可以給說話人起名字。
- 後臺浮窗、通話字幕、字幕記錄（可搜索、分享，默認保留 3 天）、有人叫你名字時振動。桌面有單獨的「字幕」圖標。

**鍵盤與外觀**

- 固定、懸浮、分體三种放法；懸浮鍵盤可以拖動、縮放、旋轉；平板固定鍵盤可以從頂邊把手拉成懸浮。
- 字母排列：QWERTY、Colemak、QWJRTK、Cyletix10、Typewise 風格蜂窩六邊形，也可以自己排。
- 多套配色主題、玻璃效果、自己的圖片做背景，按鍵形狀可選圓角、方形、直角。
- 工具欄和菜單可以拖動排列；按鍵音、振動、滑動手勢可調。
- 實體鍵盤：浮動候選欄、快捷鍵切換語言。

**剪貼板與同步**

- 剪貼板歷史、固定、搜索、自定義分組，導出 TXT／Markdown／JSON；短信驗證碼在候選欄提示。
- 和 Windows 版 CyIME（還沒公開）掃碼配對後，兩邊剪貼板互相同步。
- 本地備份與恢復。

**設置**

- 設置搜索（認同義詞、錯別字、繁體字）、按設備推薦配置、性能檔位。
- 擴展頁：下載輸入方案和模型；導入 Lua 插件（`.xipk`），倉庫 `plugins/` 裡有顏文字、在線語音識別、WebDAV 備份等示例。

## 構建

```bash
# 克隆項目（含子模塊）
git clone --recursive https://github.com/Cyletix/CyIME.git

# 已克隆過則初始化子模塊
git submodule update --init --recursive

# 調試包 / 發佈包
./gradlew assembleDebug
./gradlew assembleRelease
```

需要 JDK 17 和 NDK `29.0.14206865`。構建時會聯網下載霧凇拼音、日語詞庫、筆畫、倉頡碼錶和 sherpa-onnx 運行庫，按記錄的 SHA-256 校驗後打進安裝包。日常出包用倉庫根目錄的 `packager.cmd`（雙擊打開圖形界面，帶參數是命令行），預設見 `scripts/packager/config.json`；版本規則見 [打包與版本規則](docs/packaging.md)。

技術棧：Kotlin · Jetpack Compose · Material 3 · librime（JNI / C++）· ONNX Runtime · sherpa-onnx · OpenCV

## 項目關係

- 源碼 fork 自 [Xime](https://github.com/ximeiorg/Xime)（Kingz Cheung / kingzcheung 及貢獻者），分叉點是 2026-09-21 的上游提交 `9452b16`。
- 為了方便對照上游，源碼包名 `com.kingzcheung.xime`、插件標識、`xime.yaml` / `xime.custom.yaml`、擴展商店協議都沒改，只有安裝身份獨立。取捨見 [命名與身份記錄](docs/cyime-identity.md)。
- 作者自己的前作：[Cyletix-Keyboard](https://github.com/Cyletix/Cyletix-Keyboard)（2024 年的鍵盤佈局研究，Cyletix10 佈局出自這裡）、ASRInput（電腦上的語音字幕，字幕逐字出現的節奏沿用它）。
- 社區 Linux 版：[tuxKOH/CyIME 的 linux-desktop-preview 分支](https://github.com/tuxKOH/CyIME/tree/feat/linux-desktop-preview)（IBus 拼音 + 電腦剪貼簿，tuxKOH 維護）。歡迎 Linux 版本的魔改，但由於本人精力有限，暫時不會維護 Linux 版本。

## 貢獻

見 [CONTRIBUTING.md](CONTRIBUTING.md)：先提 Issue，再提最小修改的 PR。

## 鳴謝

CyIME 用到了下面這些項目、數據和模型，感謝作者們的工作。安裝包裡的許可證全文和版權聲明見 `app/src/main/assets/NOTICE.txt`、`app/src/main/assets/licenses/` 和各方案目錄下的 NOTICE 文件；應用裡在「設置 › 關於 › 鳴謝」和「開源許可證」。

### 上游

| 項目 | 作者 | 用在哪裡 | 許可證 |
| --- | --- | --- | --- |
| [Xime](https://github.com/ximeiorg/Xime) | Kingz Cheung（kingzcheung）及貢獻者 | CyIME 的起點：界面框架、Rime 接入、九鍵 librime-t9、插件系統、手寫與聯想 | GPL-3.0 |
| [rime-wubi](https://github.com/kingzcheung/rime-wubi)（fork 自 [KyleBing/rime-wubi86-jidian](https://github.com/KyleBing/rime-wubi86-jidian)） | kingzcheung；KyleBing | 內置五筆 86 方案和手寫的五筆反查 | Apache-2.0 |
| Xime 模型：[ochwpro](https://www.modelscope.cn/models/bikeand/ochwpro)、[predictive-text-small](https://www.modelscope.cn/models/bikeand/predictive-text-small)、[predictive-text-base](https://www.modelscope.cn/models/bikeand/predictive-text-base) | Xime 項目 | 中文手寫識別；智能聯想 | ochwpro MIT；small Apache-2.0；base CC BY-NC-SA 4.0 |

### 輸入引擎

| 項目 | 作者 | 用在哪裡 | 許可證 |
| --- | --- | --- | --- |
| [librime](https://github.com/rime/librime)（經 [ximeiorg/librime](https://github.com/ximeiorg/librime)） | 佛振（lotem）及 Rime 社區 | 輸入引擎 | BSD-3-Clause |
| [librime-lua](https://github.com/hchunhui/librime-lua) | hchunhui | 方案裡的 Lua 腳本 | BSD-3-Clause |
| [librime-octagram](https://github.com/lotem/librime-octagram) | 佛振（lotem） | 九鍵語法模型 | GPL-3.0 |
| [librime-predict](https://github.com/rime/librime-predict) | Rime | 聯想 | BSD-3-Clause |
| [OpenCC](https://github.com/BYVoid/OpenCC) | BYVoid（Carbo Kuo） | 簡繁轉換 | Apache-2.0 |
| [Boost](https://www.boost.org)、[glog](https://github.com/google/glog)、[yaml-cpp](https://github.com/jbeder/yaml-cpp)、[LevelDB](https://github.com/google/leveldb)、[marisa-trie](https://github.com/s-yata/marisa-trie)、[Snappy](https://github.com/google/snappy) | 各自作者 | librime 依賴 | BSL-1.0、BSD-3-Clause、MIT、BSD-3-Clause、BSD-2-Clause 或 LGPL-2.1、BSD-3-Clause |

### 詞庫與碼錶

| 項目 | 作者 | 用在哪裡 | 許可證 |
| --- | --- | --- | --- |
| [霧凇拼音 rime-ice](https://github.com/iDvel/rime-ice) | iDvel（Dvel） | 全拼、小鶴雙拼、九鍵、14 鍵的詞庫和方案 | GPL-3.0 |
| [RIME-LMDG（萬象語言模型）](https://github.com/amzxyz/RIME-LMDG) | amzxyz | 九鍵語法增強模型（可下載） | CC BY 4.0 |
| [rime-stroke](https://github.com/rime/rime-stroke) | 四季的風、雪齋、Kunki Chou、宋天 | 筆畫方案 | LGPL-3.0 |
| [Cangjie5](https://github.com/Jackchows/Cangjie5) | Jackchows；倉頡之友·馬來西亞 | 倉頡五代碼表 | MIT |
| [rime-cangjie](https://github.com/rime/rime-cangjie)、[rime-bopomofo](https://github.com/rime/rime-bopomofo) | Rime | 倉頡、注音方案的寫法參照 | LGPL-3.0 |
| [小麥注音 McBopomofo](https://github.com/openvanilla/McBopomofo) | OpenVanilla | 臺灣詞、讀音和詞頻（注音、繁體輸出、倉頡排序） | MIT |
| [rime-jaroomaji](https://github.com/lazyfoxchan/rime-jaroomaji) | lazy fox chan | 日語方案和詞庫 | Apache-2.0；詞庫各自保留原許可：Mozc（BSD 風格）、JMdict / KANJIDIC2（EDRDG，CC BY-SA 4.0） |
| [搜狗細胞詞庫](https://pinyin.sogou.com/dict/) | 搜狗輸入法及詞庫作者 | 應用內下載的分類詞庫，文件直接從搜狗下載 | 版權歸原作者 |
| [rime-wubi98](https://github.com/cz-archive/rime-wubi98) | cz-archive | 五筆 98（應用內下載） | GPL-3.0 |
| [ECDICT](https://github.com/skywind3000/ECDICT) | skywind3000（Linwei） | 候選釋義：英→中 | MIT |
| [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) | MDBG | 候選釋義：中→英 | CC BY-SA 4.0 |
| [opus-mt-zh-en](https://huggingface.co/Helsinki-NLP/opus-mt-zh-en) | Helsinki-NLP | 釋義裡常見短語的參考譯文（在電腦上離線生成） | CC BY 4.0 |
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) | Hermit Dave | 西班牙語補全詞表 | 代碼 MIT；詞表 CC BY-SA 4.0 |
| [ChaiPUA 字體](https://github.com/tansongchen/input) | 譚淞宸（tansongchen） | 顯示五筆字根等擴展區字符（隨 Xime 帶入） | 字體內未標明 |

### 語料（訓練 CyIME 自己的模型）

| 語料 | 作者 | 用在哪裡 | 許可證 |
| --- | --- | --- | --- |
| [LCCC](https://github.com/thu-coai/CDial-GPT)（Large-scale Cleaned Chinese Conversation） | 清華大學 CoAI 小組 | 九鍵詞組模型、聯想底表、26 鍵常用詞詞頻 | MIT |
| [OpenSubtitles](https://opus.nlpl.eu/OpenSubtitles/corpus/version/OpenSubtitles)（OPUS） | opensubtitles.org；OPUS | 九鍵詞組模型的字幕口語部分 | 未標明 |
| [Natural Language Corpus Data](https://github.com/norvig/pytudes) | Peter Norvig | 英文滑行的詞頻和詞對頻率 | MIT |
| [Cornell Movie-Dialogs Corpus](https://www.cs.cornell.edu/~cristian/Cornell_Movie-Dialogs_Corpus.html) | Cristian Danescu-Niculescu-Mizil、Lillian Lee | 英文滑行的口語詞對 | 研究用公開數據 |
| [MathWriting](https://github.com/google-research/google-research/tree/master/mathwriting) | Google | 訓練公式手寫模型 | CC BY-NC-SA 4.0 |

### 語音、識字與推理

| 項目 | 作者 | 用在哪裡 | 許可證 |
| --- | --- | --- | --- |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | k2-fsa（新一代 Kaldi） | 語音識別、斷句、聲紋運行庫，以及各語音模型的 ONNX 導出 | Apache-2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | Microsoft | 手寫、聯想、識字等模型推理 | MIT |
| [Silero VAD](https://github.com/snakers4/silero-vad) | Silero | 語音斷句 | MIT |
| [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) | 阿里巴巴 FunAudioLLM | 語音識別（多語言、整句定稿） | FunASR 模型許可（需署名、保留模型名） |
| [Paraformer](https://github.com/modelscope/FunASR) | 阿里巴巴達摩院 FunASR | 中英流式語音識別 | Apache-2.0 |
| [Zipformer 中文](https://github.com/k2-fsa/sherpa-onnx) | k2-fsa | 中文流式語音識別 | Apache-2.0 |
| [X-ASR zh-en](https://huggingface.co/GilgameshWind/X-ASR-zh-en) | GilgameshWind | 中文整句定稿（可下載） | Apache-2.0 |
| [ReazonSpeech k2 v2](https://huggingface.co/reazon-research/reazonspeech-k2-v2) | Reazon Research | 日語語音識別 | Apache-2.0 |
| [3D-Speaker CAM++](https://github.com/modelscope/3D-Speaker) | 阿里巴巴 3D-Speaker | 字幕區分說話人 | Apache-2.0 |
| [PaddleOCR PP-OCRv5](https://github.com/PaddlePaddle/PaddleOCR)、[RapidOCR](https://github.com/RapidAI/RapidOCR) | PaddlePaddle；RapidAI | 拍照識字模型及其 ONNX 版本 | Apache-2.0 |
| [OpenCV](https://github.com/opencv/opencv) | OpenCV | 拍照識字的圖像處理 | Apache-2.0 |
| [KaTeX](https://github.com/KaTeX/KaTeX) | KaTeX 作者 | 顯示手寫公式 | MIT |
| [UER-py](https://github.com/dbiir/UER-py) 中文 RoBERTa | UER 團隊 | 九鍵整句打分（已停用，詞表仍在包裡） | Apache-2.0 |

### Android 庫

[Kotlin](https://kotlinlang.org)、[Jetpack Compose 與 AndroidX](https://developer.android.com/jetpack/androidx)（含 CameraX、Room）、[Material Components](https://github.com/material-components/material-components-android)、[OkHttp](https://github.com/square/okhttp)、[Coil](https://github.com/coil-kt/coil)、[kaml](https://github.com/charleskorn/kaml)、[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)、[ZXing](https://github.com/zxing/zxing)（配對二維碼）、[exp4j](https://github.com/fasseg/exp4j)（計算器）、[Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) 均為 Apache-2.0；[LuaJ](https://github.com/luaj/luaj)（插件運行時）MIT；[sora-editor](https://github.com/Rosemoe/sora-editor)（方案文件編輯器）LGPL-2.1。

### 設計參考

CyIME 的交互、外觀和鍵位參考過下面這些產品和方案：

- [三星鍵盤 Samsung Keyboard](https://www.samsung.com/)：鍵盤整體的交互和外觀。
- [Gboard](https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin)：鍵盤交互和外觀，比如大屏工具欄樣式。
- [Typewise](https://www.typewise.app/)：蜂窩六邊形佈局。
- [Colemak](https://colemak.com/)（Shai Coleman）：Colemak 字母排列。
- [首道雙拼](https://sspai.com/post/108949)：首道雙拼鍵位。
- 小鶴雙拼（[小鶴音形](https://flypy.cc/)）、自然碼、智能ABC、拼音加加、微軟雙拼：內置雙拼鍵位。
- [Trime 同文輸入法](https://github.com/osfans/trime)、[fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)：Android 上 Rime 輸入法的做法，用來對照。

## 許可證

[GPLv3](LICENSE)。上游代碼版權歸其作者所有，本分支的修改由 Cyletix 維護。"Xime" 名稱、Logo 及其他品牌資產**不屬於** GPLv3 授權範圍，詳見 [TRADEMARKS.md](TRADEMARKS.md)。第三方組件、數據和模型按各自的許可證分發，見上面的鳴謝和 `app/src/main/assets/NOTICE.txt`。注意智能聯想 base 模型（CC BY-NC-SA 4.0）和公式手寫模型（訓練數據 MathWriting 為 CC BY-NC-SA 4.0）不能用於商業用途。
