# 九键词组概率模型交接（2026-10-10）

接手人先读本页，再看[本轮记录 1.12.0 一节](split-themes-quick-actions-20261009.md)（结果与速度门禁全文）和[打包记录](../packaging.md)。

## 1. 用户要的是什么

- 九键选词把“词库权重、语法搭配、每词扣分”合成**一种分数**：每个词都是 ln P(词 | 前面一两个词)，全部来自同一份语料计数。用户原话的要点：一切都转成权重，语法只是证据更多、更不易变的权重；**不要为个别句子加特殊规则**，那是“庸俗和愚蠢的解决方案”。
- 长期要能加入新语料、学习用户习惯，而**不失衡、不遗忘基本语法**（底座模型不被学习改写，用户层只做有上限的叠加）。
- 打长句时**只推荐一条整句**，后面是正常的短词（Rime＋雾凇的形状）。
- 测试纪律：不往词库或权重里注入测试句/答案；参数只在调参集（`gen/tune`、LCCC `dev`）上选，固定测试集只做最终检验。
- 用户允许继续做“有价值的实验”；不要几小时的多代理扇出（见记忆 feedback-no-heavy-agent-fanout）。

## 2. 当前交付状态

- 手机 R5CW13EYCSF 已装 **1.12.0 / 20261123**（APK 313681194 字节，SHA256 `dbf4857a…32a4`，版本与哈希已读回核对）。**用户未验收**；安装时手机停在通知栏，没打开键盘看过。首次打开键盘会复制约 370MB 模型并重新部署一次。平板仍是 1.11.1。未公开发布。
- **模型不在源码树里**：这次是把 `~/cyime-ngram/model-v3-ls-tri.lm`（WSL，367598664 字节，SHA256 前缀 `766c21385a6d3e5a`）手工复制成 `app/src/main/assets/rime_chinese/t9_ngram.lm` 打包，打包后已删除。**不带模型构建的包会自动回到旧算法**——下次打包前必须先解决（见 §6 第 3 条），或照同样方法临时复制并在打包后删除、永不提交。
- 工作树有 531 处未提交改动，横跨 1.7.x–1.12.0 多轮（按用户规则不自动提交，也不要还原用户改动）。本工作相关的文件见 §3。
- 台账 `D:/OneDrive/Notebook/Project/CyletixKeyboard/CyIME输入法 todo.md` 已更新到 1.12.0（三条：四音节整词计分、统一词组概率、长句只给一条），均未勾选。

## 3. 代码现状

引擎（`app/src/main/jni`）：
- 新文件：`librime-t9/src/t9_ngram_format.h`（v2 “CYT9NGR2”）、`t9_ngram_model.{h,cc}`（内存映射；`Id/Start/LogProb/LogProb3/Decompose/Chain`）。资源类型 `{"t9_ngram","",".lm"}`——不能用 `.bin`，Rime 启动清理会把用户目录的 `*.bin` 移进回收站。
- `cmake/T9JointDecoder.inc`：有模型时组句打分＝词组概率＋拼写扣分；最终排序可加句尾概率；`restore` 里按 `t9/display_sentences` 截断整句条数。
- `cmake/T9PhraseBeforeSentence.cmake`：读方案参数、解析模型路径。**注意**：`PinyinSyllableSelection.cmake` 的锚点依赖本文件生成的构造函数签名，改签名会让配置失败（这次踩过，最后改为复用它加的 `context` 参数）。
- `librime_jni/rime_jni.cc`：新增 `nativeSetT9LeftContext(String?)` → 会话属性 `cyime_left_context`（值为 `"\x1e"+文字`，null/空串＝未知）。**App 的 Kotlin 还没有调用它**。
- 修复 1（四音节以上整词计分）在 `RimeDictionary.patch` 与 `patches/cyime-librime.patch` 两处，二者必须保持一致（Gradle `applyCyimeSourcePatches` 会检查）。

