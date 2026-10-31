# 仓颉五代方案

`cyime_cangjie` 的码表来自 **Jackchows/Cangjie5**（倉頡五代，朱邦復發明；由倉頡之友《五倉世紀》碼表按官方資料修訂，
倉頡之友·馬來西亞、倉頡五代補完計劃），许可证 MIT，全文见 `LICENSE-cangjie`。固定版本 `c9b64e77951cba48bdfa5de8eaedd1a908666b9b`。
CyIME Windows 用同一版本的同一张表。

- `cyime_cangjie.dict.yaml`：构建时由 `app/build-logic/tasks-cangjie.gradle.kts` 下载 `Cangjie5.txt` 并校验 SHA-256；
  去掉康熙部首、笔画符号、兼容汉字，扩展 B 起的字只留通用规范汉字表里的。编码本身未改。
  原表不带字频，按小麦注音 McBopomofo（MIT）提交 `18e2060dd5d0b511753ad79a8fe0d112e944f1af` 的 `Source/Data/phrase.occ`
  台湾语料计数与雾凇 8105 字频（按「的」折算到同一量级）取大者排序；2–5 字、计数不少于 5 的词（约 6 万条）不写编码，
  由 librime 按仓颉取首尾码的构词规则编码。
- `cyime_cangjie.schema.yaml` 参照 rime/rime-cangjie（LGPL-3.0）`cangjie5.schema.yaml` 的写法：编码显示成字根、
  x/z/yyy 码不进自动造词。去掉拼音反查与「常用/增廣」开关；简繁跟 CyIME 的「繁体输出」设置走（繁转简用 `opencc/cyime_t2s.json`）。

- `cyime_quick.dict.yaml`（仓颉速成）由同一任务从上面的单字码生成：取首尾两码，不另外下载。

来源：https://github.com/Jackchows/Cangjie5/tree/c9b64e77951cba48bdfa5de8eaedd1a908666b9b
