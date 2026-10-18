# 26 键简拼被邻键纠错挤掉（2026-10-17）

用户报告：26 键输入 `zdky`，首选“在都可以”，预期“这都可以”。

## 原因

- 词库里有 `这都可以 zhe dou ke yi`（雾凇 `cn_dicts/ext`），`zdky` 能整词匹配。不开纠错时首选就是它。
- 触屏打开邻键纠错（`cyime_neighbor_correction`）后，同样长度的纠错词（如 则可以、总动员）权重更高，排在整词桶最前。`ScriptTranslation::Evaluate` 只看桶首个词：它是纠错词，于是判定“没有可靠整词”，转去造句；补丁里的 `MakeExactSentence` 又把原拼写造出的句子放到最前，结果是单字词频拼出的“在都可以”。
- 同一原因还使 `hzp` 首选“或作品”（应为化妆品）、`zdh` 首选“制度或”（应为自动化）、`hyddj` 首选“活跃度等级”（应为还要等多久）。

## 修改

`DictEntryIterator::PreferCodes`：整词桶里存在原拼写（非纠错）的精确匹配时，这些词排在同长度纠错词之前，组内仍按权重。`Evaluate` 仅在“开纠错、整词桶首个是纠错词”时调用它，之后照旧判断是否造句。不开纠错、桶首本来就是原拼写、或整串没有原拼写整词（如 `nihso` → 你好）时行为不变。

文件：`librime/src/rime/dict/dictionary.h`、`dictionary.cc`、`gear/script_translator.cc`；`patches/cyime-librime.patch` 与 `app/src/main/jni/cmake/RimeDictionary.patch` 同步（两者 `git apply --reverse --check` 通过）。`tools/ime_lab/host_replay/PinyinCorrectionProbe.java` 增加 hzp、zdh 断言（主机引擎目前因 WSL 迁移缺 Java/CMake，未运行）。

## 验证（电脑端 Windows 引擎，同源 librime + 当前雾凇词库）

在 `D:/CyIME-Data/tmp/dict-zdky` 另编一份 CyIME-Windows 引擎（未动其 build 目录），ctypes 驱动，QWERTY 邻键表近似手机测得的邻键。用例 499 条：120 句日常全拼、74 句整句简拼、300 个常用三字以上词简拼、5 条纠错探针。

| 用例 | 改前（纠错开） | 改后（纠错开） | 改前/改后（纠错关） |
|---|---|---|---|
| 日常全拼 | 107/120 | 107/120 | 107 / 107 |
| 整句简拼 | 19/74 | 20/74 | 20 / 20 |
| 常用词简拼 | 295/300 | 297/300 | 297 / 297 |
| 纠错探针（含 nihso） | 3/5 | 3/5 | 4 / 4 |

纠错开时首选变化 5 处：hyddj、hzp、zdh 改对；slbsl 仍错（换了一个错词）；zdky 不再出“在都可以”，但首选成了“真的可以”，“这都可以”在第 9。纠错关时 499 条首选完全不变。

## 仍未解决：雾凇 ext 词库没有词频

`cn_dicts/ext.dict.yaml` 339,177 条全部是权重 100，`zdky` 的 8 个四字词（这都可以、真的可以、值得夸耀……）同分，谁排第一只取决于排序算法对同分项的任意次序。语料（九键词组模型 v3）里“真的 可以” ln P = −11.8，“这 都可以” −13.7，按语料“真的可以”更常见。要让同分词按真实用量排，需要给 ext 词条补语料词频，10-18 已补，见 [ext-weights-20261018.md](ext-weights-20261018.md)。

## 真机（2026-10-18，1.14.1 / 20261186，手机 R5CW13EYCSF）

在 Cyletix10 布局（与 26 键同一方案配置、同一引擎）逐键点按：zdky 首选“这都可以”，wxsl 首选“我先睡了”，hzp 首选“化妆品”。测完已把键盘切回用户原来的九键＋拼音。

## 14 键简拼只出大写字母（2026-10-18）

装机线程报告：14 键打 zdky 只出 ZDJT。电脑与手机均复现，规律是凡输入里有完整拼写（或整串只有合并键）时，大写合并键的简拼全部失效（Z、BUG、ZAD 失败，zD、BUg 正常）。

原因：雾凇 `rime_ice.dict.yaml` 有 26 个单字母词条（`A\tA` … `Z\tZ`），在词库里形成 A–Z 音节。14 键用大写字母 Q E T U O A D G J Z C B 表示合并键，这些音节的拼写与合并键编码同形，被当成“完整拼写”。Rime 的音节图在存在完整拼写路径时会剪掉同位置的简拼边（`Syllabifier::BuildSyllableGraph` 的 stale 清理），于是 Z 只剩字母 Z 本身。电脑端加临时日志确认：输入 `Z` 的边只剩 1 个音节，小写 `z` 有 37 个。

修改：`pinyin_14jian.schema.yaml` 与 `double_pinyin_flypy_14jian.schema.yaml` 的拼写运算第一条加 `erase/^[A-Z]$/`。26 键、九键方案文件未改。电脑端：ZDJT → 现代教育、直到今天…（14 键 z/x、d/f、j/k、t/y 同键，“这都可以”不在前 10），Z → 在/这/子，BUG → 不过/不会/不好，BUGAO 仍为不好/你好。需要重新打包后真机复核。