**装机之后新增、未进 APK 的改动**（开关默认关，已验证与装机版逐键候选完全一致：253/253 个事件相同）：
- `t9/ngram_merge_segmentations`（默认 false）：同一串字、同一拼写的不同分词按概率相加（log-sum-exp），保留较好分词作后文历史。动机：词库里有“那你／那你还／这也”这类短语，同一句话的概率被拆散到多种切法，取最大值会让错句以极小差距胜出（如“那你还来不来” −23.22 对 “么你还来不来” −23.16）。
- 左侧上文：组句根节点从 `cyime_left_context` 取最后两个模型词作历史；上文为空或以非汉字结尾＝句首（与训练的汉字串一致）；未知时保持原逻辑。

方案 `app/src/main/assets/rime_chinese/t9_pinyin.schema.yaml` 当前值：`joint_decoder: true`、`display_sentences: 1`、`dictionary_beam: 16`、`ngram_model: t9_ngram`、`ngram_sentence_start: false`、`ngram_eos_weight: 0`、`ngram_merge_segmentations: false`。实验脚本会临时改这些值并在结束时恢复——**脚本运行中不要打包、不要改引擎源码**。

App：`app/build.gradle.kts` 版本 1.12.0 / 20261123（下一轮兼容修复升 1.12.1，versionCode 继续递增）；长句模型 `t9_sentence.onnx` 两种包都不再带。

## 4. 数据、模型与工具

- WSL `~/cyime-ngram/`：`vocab.tsv`（九键词库 187 万词）、`train.txt`（LCCC，Han 串）、`subs-s.txt`（字幕转简体）、`valid*.txt`；模型 `model-v3-ls-tri.lm`（采用）、`model-v3-{l-bi,l-tri,ls-bi}.lm`、剪枝版 `model-v4-ls-p{1e-8,5e-8,2e-7}.lm`、加权版 `model-v5-w12.lm`（560MB）/`w21`（918MB，均未评测）；构建器二进制 `ngram_build3/4/5`。
- 语料许可：LCCC-base 为 MIT；**OpenSubtitles（OPUS）未写明许可**，正式发布前须替换或确认。
- 工具 `tools/ime_lab/ngram/`：`prepare_data.py`（corpus/text/vocab/dev）、`ngram_build.cc`、`ngram_query.py`（逐项打印对数概率，排查最有用）、`compare_runs.py`、`category_report.py`、`gen_tests.py`（DeepSeek 生成测试句；密钥运行时从 `~/.cline/data/secrets.json` 读，绝不打印或保存）、`PinyinReplay.java`（26 键全拼对照）、`ChunkReplay.java`＋`chunk_cases.py`（左侧上文实验）、`eval_runs.sh`、`final_eval.sh`（全部测试集＋速度门禁会话）、`tune_next.sh`（见 §5）。
- 回放：`python3 tools/ime_lab/t9_replay.py run --fixture … --output … --json-jar …`，在 WSL 里 `nice -n 19` 跑；可选资源根 `app/build/generated/t9-ngram/rime_chinese/t9_ngram.lm`（现放着三元组模型，回放会读它；不进 APK）。
- 结果目录 `.codex-artifacts/t9-ngram-20261010/`：`final/`（装机版全部测试集与速度门禁）、`rime/`（Rime 原生）、`eval/`（原算法与二元版）、`v3/`、`tune/`、`gen/`、`dev/`、`anota/`、`chunk/`（分段用例）、`next/`（调参，见下）。

## 5. 进行中的实验（已停，未完成）

`tools/ime_lab/ngram/tune_next.sh` 在调参集上依次跑：基准、分词合并、句尾 0.3/0.6/1.0、句首概率、`dictionary_beam` 64、字幕加权 w12/w21，最后用基准运行的引擎跑 `ChunkReplay`（整句无上文 vs 句首上文；后半句无上文 vs 前半句为上文）。交接时只跑完 `base:tune`（逐字 585/991、整段 109/156，与先前三元组完全一致），其余未跑。脚本自包含，重跑：

```bash
wsl.exe -e bash -lc "cd /mnt/d/GitHub/CyIME && bash tools/ime_lab/ngram/tune_next.sh > /tmp/tune_next.log 2>&1"
```

