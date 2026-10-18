# 方言二次校正模型（2026-10-18）

目的：中文语音停顿后的第二遍（目前是 SenseVoice）可以改选两种更耐方言的模型，供用户在设备上试用后再决定保留哪一个。流式 Paraformer 不变，SenseVoice 仍是默认。

## 模型

| ID | 来源（sherpa-onnx 官方 int8 导出，固定提交） | 文件 | 下载大小 |
|---|---|---|---|
| `dolphin-base-ctc-int8` | `csukuangfj/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02@1f3a53d0` | `model.int8.onnx` + `tokens.txt` | 104.2 MB |
| `telespeech-ctc-int8` | `csukuangfj/sherpa-onnx-telespeech-ctc-int8-zh-2024-06-04@d6ae7e61` | `model.int8.onnx` + `tokens.txt` | 341.1 MB |

只按需下载，`scripts/prepare-bundled-models.py` 的 `INCLUDED` 未加入，不进任何安装包。哈希与 Hugging Face LFS 记录一致（本地下载复核）。

## 电脑测量

`tools/speech_lab/compare_models.py --models sense-zh dolphin-base telespeech`，FLEURS zh 冻结清单 32 段（346 秒），CPU 双线程，每个模型单独进程。结果目录 `D:/CyIME-Data/speech-lab/dialect-compare-20261018`。RSS 是整个 Python 进程，含运行库，不等于手机内存。

| 模型 | CER | 去掉含数字/英文的句子后 CER | 中位耗时 | P95 | RTF | 加载 | 峰值 RSS |
|---|---|---|---|---|---|---|---|
| SenseVoice（zh） | 7.4 % | 4.4 % | 0.74 s | 1.24 s | 0.072 | 1.47 s | 389 MiB |
| Dolphin base | 16.1 % | 9.4 % | 0.37 s | 0.68 s | 0.036 | 0.33 s | 472 MiB |
| TeleSpeech | 22.9 % | 14.1 % | 0.61 s | 1.47 s | 0.071 | 1.99 s | 841 MiB |

FLEURS 是普通话朗读，不代表方言表现。两种新模型都不加标点（Dolphin 偶有逗号句号），数字写成汉字（“二零一一年”），所以整体 CER 偏高一部分来自格式。

方言样例（模型仓库自带 test_wavs，四川/天津/河南各一段），文字质量大体：Dolphin ≈ SenseVoice（zh 锁定）> TeleSpeech；TeleSpeech 漏字较多。

Dolphin 的地区/方言标签：sherpa-onnx 1.13.8 的结果里 `lang` 为空，拿不到标签，因此只在代码注释记录，不做任何行为。

## 规则

- 只对中文生效；方言模式不支持英文、日语，任何检测结果都不会自动切换识别器。
- 输出只保留中文：结果里出现假名、韩文、泰文等其他文字时丢弃，保留流式结果（`DialectCorrection.hasForeignScript`）。
- 存储为一个模式值 `主模型+校正模型`（如 `paraformer-zh-en-int8+dolphin-base-ctc-int8`），沿用现有把模式显式传给 `:asr` 进程的通道；原有 SenseVoice 模式值不变。

## 状态

- 设置入口：语音设置 → 离线模型 → 二次校正 → 中文开关下方“中文校正模型”，单选 SenseVoice / Dolphin / TeleSpeech，未下载的可直接下载；选中即开启中文二次校正。换主模型时保留所选方言校正。
- 接入：`SpeechModelCatalog`（下载清单）、`SpeechModelSelection`（`corrector`/`withCorrector`）、`SpeechLanguages`（方言模式只支持中文）、`AsrModelManager.selection`（`Selection.second`）、`SherpaSpeechEngine`（按 `second` 建识别器，方言结果走 `DialectCorrection.clean`）、`OfflineModelCard`。中文过滤由字幕线程在 `keepChinese` 统一使用 `hasForeignScript`。字幕模式仍固定用 SenseVoice。
- 检查：`:app:testDebugUnitTest`（DialectCorrection 9、SpeechModelSelection 4、SpeechLanguages 8，全部通过），`:app:assembleDebug` 通过。
- 未在手机或模拟器上测量，未安装，未真机验收。

## 河南话视频复测（2026-10-18）

用户提供的 39 秒教学视频（虚拟主播变声，代表性有限；音频与逐段结果在 `D:/CyIME-Data/tmp/dialect-henan/`，不入库）。按画面字幕的 13 个方言说法计，写对：Paraformer 2、SenseVoice 5、Dolphin 0、TeleSpeech 1；作参考的 Fun-ASR-Nano 0.8B 6、Qwen3-ASR 0.6B 6（每段 1.4–5.1 s，两者同进程 2.9 GB）。洋车儿、擤、洋柿、芫荽、仨馍无一写对。

流程无误：1.14.25 已含方言校正；中文过滤未丢结果；超时会报错清段而不是退回流式文字。差在模型本身。

决定：不加大模型（用户：手机性能跑不动）。等用户换一段真人方言视频再测，再决定 Dolphin／TeleSpeech 去留。

## 河南农村大爷真人视频（2026-10-19）

70 秒真人访谈（无变声），VAD 切 19 段共 61 秒，结果在 `D:/CyIME-Data/tmp/dialect-henan-p1/`。参考文字取画面字幕，字幕是整理过的普通话（如口语“拍/谝”写成“聊”），CER 只作相对比较。外文结果按产品规则换回流式文字后计算。

| 模型 | CER | 外文段 | 每段中位耗时（≥3 s 段） |
|---|---|---|---|
| Paraformer（流式参考） | 29.7 % | 0 | — |
| SenseVoice | 21.2 % | 0 | 0.27 s |
| Dolphin | 34.5 % | 2（俄文、韩文） | 0.15 s |
| TeleSpeech | 45.4 % | 0 | 0.24 s |

方言/口语词（庄上、种地、经商、当兵、腔调、准不准、意音、脉络、地方言、爷们、看来、大致）中 SenseVoice 写对最多，Dolphin、TeleSpeech 都不如它；“庄上、种地、意音、脉络、地方言”四个模型都没写对。Dolphin 在不到 1 秒的短段会输出俄文、韩文。

## 结论：撤下（2026-10-19）

两段河南话视频都是 SenseVoice 最好，Dolphin、TeleSpeech 没有一项胜出，Dolphin 还会在短段输出外文，因此撤下这两个选项，中文二次校正只保留 SenseVoice。

- 删除 `DialectCorrection.kt`、下载清单、选择模式、设置里的“中文校正模型”行和识别器分支；外文过滤 `hasForeignScript` 移到 `LocalSpeechSession.kt`，字幕和听写照旧使用。
- 已选 Dolphin／TeleSpeech 的用户（存为 `主模型+模型`）在下次读取时改回同一主模型 + SenseVoice 校正，同时删除手机上已下载的这两个模型文件（`SpeechModelSelection.withoutRetiredCorrector`）。
- 电脑上的测试模型副本已删；音频和结果保留在 `D:/CyIME-Data/tmp/dialect-henan*`。
