<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.md">简体中文</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

**CyIME** is an Android input method developed and maintained by Cyletix. It runs on the [Rime](https://rime.im/) engine and is a fork of [Xime](https://github.com/ximeiorg/Xime). Chinese Pinyin, Shuangpin, Wubi, stroke, Cangjie and Zhuyin, plus Japanese, English and Spanish, handwriting, voice and photo text recognition all run on the device. It also has live captions for people who are hard of hearing.

## Download and install

Requires Android 9.0 (API 28) or later. Packages are ARM64 only.

1. Download from [Releases](https://github.com/Cyletix/CyIME/releases). Both packages are built from the same source and differ only in the bundled offline models:
   - **Lite** `CyIME-<version>-arm64-v8a.apk`: no voice, handwriting or prediction models; download them in "Extensions › Models" when needed.
   - **Full** `CyIME-<version>-full-arm64-v8a.apk`: bundles the SenseVoice voice model, handwriting, speaker, prediction and 9-key phrase models, so it works offline right away. The streaming Paraformer model is not bundled; download it in the app if you want text while you speak.
2. Enable **CyIME** in the system settings and make it the current keyboard.
3. Later updates: "Settings › About › Check for updates" downloads the same edition (lite or full) and hands it to the system installer.

> On phones with Google services (Samsung, global models), apps from developers not registered with Google need a one-time "advanced flow" to install from 2027. Phones sold in mainland China and adb installs are not affected. See [Google developer verification](docs/android-developer-verification.md) (Chinese).

> Moving from the old `com.kingzcheung.xime`: the install ID is now `com.cyletix.cyime`, so Android treats CyIME as a new app. Both can be installed side by side, and old data is not carried over.

## Features

Features marked "off by default" have to be turned on in the settings.

**Chinese**

- Full Pinyin on 26 keys, 9 keys and 14 keys, with the rime-ice dictionary. The 9-key keyboard can download a phrase model and a grammar model and picks words key by key in long sentences.
- Shuangpin: built-in Xiaohe, Shoudao, Ziranma, Zhineng ABC, Pinyin Jiajia and Microsoft layouts. Copy and edit them or build your own. Every Shuangpin scheme has a 14-key version; Xiaohe, Shoudao and your own schemes also have a 9-key version (off by default).
- Wubi 86 (bundled), Wubi 98 (download), stroke, Cangjie 5 and Zhuyin (Dachen keyboard), enabled in "Languages and schemes".
- Traditional Chinese output (Taiwan forms), fuzzy Pinyin, rare characters, mixed Chinese-English input and Chinese glide typing, all off by default.
- Candidates can show Pinyin or a Chinese/English gloss. The 26-key keyboard corrects extra or missing letters without putting the correction first.
- Dictionary page: your own phrases, import TXT / Sogou / QQ / Rime dictionaries, removed candidates, word lookup. Topic dictionaries for anime, games, programming, math and more (off by default).

**Other languages**

- English: word completion and glide typing.
- Japanese: romaji on 26 keys and a kana keyboard, with the rime-jaroomaji dictionaries.
- Spanish: ñ key, accented letters on long press, word completion (off by default).

**Handwriting, voice, text recognition**

- Handwriting: Chinese, English, Japanese (kana and kanji) and math formulas (recognized as LaTeX). Chinese, English and Japanese accept several characters at once. S Pen is supported.
- Voice: offline on the device. New installs use SenseVoice (punctuation, whole-sentence results). Download Paraformer to see text while you speak; Zipformer, X-ASR and Japanese models are also available. Hold the space bar to talk. You can also set your own online recognition service, which is used only after its test passes.
- Photo text recognition: take a photo or pick an image from the clipboard panel and get the text, offline.

**Captions**

- Turns speech around you into live captions in Chinese, English or Japanese, split by speaker; speakers can be named.
- Floating captions over other apps, call captions, caption history (search, share, kept 3 days by default), vibration when someone says your name. Captions have their own launcher icon.

**Keyboard and look**

- Docked, floating or split. The floating keyboard can be moved, resized and rotated; on tablets, pull the handle on top of the docked keyboard to make it float.
- Letter layouts: QWERTY, Colemak, QWJRTK, Cyletix10, a Typewise-style hexagon layout, or your own.
- Color themes, glass effect, your own picture as background, rounded / square / sharp key shapes.
- Rearrange the toolbar and menu by dragging; key sounds, vibration and swipe gestures are adjustable.
- Hardware keyboards: floating candidate bar, shortcut to switch languages.

**Clipboard and sync**

- Clipboard history, pinning, search, custom groups, export to TXT / Markdown / JSON; SMS codes show up in the candidate bar.
- Pair with CyIME for Windows (not public yet) by QR code to sync the clipboard both ways.
- Local backup and restore.

**Settings**

- Settings search (synonyms, typos, Traditional characters), device-based recommended setup, performance levels.
- Extensions: download schemes and models; import Lua plugins (`.xipk`). The repo's `plugins/` folder has examples such as kaomoji, online speech recognition and WebDAV backup.

## Build

```bash
# Clone with submodules
git clone --recursive https://github.com/Cyletix/CyIME.git

# Or, in an existing clone
git submodule update --init --recursive

# Debug / release
./gradlew assembleDebug
./gradlew assembleRelease
```

Needs JDK 17 and NDK `29.0.14206865`. The build downloads rime-ice, the Japanese dictionaries, the stroke and Cangjie tables and the sherpa-onnx runtime, checks them against recorded SHA-256 digests and packs them into the APK. Day-to-day packages are built with `packager.cmd` in the repo root (double-click for the GUI, or pass arguments); presets are in `scripts/packager/config.json`, version rules in [docs/packaging.md](docs/packaging.md) (Chinese).

Tech stack: Kotlin · Jetpack Compose · Material 3 · librime (JNI / C++) · ONNX Runtime · sherpa-onnx · OpenCV

## Project history

- Forked from [Xime](https://github.com/ximeiorg/Xime) (Kingz Cheung / kingzcheung and contributors) at upstream commit `9452b16` on 2026-09-21.
- To keep the code easy to compare with upstream, the source namespace `com.kingzcheung.xime`, plugin IDs, `xime.yaml` / `xime.custom.yaml` and the extension store protocol are unchanged; only the install ID is CyIME's own. See [docs/cyime-identity.md](docs/cyime-identity.md) (Chinese).
- The author's earlier work: [Cyletix-Keyboard](https://github.com/Cyletix/Cyletix-Keyboard) (2024 keyboard layout research, where the Cyletix10 layout comes from) and ASRInput (desktop voice captions; CyIME's caption pacing follows it).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) (Chinese): open an issue first, then a minimal pull request.

## Credits

CyIME builds on the projects, data and models below. Thanks to all their authors. Full license texts and copyright notices ship in `app/src/main/assets/NOTICE.txt`, `app/src/main/assets/licenses/` and the NOTICE files next to each scheme. In the app, see "Settings › About › Credits" and "Open source licenses".

### Upstream

| Project | Author | Used for | License |
| --- | --- | --- | --- |
| [Xime](https://github.com/ximeiorg/Xime) | Kingz Cheung (kingzcheung) and contributors | CyIME's starting point: app framework, Rime integration, 9-key librime-t9, plugin system, handwriting and prediction | GPL-3.0 |
| [rime-wubi](https://github.com/kingzcheung/rime-wubi) (fork of [KyleBing/rime-wubi86-jidian](https://github.com/KyleBing/rime-wubi86-jidian)) | kingzcheung; KyleBing | Bundled Wubi 86 scheme and Wubi lookup in handwriting | Apache-2.0 |
| Xime models: [ochwpro](https://www.modelscope.cn/models/bikeand/ochwpro), [predictive-text-small](https://www.modelscope.cn/models/bikeand/predictive-text-small), [predictive-text-base](https://www.modelscope.cn/models/bikeand/predictive-text-base) | Xime project | Chinese handwriting; prediction | ochwpro MIT; small Apache-2.0; base CC BY-NC-SA 4.0 |

### Input engine

| Project | Author | Used for | License |
| --- | --- | --- | --- |
| [librime](https://github.com/rime/librime) (via [ximeiorg/librime](https://github.com/ximeiorg/librime)) | lotem (佛振) and the Rime community | Input engine | BSD-3-Clause |
| [librime-lua](https://github.com/hchunhui/librime-lua) | hchunhui | Lua scripts in schemes | BSD-3-Clause |
| [librime-octagram](https://github.com/lotem/librime-octagram) | lotem | 9-key grammar model | GPL-3.0 |
| [librime-predict](https://github.com/rime/librime-predict) | Rime | Prediction | BSD-3-Clause |
| [OpenCC](https://github.com/BYVoid/OpenCC) | BYVoid (Carbo Kuo) | Simplified/Traditional conversion | Apache-2.0 |
| [Boost](https://www.boost.org), [glog](https://github.com/google/glog), [yaml-cpp](https://github.com/jbeder/yaml-cpp), [LevelDB](https://github.com/google/leveldb), [marisa-trie](https://github.com/s-yata/marisa-trie), [Snappy](https://github.com/google/snappy) | Their authors | librime dependencies | BSL-1.0, BSD-3-Clause, MIT, BSD-3-Clause, BSD-2-Clause or LGPL-2.1, BSD-3-Clause |

### Dictionaries and tables

| Project | Author | Used for | License |
| --- | --- | --- | --- |
| [rime-ice (雾凇拼音)](https://github.com/iDvel/rime-ice) | iDvel (Dvel) | Dictionary and schemes for full Pinyin, Xiaohe Shuangpin, 9 keys, 14 keys | GPL-3.0 |
| [RIME-LMDG (万象)](https://github.com/amzxyz/RIME-LMDG) | amzxyz | 9-key grammar model (download) | CC BY 4.0 |
| [rime-stroke](https://github.com/rime/rime-stroke) | 四季的風, 雪齋, Kunki Chou, 宋天 | Stroke scheme | LGPL-3.0 |
| [Cangjie5](https://github.com/Jackchows/Cangjie5) | Jackchows; 倉頡之友·馬來西亞 | Cangjie 5 table | MIT |
| [rime-cangjie](https://github.com/rime/rime-cangjie), [rime-bopomofo](https://github.com/rime/rime-bopomofo) | Rime | Reference for the Cangjie and Zhuyin schemes | LGPL-3.0 |
| [McBopomofo (小麦注音)](https://github.com/openvanilla/McBopomofo) | OpenVanilla | Taiwan words, readings and frequencies (Zhuyin, Traditional output, Cangjie ranking) | MIT |
| [rime-jaroomaji](https://github.com/lazyfoxchan/rime-jaroomaji) | lazy fox chan | Japanese schemes and dictionaries | Apache-2.0; dictionaries keep their own licenses: Mozc (BSD-style), JMdict / KANJIDIC2 (EDRDG, CC BY-SA 4.0) |
| [Sogou cell dictionaries](https://pinyin.sogou.com/dict/) | Sogou and dictionary authors | Topic dictionaries downloaded in the app straight from Sogou | Copyright of their authors |
| [rime-wubi98](https://github.com/cz-archive/rime-wubi98) | cz-archive | Wubi 98 (download) | GPL-3.0 |
| [ECDICT](https://github.com/skywind3000/ECDICT) | skywind3000 (Linwei) | Candidate gloss, English → Chinese | MIT |
| [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) | MDBG | Candidate gloss, Chinese → English | CC BY-SA 4.0 |
| [opus-mt-zh-en](https://huggingface.co/Helsinki-NLP/opus-mt-zh-en) | Helsinki-NLP | Reference translations of common phrases in the gloss (generated offline on a PC) | CC BY 4.0 |
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) | Hermit Dave | Spanish word list | Code MIT; lists CC BY-SA 4.0 |
| [ChaiPUA font](https://github.com/tansongchen/input) | Tan Songchen (tansongchen) | Shows Wubi radicals and other extension characters (inherited from Xime) | Not stated in the font |

### Corpora (for CyIME's own models)

| Corpus | Author | Used for | License |
| --- | --- | --- | --- |
| [LCCC](https://github.com/thu-coai/CDial-GPT) (Large-scale Cleaned Chinese Conversation) | Tsinghua University CoAI group | 9-key phrase model, base prediction table, 26-key word frequencies | MIT |
| [OpenSubtitles](https://opus.nlpl.eu/OpenSubtitles/corpus/version/OpenSubtitles) (OPUS) | opensubtitles.org; OPUS | Spoken part of the 9-key phrase model | Not stated |
| [Natural Language Corpus Data](https://github.com/norvig/pytudes) | Peter Norvig | English glide word and word-pair frequencies | MIT |
| [Cornell Movie-Dialogs Corpus](https://www.cs.cornell.edu/~cristian/Cornell_Movie-Dialogs_Corpus.html) | Cristian Danescu-Niculescu-Mizil, Lillian Lee | Spoken word pairs for English glide | Public research data |
| [MathWriting](https://github.com/google-research/google-research/tree/master/mathwriting) | Google | Training the formula handwriting model | CC BY-NC-SA 4.0 |

### Speech, recognition and inference

| Project | Author | Used for | License |
| --- | --- | --- | --- |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | k2-fsa (Next-gen Kaldi) | Speech recognition, VAD and speaker runtime; ONNX exports of the voice models | Apache-2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | Microsoft | Inference for handwriting, prediction, text recognition | MIT |
| [Silero VAD](https://github.com/snakers4/silero-vad) | Silero | Voice activity detection | MIT |
| [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) | Alibaba FunAudioLLM | Speech recognition (multilingual, whole sentences) | FunASR model license (attribution, keep the model name) |
| [Paraformer](https://github.com/modelscope/FunASR) | Alibaba DAMO Academy FunASR | Streaming Chinese-English recognition | Apache-2.0 |
| [Zipformer Chinese](https://github.com/k2-fsa/sherpa-onnx) | k2-fsa | Streaming Chinese recognition | Apache-2.0 |
| [X-ASR zh-en](https://huggingface.co/GilgameshWind/X-ASR-zh-en) | GilgameshWind | Chinese whole-sentence results (download) | Apache-2.0 |
| [ReazonSpeech k2 v2](https://huggingface.co/reazon-research/reazonspeech-k2-v2) | Reazon Research | Japanese recognition | Apache-2.0 |
| [3D-Speaker CAM++](https://github.com/modelscope/3D-Speaker) | Alibaba 3D-Speaker | Telling speakers apart in captions | Apache-2.0 |
| [PaddleOCR PP-OCRv5](https://github.com/PaddlePaddle/PaddleOCR), [RapidOCR](https://github.com/RapidAI/RapidOCR) | PaddlePaddle; RapidAI | Text recognition models and their ONNX versions | Apache-2.0 |
| [OpenCV](https://github.com/opencv/opencv) | OpenCV | Image processing for text recognition | Apache-2.0 |
| [KaTeX](https://github.com/KaTeX/KaTeX) | KaTeX authors | Rendering handwritten formulas | MIT |
| [UER-py](https://github.com/dbiir/UER-py) Chinese RoBERTa | UER team | 9-key sentence scoring (retired; the vocabulary still ships) | Apache-2.0 |

### Android libraries

[Kotlin](https://kotlinlang.org), [Jetpack Compose and AndroidX](https://developer.android.com/jetpack/androidx) (including CameraX and Room), [Material Components](https://github.com/material-components/material-components-android), [OkHttp](https://github.com/square/okhttp), [Coil](https://github.com/coil-kt/coil), [kaml](https://github.com/charleskorn/kaml), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), [ZXing](https://github.com/zxing/zxing) (pairing QR codes), [exp4j](https://github.com/fasseg/exp4j) (calculator) and [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) are Apache-2.0; [LuaJ](https://github.com/luaj/luaj) (plugin runtime) is MIT; [sora-editor](https://github.com/Rosemoe/sora-editor) (scheme file editor) is LGPL-2.1.

### Design references

CyIME's interaction, look and key layouts drew on these products and schemes:

- [Samsung Keyboard](https://www.samsung.com/): overall keyboard interaction and look.
- [Gboard](https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin): keyboard interaction and look, such as the large-screen toolbar.
- [Typewise](https://www.typewise.app/): hexagon layout.
- [Colemak](https://colemak.com/) (Shai Coleman): the Colemak letter layout.
- [Shoudao Shuangpin (首道双拼)](https://sspai.com/post/108949): the Shoudao key map.
- Xiaohe ([flypy.cc](https://flypy.cc/)), Ziranma, Zhineng ABC, Pinyin Jiajia and Microsoft Shuangpin: the built-in Shuangpin key maps.
- [Trime](https://github.com/osfans/trime) and [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android): how other Rime-based Android input methods do things, used for comparison.

## License

[GPLv3](LICENSE). Upstream code remains copyright of its authors; the changes in this fork are maintained by Cyletix. The "Xime" name, logo and other brand assets are **not** covered by the GPLv3; see [TRADEMARKS.md](TRADEMARKS.md). Third-party components, data and models are distributed under their own licenses, listed in the credits above and in `app/src/main/assets/NOTICE.txt`. Note that the base prediction model (CC BY-NC-SA 4.0) and the formula handwriting model (trained on MathWriting, CC BY-NC-SA 4.0) may not be used commercially.