约 1.5 小时；结果在 `next/runs.log`，每次运行结束删掉可再生的 `native-run`/`resources.zip`（基准运行保留给 ChunkReplay）。注意 D: 剩约 143GB（93%），每次完整回放约 2GB。

## 6. 下一步（建议顺序）

1. **跑完调参实验并定默认值**（只看 tune/dev），再在固定测试集与速度门禁上复核。预期：句尾概率治“被/呗、勒/了”等句尾错误；句首概率／左侧上文治“们/门、么/那、些/这”等句首错误；字幕加权可能改善“的/得”；分词合并治短语切分造成的微小差距。
2. **左侧上文接入 App**（实验有收益才做）：组句开始（`T9InputController.onDigitPressed`，组合为空时）设上文＝输入框光标前文字；右选部分上屏前（`ImeKeyRouter.selectCandidateAsync` 九键分支、调用 `onT9RightCandidateWillBeSelected` 之前）设上文＝开始时上文＋`service.t9PartialSegments` 文字＋所选词；剩余数字由 `refreshRemainingInput` 重送。`PredictionManager.lastCommittedText` 已维护本输入框的上屏文字，可参考。需在引擎队列内、按键处理前设置。
3. **模型分发与构建集成**（需用户决定）：建议普通包按现有“扩展商店”下载（`CandidateModelCatalog`，与原“九键语法增强”同一机制），老人包内置；先做紧凑格式（16 位量化对数概率、8 字节哈希槽、稀疏三元组索引，估计 370MB → 约 200–240MB；配合剪枝更小）。在此之前，任何新包都要么临时带模型、要么接受回到旧算法——必须向用户说明。
4. **用户学习层**：把现在的 `t9/ngram_user_bonus`（2.0×ln(1+提交次数)，启发式）换成线性插值的缓存模型 P=(1−λ)·P底座+λ·P用户，λ 随用户数据量有上限；底座模型只读，保证不遗忘。
5. 候选短词（整句之后）的顺序目前是 Rime 词库权重；可考虑按模型单词概率排序。

## 7. 测得的结果（整段首选）

| 测试集 | 原算法 | Rime 原生九键 | 二元版 | 1.12.0 三元版 |
|---|---|---|---|---|
| 用户报告句 | 2/5 | 3/5 | 5/5 | 5/5 |
| A不A 难例 | 14/30 | 11/30 | 20/30 | 23/30 |
| 120 句日常集 | 104/120 | 94/120 | 109/120 | 113/120 |
| 生成日常句 | 122/171 | 113/171 | 151/171 | 154/171 |
| 语法结构句 | 241/374 | 214/374 | 287/374 | 292/374 |
| 真实聊天 | 54/130 | 43/130 | 92/130 | 97/130 |

速度门禁（聊天，B C B C B）：空词库热键均值 4.91 → 3.99 ms（−19%）；预置词库连续过程 6.79 → 6.91 ms（+1.8%，噪声带内）、停顿过程 −10.6%。工作量：词典与束评估逐键相同，语法查找 5512 万 → 0，词组查找 0 → 1527 万，束插入 +23%——按逐键计数规则**形式上不通过**，已如实告知用户，不要写成通过。

剩余失败类别：他/她、早/晚、下午/下雨等同码歧义；的/得；句尾语气词；句首不可能的字；个人或工作词汇（核/喝、磨叽/墨迹）。

## 7a. 用户决定（2026-10-10）

- 模型分发：先做普通精简包，模型压成紧凑格式，走扩展商店下载。
- OpenSubtitles 字幕语料：用户认可，直接使用。
- 速度门禁：九键模型按实测耗时判定通过；逐键工作量计数只记录，不作为不通过依据。

## 7b. 2026-10-10 晚接手后结果

