# 笔画方案

笔画键盘使用的 `stroke` 方案和码表来自 **rime/rime-stroke**（五笔画，四季的風、雪齋、Kunki Chou、宋天），
许可证 LGPL-3.0，全文见 `LICENSE-stroke`。固定版本 `1e8fff9b9494ddec23b0cbc526bcfd8171a6fd48`。

本版有改动：
- `stroke.schema.yaml`：名称改为简体「笔画」；去掉依赖 luna_pinyin 的拼音反查；候选页数沿用全局设置；
  加入 `lua/cyime_stroke_wildcard.lua`（* 代替一笔）、`lua/cyime_stroke_rank.lua`（没打完的常用字按字频排在生僻字前）
  和 `lua/cyime_stroke_phrase.lua`（用分词键隔开、每个字只打头几笔时出词）。
- `stroke_phrase.dict.yaml`（CyIME 新增，构建时生成）：词和词频取自雾凇拼音 base 词库两到四字的常用词，
  编码是每个字的第一笔，笔顺取自上面的码表；由依赖方案 `stroke_phrase.schema.yaml` 编译，不单独出现在方案列表里。
- `stroke.dict.yaml`：构建时由 `app/build-logic/tasks-stroke.gradle.kts` 下载并校验 SHA-256
  `b3e93dce89c185f45c3d6e189b86b3a8626913352cc85e1094c786579a665791`，按雾凇 8105 字频加权重排序，
  去掉 8105 以外的增补平面字（扩展 B 起、西夏文）以及部首、笔画符号、兼容汉字。编码本身未改。

来源：https://github.com/rime/rime-stroke/tree/1e8fff9b9494ddec23b0cbc526bcfd8171a6fd48
