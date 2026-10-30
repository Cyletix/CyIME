# CyIME 中文方案

中文26键、小鹤双拼及共享词库来自 iDvel/rime-ice（GPL-3.0），固定版本 `9e66b0729083b37d217312294f6d516c8d7234be`。LICENSE/README 和词库原始注释一并保留；构建时校验上游归档 SHA-256，安装后无需联网下载。

CyIME 构建适配在 `app/build-logic/tasks-chinese.gradle.kts`：接入候选策略、相邻键纠错及布局预设，并在 `lua/corrector.lua` 中保留宿主请求的候选拼音，继续提供原有错音提示；显示与隐藏由键盘实际宽度及用户设置决定。派生的九键英文索引保留上游词条原文。本版不是完全未经修改的上游分发。

中文九键沿用 Xime 的原生 librime-t9 适配；中文14键源自本仓库 `docs/schemas_examples/pinyin_14jian.schema.yaml`。两者的词典引用改为 rime_ice，其余键位映射保留。市场同名方案和个人 .custom.yaml 优先，不覆盖用户修改。

来源：https://github.com/iDvel/rime-ice/tree/9e66b0729083b37d217312294f6d516c8d7234be

## 简繁转换表（opencc/）

`s2t.json`、`s2tw.json`、`s2hk.json` 和其中引用的 `.ocd2` 表来自 OpenCC（Apache-2.0），即 librime 子模块 `deps/opencc` 的提交 `e9f3bb3`，由同一份源码编出，未修改。`cyime_s2tw.json` 在 `s2tw.json` 后加一步 `CyimeTWPhrases.ocd2`，把 s2tw 选的字形换成台湾日常写法（臺→台、計劃→計畫、瞭解→了解），表的原文是 `tools/opencc/cyime_tw_phrases.txt`，按小麦注音 McBopomofo（MIT）`Source/Data/phrase.occ` 的词频挑出，提交 `18e2060dd5d0b511753ad79a8fe0d112e944f1af`。生成方法见 `tools/opencc/build_cyime_opencc.py`。

## 注音（cyime_zhuyin）

`cyime_zhuyin.schema.yaml` 的拼写运算（拼音音节改写成注音、再落到大千键位）照 rime/rime-bopomofo（LGPL-3.0）`zhuyin.yaml` 与 `bopomofo.schema.yaml` 的写法，按雾凇词库的 ü 写法和 CyIME 的键码改写。`cyime_tw.dict.yaml`（台湾词与台湾读音）和 `lua/cyime_zhuyin_tones.lua`（每个字可读的声调）由 `tools/zhuyin/build_cyime_tw.py` 从小麦注音 McBopomofo（MIT）`Source/Data` 的 `BPMFMappings.txt`、`phrase.occ`、`BPMFBase.txt` 生成，提交 `18e2060dd5d0b511753ad79a8fe0d112e944f1af`。