- `tune_next.sh` 重跑到 `next2/`（旧 `next/` 被占用；脚本 `OUT` 可覆盖，ChunkReplay 库路径已修为 `engine-build.json` 的 `library` 所在目录）。整段首选 tune/dev：基准 109/156、306/400；分词合并 109、305；句尾 0.3/0.6/1.0 整段略升但逐字明显降；句首概率 109、310（dev 逐字 1735→1782）；beam64 无变化且更慢；字幕加权 w12 大幅变差（93、260）。
- 固定测试集开句首概率（`report2/bos-*`）：日常 113→114/120，生成日常 154→155/171，报告 5/5，语法结构 292→297/374，A不A 23→20/30；合计 +4，设为默认 `ngram_sentence_start: true`。
- 左侧上文（ChunkReplay）：后半句无上文→有上文 tune-dev 373→410/488，test 419→436/495；整句句首上文 358→362。已接入 App：`T9InputController.onDigitPressed` 先取 `KeyboardCallbacks.t9LeftContext`（组合为空、无右选段、非密码框时读光标前 16 字），`RimeEngine.processQueuedT9KeyAndGetResult` 在组合为空时设置；`selectQueuedT9Candidate` 把所选词接到上文；退格撤销右选后上文置为未知。
- “能烧水的”（用户报告）：不是剪枝（beam 全放宽无变化），模型本身判“能 潜水的”−21.90 优于“能 烧水 的”−24.12，属语料覆盖问题；待补生活语料或做用户学习层。
- 1.12.1 / 20261124 已装手机，见 packaging.md。
- 用户学习层（§6 第 4 条）已做：`t9/ngram_user_bonus` 换为 `t9/ngram_user_weight`（λ=0.05），词分数 ln((1-λ)·P底座+λ·min(1,e^weight))，weight 为用户词库条目自带的对数概率（上屏占比＋约 200 次上屏内衰减的近期项）。回放（`report2/learn-*`、`seed-*`）：预置 3000 词词库＋刚选过一次“烧水”后“能烧水的”首选，“这个壶能烧水吗”仍错；预置词库下日常 114/120、生成日常 155/171 与 λ=0 相同；按键均值 6.95→7.11 ms（复测，噪声内）。注意 `T9MergeEntries` 去重保留排序靠前的条目，若系统条目排在用户条目前会丢掉 commit_count。1.12.2 / 20261125 已装手机。

## 7c. 长按删除改为“忘掉学习”（2026-10-11，用户决定“忘掉”）

- 旧行为：九键长按删除把整条文字写进 `t9-suppressed-v1.txt` 永久屏蔽（`Menu::SuppressText`＋`t9_filter`），26 键写进 `cyime-hidden-candidates.txt`（`candidate_policy`）。用户评价“完全偏离预期”。
- 新行为（`rime_jni.cc` `forgetCandidate`，返回 0 失败／1 已忘掉／2 没有学习记录）：词或单词整句 → `Context::DeleteCandidate` 走 Rime 原生删除，对应翻译器的用户词库 `UpdateEntry(entry, -1)` 标记删除（`CreateDictEntry` 跳过 commits<0），词库里本来有的词回到原位，自造词消失，再选会复活；多词整句 → 通过组件池拿同一个用户词库（`Ticket(schema,"translator")`，需 `Attach(table, prism)` 才能生成 key），只对 `commit_count>0` 的组件 `UpdateEntry(-1)`，然后 `RefreshNonConfirmedComposition`。英文（table 用户词）由 Rime 原生删除经 TableTranslator 的 Memory 处理。`UpdateEntry` 内已有 `T9InvalidateUserDictionary()`，联合解码缓存随之失效。
- Kotlin：`RimeEngine.forgetCandidateAtRevision`（替代 `suppressCandidateAtRevision`）；`ImeKeyRouter.deleteCandidate/Global` 按结果弹 Toast“已忘掉「X」的学习记录”／“「X」没有学习记录，排序不变”；`KeyboardView` 确认框改为“忘掉学习”；设置页“恢复以前屏蔽的候选”列出两种旧列表（新增 `nativeGetHiddenCandidates/nativeRestoreHiddenCandidate`），未确认的删除框在换输入框、候选变化时自动取消。
- 保留：`T9SuppressionStore`、`T9MenuSuppression.patch`、`candidate_policy` 的隐藏过滤只为兼容旧记录；不再有新写入。
- 验证：`.codex-artifacts/t9-ngram-20261010/probe/ForgetProbe.java`（自带 `RimeEngine.java` 声明，放在 runner 类之前的 classpath），用 `nativeHostRestoreUserDict` 装入 `report2/seed-shaoshui.userdb.txt`，三种模式 sentence／word／qwerty。
- 验证记录（2026-10-11 13:5x JST，电脑同源引擎，预置 3000 词用户词库＋“烧水”学过 1 次）：
  - 九键整句：删前 `63647426748433` 首选“能烧水的”；长按忘掉返回 1；之后首选“能潜水的”，`74267484` 里“烧水”仍在第 5 位（系统词原位）。
  - 九键词：`74267484` 删前“烧水”第 1（sentence 类型、学过）；忘掉返回 1；之后第 5 位、仍可选；“能”“ChatGPT”无学习记录返回 2，位置不变。
  - 26 键（rime_ice）：“烧水”删前 user_phrase 第 1；忘掉返回 1；之后变回 phrase（系统词）仍第 1；“你好”返回 2 不变。
  - 旧屏蔽列表两种均为空，新删除不再写入。
