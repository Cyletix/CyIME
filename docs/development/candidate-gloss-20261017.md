# 候选中英释义（2026-10-17）

## 做了什么

- 设置「布局与显示 → 候选词」：原「候选拼音与编码」改名「候选注释」（显示／自动／隐藏不变）；新增「候选注释内容」拼音／释义二选一，默认拼音，偏好键 `candidate_comment_content`。
- 选释义时，中文键盘里的中文候选注释换成英文、英文候选换成中文；英文、日文键盘不受影响。查不到释义的候选保留原注释。
- 只改显示层：`KeyboardView`（候选栏、展开候选）和实体键盘候选（`XimeInputMethodService`）。**候选数据里的注释不能改**：九键选词用 `candidateComments` 里的拼音交给 C++ 定位候选（`ImeKeyRouter` 的 `candidatePinyin`），曾在数据层替换过一版，已撤回。
- 查表 `gloss/CandidateGlossary.kt`：词典文件内存映射，堆上只留每行起点（约 24 万条，约 1 MB）；后台线程加载，`onStartInputView` 和设置切换时按偏好加载或释放；选拼音时不加载。
- 词典是模型目录里的可选下载 `candidate-gloss`（`BuiltinModelCatalog`，分类「其他」），存放 `files/models/candidate-gloss/cyime_gloss.tsv`；不进 APK、不进离线完整版。

## 词典生成

```bash
python -I tools/dictionaries/glossary/build_glossary.py --ecdict ecdict.csv --cedict cedict_1_0_ts_utf-8_mdbg.txt.gz --output cyime_gloss.tsv
```

- 英→中：ECDICT（MIT），只收单词，且 frq/bnc/考试标签/柯林斯/牛津任一非空，约 5.8 万条；取义项最多的那一行词性的第一个释义（避免 good → 善行）。
- 中→英：CC-CEDICT（CC BY-SA 4.0），简繁两种键；同一写法多个读音时专名靠后、义项多的读音优先（东西 → thing）；去掉 variant/see/CL 等义项和开头的 “to ”。
- 文件开头写 ECDICT 的 MIT 全文与 CC-CEDICT 署名；整个文件按 CC BY-SA 4.0 分发。
- v1：5,541,245 字节，sha256 `f7b5d96b8e973d5462b71d0febcda08ea8e13a2935dedf1c3ecb52be95fc3617`。源文件和产物在本地 `.codex-artifacts/glossary-src`、`.codex-artifacts/glossary-out`。

## 发布

- 2026-10-18 用户同意后上传到 Release `models` 标签：`cyime_gloss-v1.tsv`，并在 `NOTICE-models.txt` 加第 5 节署名与哈希；从公开地址重新下载核对 sha256 一致。

## 验证

- 单元测试 `com.kingzcheung.xime.gloss.CandidateGlossaryTest` 4 个通过（JDK 21，--max-workers=4）；`compileDebugKotlin` 通过。
- 模拟器 `emulator-5560`（x86_64，1.14.0 debug）手动推入词典后核对：实体键盘候选「你好 hello」「拟好 ni hao」、「computer 电脑」；26 键候选栏与展开候选（你 you、泥 mud 等）；九键 64426 首选「你好 hello」，点选后正确上屏。设置页两张示意卡渲染正常。验证后恢复为自动＋拼音。
- 平板 R52T800SKWZ 已装 1.14.3（20261188，10-18 01:59 JST，统一打包）：从平板拉回 APK，dex 中含本功能字符串；平板前台是用户应用，未上机操作，未验收；九键回放未运行（本改动不涉及解码与排序）。

## 10-18 下载入口

- 用户反馈 1.14.7 选了释义后只看到“尚未下载”，找不到下载处（条目只在扩展商店模型「其他」里）。
- 新增 `ui/settings/GlossDictionaryStatus.kt`：选「释义」时词典未下载就自动开始下载；提示行带下载／重试按钮和百分比，下完立即 `CandidateGlossary.prepare`。下载走 `ModelManager.downloadModelInBackground`，离开页面不中断。
- 模拟器 emulator-5560（1.14.7 debug x86_64）：删掉本地词典后点「释义」，约 6 秒下完提示消失，打 nihao 首选「你好 hello」。
- 10-18 设置清理：候选注释选「隐藏」时「候选注释内容」整段不显示、不触发下载；注释隐藏时 `CandidateGlossary.prepare` 也释放词典。compileDebugKotlin 与 gloss 单测 4 个通过，未装机。

## 10-19 释义纠错（v2）

- 随机测试发现 几号 → heroin、在教 → be a believer、话 → dialect。原因：CC-CEDICT 的俚语义项（"(slang) heroin"）被去掉标签后照用；收了大量罕见词组（在教 是宗教义，候选里由 在+教 拼出）；读音和义项都不按常用排（没 取了 mo4 “drowned”，告诉 取了 “press charges”，年 吃到异体字 秊 的 “grain”）。
- 生成脚本按原因修：语域／粗口／字面直译义项不用；多字词只收 Rime 词库权重 ≥ 500 的词，且只用 Rime 最常用读音对应的条目；义项用 ECDICT 反查挑选；单字从严；繁简同形字不吃别的繁体字形的条目。抽查 Rime 最常用 300 字、800 词，错释义基本消失，剩下的是首义偏书面（心里 → chest 一类）。
- v2：中文 1.99 万条（v1 18.3 万），文件 1,547,732 字节，sha256 `4492a6b05b19918d9a3fd631ad04ad3eaa08e8a4e1c9b8bba370c0806429061c`。App 改读 `cyime_gloss-v2.tsv`，已选释义且只有 v1 的设备会自动下载 v2 并删掉 v1。v2 尚待上传 Release。

