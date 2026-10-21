# 释义评测脚本

量候选释义的覆盖和对错用，生成的结果放 `.codex-artifacts/`，不进仓库。用户自己的聊天只在电脑本地读，结果留在 `D:/CyIME-Data`。

- `measure.py`：九键回放每键前 5 个候选里有英文的比例，按长度分类。
- `sample_bar.py`：从回放候选里随机抽显示出来的释义，人工判对错。
- `typed_cov.py`、`sample_shown.py`：按一份“词<TAB>次数”的用词表量覆盖、按频率抽样（用户聊天的用词表只在本地）。
- `singles.py`：单字释义按常用度分档统计。
- `roundtrip.py`：中→英→中 往返能否回到原词。
- `derive.py`：与 App `CandidateGlossary.derived` 同规则（语气词、吗、数字加单位），量的时候用。
- `round1-sample.tsv`：手工判断第 1 轮的 100 个公开候选（回放语料）。