- 九键英文词表（2026-10-11）：`tasks-chinese.gradle.kts` 只收随包 english.txt 前 3000 常用词＋雾凇 en_ext 全表（互联网/品牌词）＋两字母大写缩写，`t9_english/digit_suffixes: false` 不再生成“英文＋数字尾巴”。回放 `report2/eng3-*` 与只收常用词的 `eng-*` 逐键候选完全相同（日常 114/120、生成 155/171、报告 5/5，逐键首选 599/742、633/891、25/36 无一变化），品牌词不挤中文。

## 7d. 用户报告“改法→改打”（2026-10-11）排查

- 42432 读法：该打／该法／还发／还大／还打／改大／改打／改法。语料出现次数：改法 33（LCCC 13＋字幕 20）、改打 29、还大 1976、该打 2017；“这种改法”1 次、“这种还大”0 次。模型：改法 −19.32、改打 −16.27、改 法 −16.83；“这种 改 法” −25.56、“这种 改法” −27.19，输给“这种 还大” −20.47、“这 胸 还大” −20.80。电脑引擎：42432 首选“该打”，改法第 10；“这种改法”整句解码为“这胸还大”（目标不在前 120）。平板上出“改打”是上一句上屏把它学进去了（长按→忘掉可退回）。
- 构建缺陷（真实但对此句无效）：`ngram_build.cc` 用词库权重做初始先验的硬 Viterbi 切词，先验低的真词（改法 111 vs 改打 2120）第一轮就被切成单字，之后计数≈0（−19.32 ≈ 0.65 个 token），而改打保住约 14/29。即使保留全部 33 次，“这种改法”也只到约 −24.5，仍输“这种还大”。
- 结论：这类书面说法要排前面只能补书面语料（新闻／百科／评论）；聊天＋字幕语料里“这种”后接“改”极少。已向用户说明，等其选择“补语料”或先做模型下载。
- 工具：`probe/CandProbe.java`（含左上文）、`probe/RowProbe.java`（整行权重、组件、目标名次），都用 `probe/src` 自带的 `RimeEngine.java` 声明。

## 7e. 上屏后联想“这→篇新闻”（2026-10-11）

- 平板 1.12.9 复现（26 键打 zhe 选“这”）：联想为「篇新闻 个 样 是 就 儿 种 么 些 都 也 几天 两天 道 三个 封 款 时 位 两」。不是平板残留学习。
- 原因：(1) 用户下载的联想神经模型（ONNX，top-40 logits 做 softmax，未校准）在 `NgramFusionEngine.fuseCandidates` 里直接覆盖底表分数，“篇”得分最高，再被 `FragmentCompletion` 接成“篇新闻”并保留原分数；(2) 内置底表 `association/lccc-base-v1.bin` 只是 LCCC 1/34 抽样、按词切分统计、每个上文 ≤6 条、分数 0.25·count/total，“这”后面只有 是／几天／就是／不是／都／两天，没有 个／些／样（“这个”是一个词，词级统计里从不出现 这→个）；(3) 个人学习按字记录，只会给单字，和这条无关。
- 修法：`tools/ime_lab/ngram/continuation_build.cc` 用九键模型同一份切分语料（`ngram_build6 ... --segment` 产出 `train.seg.txt`、`subs.seg.txt`）统计“已上屏文字尾部 1–3 字 → 后续”概率：词边界上 后续=下一个词，词内部 后续=该词剩余部分，两类同一计数尺度；输出格式不变（`# cyime-base-associations-v1`，context	word	count	total），每上文最多 10 条、4 万上文、lossy counting 控内存。`BaseAssociationModel.read` 上限改为 16 条／6 万上文，分数改为 count/total（概率）。`fuseCandidates` 改为一种尺度：0.7·P底表 + 0.15·P神经模型 + 0.15·P个人（打字观测按 n/8 渐进置信；导入语料按 0.4 份额换算）。
- 回归：`BaseAssociationModelTest.bundledTableRanksCommonContinuationsFirst` 读真实资源，断言 这→个 且 是／些 在前 3，我／你／不 的高频后续在前 3，单字上文首选不超过 2 字。运行：`./gradlew :app:testDebugUnitTest --tests "*BaseAssociationModelTest*"`。

## 7f. 词库先验（2026-10-12）

- 动机：内置明日方舟细胞词库的词不在模型词表，九键里按字拆开计分，首选被拼出来的普通词占住（无精二→武警而）。
- 实现：`T9JointDecoder.inc` 对不在词表的词条，`lp = ln((1-μ)·e^{链式拆字} + μ·e^{entry->weight})`（词库权重当作整词的单词概率），方案键 `t9/ngram_dictionary_prior`，进联合缓存 Options。
- 回放（引擎 prior/build，模板 eng2-gen-everyday 回放根，探针 `tools/dictionaries/arknights/probe/run.sh`）：
  - 方舟 38 词（九键、开词库）首选：μ=0 2，0.02 28，0.05 31，0.1 31；10 句整句 0→5（0.02 起）。26 键不受影响（38/38）。
  - 群聊 84 分句首选 15→29（0.02）→30（0.05）；B 站 82 句 4→16→17；干员名 322 个 45→151→166。
  - 日常调参集：tune 整句 109→108（0.02）→106（0.05），逐字 582→582→581；dev 310→310→310，逐字 1782→1780→1778。μ=0.05 多错的句子是 高时效／稳当些／南沙海关／了嘛 这类语料没见过但词库权重高的词插队。
  - 定默认 μ=0.02。逐键耗时该轮 4.06→4.48 ms（与基线非同一会话，噪声未隔离，待安静环境复测）。
- 验收用例含用户私聊，只在 .codex-artifacts，不提交不上传。

## 7g. 模型紧凑格式（2026-10-12，用户决定“两步都做”）

- 目标：v3 模型（367.6 MB）不改内容装进更小的文件，为之后补书面语料重建留空间；用户明确不接受为了缩小而掉分。
- 格式 v3（`CYT9NGR3`，`t9_ngram_format.h` 里的 `Header3`）：哈希槽 8 字节（键高 40 位 + 词号+1，槽位由键低位决定）；词条 3×int16（ln 概率 ×1024，步长约 0.001 nat，范围 ±32）；二元/三元后继词号 3 字节；二元 logp、二元回退、三元 logp 都是 int16；三元起点改为“每 2^shift 个二元一个 uint32 基址 + 每个二元一个 uint16 偏移”，构建器自动选不溢出的最大 shift（v3 模型取 6）。
- 读取器 `t9_ngram_model.{h,cc}` 同时识别 v2 和 v3（按 magic 分派，查找走统一的 `FindBigram/FindTrigram`），已安装旧格式模型的设备升级后不受影响。
- 工具：`tools/ime_lab/ngram/ngram_write.h`（两种格式的共享写入器）、`ngram_pack.cc`（v2 → v3 转换并自检：全部词条 + 随机 30 万二元/三元/回退路径，新旧读取器逐项比较）、`ngram_build.cc` 默认写 v3（`--format 2` 保留旧布局）、`ngram_query.py` 兼容两种格式。
- 转换结果：`D:\CyIME-Data\models\t9_ngram-model-v3-ls-tri.c3.lm` 212,706,156 字节（SHA256 前缀 `11cee2cbd87f42cb`），自检 4,361,482 项零不一致、最大差 0.0014 nat、哈希零冲突、无截断值；耗时 10 s，峰值内存 0.9 GB。
- 固定回放（Ubuntu-22.04-D，同一引擎，结果在 `.codex-artifacts/t9-ngram-20261010/compact/`，`old-*` 为原格式、`c3-*` 为紧凑格式）：tune 整句首选 108/156、逐字 582/991、top5 653，两种格式完全相同；dev 整句 310/400、逐字 1780/2798、top5 1924，完全相同；`compare_runs.py` 逐句比较 gained 0 / lost 0；逐键耗时 tune 4.04→4.01 ms、dev 4.29→4.31 ms（同一会话串行），搜索量与查表次数逐键一致。插件单元测试 `test/t9_ngram_model_test.cc`（两种布局同值、三元索引跨块）2/2 通过。
- 交付（2026-10-13 02:45–03:05 JST，用户决定“两个都留，默认 v3”）：v3 与 v4（p5e-8）紧凑版都已上传 models Release（`t9_ngram-v3-ls-tri-c3.lm` 212,706,156 字节、`t9_ngram-v4-ls-p5e-8-c3.lm` 109,685,894 字节），`CandidateModelCatalog` 改为两个条目（t9-ngram 完整版默认、t9-ngram-small 精简版，同一文件名二选一；因为模型管理只装条目的第一个版本，所以不能放同一条目的两个版本）；1.12.27 / 20261150 用打包器带 `--t9-model t9_ngram-model-v3-ls-tri.c3.lm` 打出并装平板，APK 246,449,831 字节（上一版 316,532,219）。打包器预设 config.json 的 t9_model 仍是旧文件名，未改。v4 紧凑版对比：dev 310→296、tune 109→107，丢的是「不要用脸打字→脚」「你咋不听→挺」这类日常句。
- 坑：从 WSL 首次调用 Windows python 跑 `storage_guard.py --measure` 冷缓存要超过 120 s 会超时，第二次 7 s；重试即可。

## 7h. 书面语料（中文维基）重建实验（2026-10-12 深夜 – 10-13，结论：不采用）

- 语料：`zhwiki-20261001-pages-articles.xml.bz2`（3.43 GB，SHA-1 04d5eec3…，CC BY-SA 4.0，用户同意模型按此许可发布），`prepare_data.py wiki` 流式抽正文（只取条目页、去模板/表格/引用、`-{}-` 取简体、OpenCC t2s、按标点切汉字串、≥2 字、60 字截断）：155.9 万篇 → 9667 万串、7.59 亿字，27 分钟，内存 37 MB；归档 `D:\CyIME-Data\corpus\wiki.txt.gz`。
- 候选（`ngram_build7` = 当前 `ngram_build.cc`，紧凑格式；构建日志在 `D:\CyIME-Data\corpus\zhwiki\`）：v6 全量混入 642 MB（9 分钟，2.3 GB）；v6b 维基取 1/4 行 332 MB；v6b 剪枝 2e-8 229 MB、5e-8 129 MB（各 4 分钟，1.3–1.5 GB）。
- 固定回放（同一引擎，结果 `.codex-artifacts/t9-ngram-20261010/wiki/`）整句首选 tune/dev：v3 108/310；v6 106/298；v6b 103/302；v6b-p2e-8 105/296；v6b-p5e-8 98/274。逐字首选 dev：1780 → 1677/1728/1711/1639。全部劣于 v3。
- 丢分原因：百科专名挤掉口语词（白胖→阿比让、跟风→很等、无奈→作词、骑就→啤酒），维基把聊天词的概率整体压低（查表：烧水 −13.71→−14.20，潜水 −10.05→−9.75，「能烧水的」从赢「能潜水的」变成输；改法/改打差距 2.95→4.09）；百科短语确实变好（中华人民共和国 成立 −23.1→−13.5）但测试集里没有这类句子。
- 结论：计数直接合并不可行，v3 继续作默认模型。若再试，方向是解码时双模型插值（聊天模型为主、书面模型小权重只补未见词），需要改读取器与解码器，不是再调语料比例。
- 时间线：抽正文 23:12–23:39，建模 23:40–00:05，回放 00:10–01:55（JST），期间仓库冻结只写 `app/build/generated/t9-ngram/` 与 `.codex-artifacts/.../wiki/`，回放后已把前者复原为 v3 旧格式文件并核对哈希。

## 7i. 个人语料数据集（2026-10-13，用户要求：只看结构、脚本处理、不读对话）

- 来源与规则：沿用 `tools/ime_lab/chat_corpus.py`（9 月 30 日已有，只收人工输入）并加 Claude Code 解析 `chat_corpus_claude.py`；结构探查只打印字段名、类型和计数（`type=user` 12,764 条里工具结果 11,978、系统插入约 450、真人输入约 400）。Agent 全程没有看任何消息正文。
- 产物（全部在 `D:\CyIME-Data\corpora\private-20261013\`，不进仓库、不进 OneDrive、不上传）：`chat_corpus.sqlite3`（Codex 2,428 条 / 202 会话，2025-12 至 2026-10-12；Cline 2,189 条 / 332 会话，2025-06 至 2026-10-09；Claude 390 条 / 29 会话，2026-10-08 起）、`chat_corpus.learning.jsonl`、`dataset/personal-train.txt`（75,366 串、62.0 万字）、`dataset/personal-heldout.txt`（8,155 串、6.8 万字，按会话 9:1 切，哈希盐 20261013）、`personal-stats.json`；去掉重复串 54,879 条。
- 个人回放用例：`prepare_data.py dev --raw`（新加：词典贪心切词，不需建模）从留出串取 400 句（4–12 字）+ `t9_fixture.py prepare`，词典读音；多音字占 13.9%（dev 集 18.7%），读音全在词典之内。用例文件在 `D:\CyIME-Data\corpora\private-20261013\heldout-fixture\`，不进仓库。
- 实验（2026-10-13 03:32–08:45 JST，同一引擎 cd3b1516）：基础语料 + personal-train.txt:K，K=4/16/48，各约 215 MB、3–4 分钟、1.3 GB。固定集整句首选 tune 108→108/108/107，dev 310→309/309/307；个人留出集 400 句 v3 108（27.0%）→ ×4 131（32.75%，得 27 失 4）/ ×16 119 / ×48 113。权重越大越差（过拟合训练会话、挤掉通用搭配）。个人集绝对值低是因为内容偏技术词和碎片，不是读音问题。结果在 `.codex-artifacts/t9-ngram-20261010/personal/`（只看汇总行，未读句子）。
- 采用候选：×4 版复制为 `D:\CyIME-Data\models\private	9_ngram-personal-p4.c3.lm`（私有，只能装用户设备，禁止上传或提交）。装机等用户决定。

## 8. 约束与坑

- 回放构建会快照源码：**有回放在跑时不改引擎源码和 `tools/ime_lab/{host_replay,android_replay}`**；同一会话的 B/C 必须是同一引擎。
- 一次只跑一个重任务，WSL 用 `nice -n 19`；`pkill -f` 会杀掉自己的 shell，用 `pkill -x` 或 `[t]` 写法。
- 设备：任何 adb 点按/截图前确认 CyIME 在前台；不碰用户其他应用；不运行 connectedAndroidTest。
- 打包：`auto.cmd --serial R5CW13EYCSF`（JAVA_TOOL_OPTIONS 见以往记录），交付后读回版本与 APK 哈希；文档写入 `docs/packaging.md`，台账一条功能一句话。
- 禁止 `./gradlew clean`；不提交、不还原用户改动；公开发布等明确要求。
