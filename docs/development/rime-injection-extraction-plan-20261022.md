# 解码逻辑从构建时注入迁到独立源码（1.15 第 3 项）

2026-10-22。目标：我们自己的九键联合解码、26 键整句重排、CyLM 接入和相关读配置代码，不再由 CMake 在构建时用字符串替换塞进 Rime 源码的生成副本，而是写成普通的独立 `.h/.cc`；Rime 里只留几行调用入口。Rime 内部确实要改的地方继续用看得见的补丁文件，不硬塞成插件。

不能变的东西：每一步候选逐字节相同、速度不降。用户看不到任何变化。

## 1. 现状清点

`app/src/main/jni/cmake/Rime.cmake` 在 `add_subdirectory(librime)` 前后做了三类事。

### 1.1 显式补丁（`git apply` 到 librime 子模块工作区）

| 文件 | 改的 Rime 文件 | 内容 | 性质 |
| --- | --- | --- | --- |
| `RimeDictionary.patch`（`RimeDictionary.cmake` 应用） | 16 个：`algo/spelling.h`、`algo/syllabifier.cc`、`common.h`、`dict/corrector.*`、`dict/dictionary.*`、`dict/table.*`、`gear/poet.*`、`gear/script_translator.*`、`gear/table_translator.cc`、`gear/translator_commons.*`，约 775 行新增 | 增量音节图、桶查询、邻键纠错、句首句尾位置惩罚、整词优先与叠字纠错门槛、学过的纠错不抢首位、造句同音单字等 | Rime 内部确需改动 + 一部分我们的选词策略（`script_translator.cc` +319 行）。归“词库和词权问题”线程 |
| `T9DecodeCache.patch` | `script_translator.h`、`user_dictionary.cc` | 译者上挂 `t9_joint_cache`；用户词库变化时调 `T9InvalidateUserDictionary()` | 入口钩子，已经是最小形态 |
| `T9EnglishLearning.patch` | `user_dictionary.cc` | 查询前 `FetchTickCount()` | Rime 内部小改，保留 |
| `T9MenuSuppression.patch` | `menu.h/.cc` | `Menu::SuppressText` 隐藏被删候选 | Rime 内部小改，保留 |

### 1.2 字符串替换生成副本（本次要拆掉的主体）

做法：读 Rime 源文件（已打过上面的补丁）→ `string(REPLACE)` 插代码 → 写到构建目录 `cyime-*.cc` → 从 `rime-static` 里换掉原文件。锚点找不到就报错停构建。

| 文件 | 生成副本 | 插入内容 | 是否我们的逻辑 |
| --- | --- | --- | --- |
| `T9PhraseBeforeSentence.cmake` | `cyime-script-translator.cc` | 13 处：22 个头文件；构造参数加 `Config*`；约 95 行读配置（九键联合解码预算、词组模型、读音表、CyLM 文件）；约 210 行成员和资源函数（`T9ResolveOnce`、`T9LoadReadings`、`T9CylmOpen`、`T9SyllableIds`、`ApplyCylmSentence`）；联合解码分支；26 键整句重排约 150 行（词组模型给 n-best 打分、去掉纠错词重造句）；`set_exhausted` 条件；两处记住造句用的词图；关掉 Android 诊断日志；整段插入 `T9JointDecoder.inc`（577 行，`PrepareJointCandidates`） | 是，全部 |
| 同上 | — | 把 `cylm/cylm.cpp` 编进 `rime-static`，加 librime-t9、octagram、cylm、onnxruntime 头文件目录 | 构建配置，改为普通 `target_sources` 即可 |
| `PinyinSyllableSelection.cmake` | 同一份 `cyime-script-translator.cc` | 9 处：`ScriptSyllabifier` 带上 `Context*/Schema*`，`BuildSyllableGraph` 改调 `cyime::pinyin_selection::BuildGraph`，两处增量缓存加“选了音节就不缓存” | 逻辑已在独立头文件 `librime-t9/src/pinyin_syllable_selection.h`，这里只剩钩子 |
| 同上 | `cyime-pinyin-engine.cc` | `engine.cc` 两处生命周期钩子（`BeforeContextUpdate`、`BeforeCandidateSelection`） | 只剩钩子 |
| `T9SingleKeyRecall.cmake` | `cyime-syllabifier.cc` | 3 处：单个数字键保留声母缩写、九键末尾缩写不被剪掉、保留还有出边的顶点 | Rime 音节图剪枝，确需改 Rime；而 `syllabifier.cc` 同时被 `RimeDictionary.patch` 改，两层叠在一起 |
| `T9LookupTrace.cmake` | `cyime-dictionary.cc`、`cyime-table.cc`、`cyime-user_dictionary.cc` | 记录查询走到的位置（`T9ObservePosition/T9ObserveExtra/T9OriginScope`），联合解码缓存据此判断能否复用；主机构建另加工作量计数 | 钩子。注意这三个文件也同时被 `RimeDictionary.patch` 改 |
| `T9Grammar.cmake` | `cyime-octagram.cc` | octagram 加 `full_word`：九键只认整词匹配；主机构建加计数 | 语法插件内部小改 |
| `T9WorkCounters.cmake` | — | 只提供 `t9_work_replace` 函数，主机回放才用（Android 不开，生成代码不变） | 测速工具 |
| `Rime.cmake` 开头 | 原地改 `librime-lua-deps/lua5.4/liolib.c` | Android 旧 API 下 `fseeko` 的条件 | 第三方构建修正，与解码无关 |

已经是独立源码、不在本次范围：`librime_jni/candidate_policy.cc`（候选策略）、`librime-t9/src` 下的词组模型、句子评分、候选池、解码缓存、删词隐藏等。

主机回放（`tools/ime_lab/host_replay/build_host.py`）复制同一套 CMake 和补丁来构建，所以改动对 Android 和电脑回放同时生效，不用另改。Windows 版 `CyIME-Windows/third_party/cyime-engine/librime` 是另一份已打好改动的 librime，每次改 Rime 源码都要同时折进 Android 补丁和这份副本。

### 1.3 现状的问题

- 代码在构建前看不到完整样子：要读 `T9PhraseBeforeSentence.cmake` 里的 CMake 字符串才知道最后编的是什么，IDE 跳转、编译报错行号都指向构建目录里的副本。
- 同一个 Rime 文件被改两层：先打补丁，再在打完补丁的文本上字符串替换（`syllabifier.cc`、`dictionary.cc`、`table.cc`、`user_dictionary.cc`、`script_translator.cc`）。另一线程改补丁时，很容易把后一层的锚点改没，构建才报错。
- Windows 版拿不到同一份解码源码，只能手抄。

## 2. 目标形态

- 我们的逻辑放在新目录 `app/src/main/jni/cyime_rime/`，作为普通源文件编进 `rime-static`。不放 `librime-t9/src`：那里的 `*.cc` 会被整目录收进九键插件，而这些代码属于 Rime 的拼音翻译器本身。
  - `t9_script_options.{h,cc}`：读 `t9/*`、`cylm/*` 配置，资源路径解析与模型、读音表的进程级缓存。`ScriptTranslation` 以它为基类，原来的成员名不变，所以其余代码一个字都不用改名。
  - `t9_cylm_sentence.{h,cc}`：CyLM 整句替换。
  - `t9_sentence_rescore.{h,cc}`：26 键整句重排。
  - `t9_joint_decoder.{h,cc}`：由 `T9JointDecoder.inc` 改成独立编译单元。
  - `t9_script_query.h`：这几块从 Rime 拿的东西放在一个小结构里：`ScriptTranslator` 公开方法（`GetPrecedingText`、`blacklist`、`language`、`t9_joint_cache` 等，本来就在头文件里），以及 `ScriptSyllabifier` 的 `input()`、`syllable_graph()`、`IsCorrection()`。`ScriptSyllabifier` 只在 `script_translator.cc` 里声明，A 段先用函数指针传 `IsCorrection`；B 段再看要不要把它的声明挪进补丁里的头文件。
- Rime 里留下的只有显式补丁中的几行调用：构造时 `options.Load(config, context)`，`Evaluate` 里联合解码分支、整句重排、CyLM 替换各一个调用，造句处记住词图，以及拼音选音节和引擎生命周期钩子。
- `CMake` 里不再有对源码的 `string(REPLACE)`；Rime 和 octagram 的改动全部是补丁文件。

## 3. 分步

每步一个提交，单独过门禁；某步不过就回到上一步，不叠下一步。

### A. 不碰 Rime 补丁，只动我自己的文件（可以马上开始）

钩子暂时仍由 CMake 注入，但每处注入缩成一行调用，代码本体搬到独立文件。

1. **建基线**：用当前 main 跑全部门禁（见第 4 节），留存结果目录作后续对比；同时跑一次“基线对基线”，量出计时的自然波动。
2. **读配置和资源函数**：约 300 行成员与读配置块搬到 `t9_script_options.*`，生成副本里只剩基类和一个 `LoadT9Options` 调用。
3. **CyLM 整句替换**搬到 `t9_cylm_sentence.*`。
4. **26 键整句重排**搬到 `t9_sentence_rescore.*`。
5. **联合解码**：`T9JointDecoder.inc` 改成 `t9_joint_decoder.cc` 独立编译。这一步最可能影响速度（原来与 Rime 同一编译单元，编译器可内联）。如果计时超出波动，退回为“在补丁里明文 `#include` 这份 `.cc`”，代码仍是独立文件，编译结果与现在相同。

### B. 把剩下的注入写进补丁（经协调线程，交给“词库和词权问题”线程或由其同意后统一改）

每次同时改 librime 的两份补丁：Gradle 先打的 `patches/cyime-librime.patch` 和 CMake 检查的 `cmake/RimeDictionary.patch`。Windows 引擎没有联合解码、拼音选音节这些功能，它的 librime 副本不用跟着改。

6. **script_translator / engine 钩子**：`T9PhraseBeforeSentence.cmake` 与 `PinyinSyllableSelection.cmake` 剩下的一行调用写进 `RimeDictionary.patch`，两个 CMake 文件删掉替换逻辑。额外门禁：新补丁打完后的 `script_translator.cc`、`engine.cc` 与原生成副本逐字相同（允许只差 `#line` 与空白），候选不同就不可能。
7. **音节图剪枝**：`T9SingleKeyRecall.cmake` 的 3 处并入 `RimeDictionary.patch` 的 `syllabifier.cc` 部分，同样比对生成文本。
8. **查询位置记录与计数**：`T9LookupTrace.cmake` 的钩子并入补丁。计数宏在 Android 上展开为空，直接写进补丁即可。`T9WorkCounters.cmake` 的替换函数还给第 9 步的 octagram 用，到那一步再删。
9. **octagram 整词匹配**：`T9Grammar.cmake` 改成 `OctagramT9.patch`，仍打在构建目录副本上，不改子模块。
10. **收尾**：几个补丁统一一个应用函数；Lua 的 `liolib.c` 修正改成小补丁（与解码无关，最后做）。

### C. 可选，看“词库和词权问题”线程

`RimeDictionary.patch` 里 `script_translator.cc` 的 +319 行有一部分是我们的选词策略（整词优先门槛、叠字纠错门槛、造句同音单字），能抽成独立函数的再抽；与 Rime 候选循环交织的部分保持补丁。这部分正在被那边改选词，等它稳定后再定，不在本次承诺里。

## 4. 每步门禁

全部在电脑本地跑，不用平板。构建经协调线程排队（Gradle、WSL、MSVC 同一时间只一个）。

| 门禁 | 怎么跑 | 通过条件 |
| --- | --- | --- |
| 九键候选，无学习词 | `python tools/ime_lab/t9_replay.py run --output <新目录> --baseline <基线目录>` | 每个按键的完整候选列表与基线逐字节相同（比原始逐键记录，不只看质量汇总） |
| 九键候选，有学习词 | 同上加 `--user-dict tools/ime_lab/fixtures/user_dict_seed_20261009.userdb.txt` | 同上 |
| 26 键候选，无学习词 | `python tools/ime_lab/lexicon_regression.py --dll <本步主机引擎>` | 每条用例候选与基线逐字节相同 |
| 26 键候选，有学习词 | 同上加 `--learn learn.tsv` | 同上 |
| 工作量 | 主机回放的逐键计数（`CYIME_T9_WORK_COUNTERS`），`t9_replay.py compare` 默认 `--work-tolerance-percent 0` | 与基线完全相等（搬代码不该多查一次） |
| 速度 | 主机回放逐键耗时：基线与新引擎用 `--engine-from` 在同一时段交替各跑至少两次，`compare --noise-baseline … --candidate-repeat …` | 中位数和 p95 都不比基线差，超出基线自身两次之间的波动即不合格 |
| Android 构建 | 协调线程排到时 `./gradlew assembleDebug --quiet` | 四个 ABI 都能编过 |

搬代码属于“用起来不变”的内部整理，全部通过才提交；按项目规则，每轮应用改动后照常打包装机，版本升 PATCH。

## 5. 与其他线程的分工

- `RimeDictionary.patch` 和 librime 选词相关文件归“词库和词权问题”线程。A 段不碰这些文件；B 段每一步先经协调线程约定由谁改、何时改，避免两边同时改同一补丁。
- 每次 librime 改动同时进 Android 补丁和 `CyIME-Windows/third_party/cyime-engine/librime`。
- A 段完成后，Windows 版可以直接复制这几个独立文件，不再手抄 CMake 字符串里的代码。

## 6. A 段结果（2026-10-22）

第 2~5 步都是机械搬移，在一个引擎里一起编、一起过门禁，作为一个提交。基线是 ea6f5595 的源码（`source-files.json` 里 `T9PhraseBeforeSentence.cmake` 哈希与该提交相同）；所有结果、比较脚本在 `D:/CyIME-Data/eval/rime-extract-20261022/`。

- 生成副本里留下的注入：一个基类 `T9ScriptOptions`、一个 `LoadT9Options` 调用、`T9Query` 和 `T9IsCorrection` 两个小函数、`ApplyCylmSentence` 包装，以及联合解码、26 键重排各一个调用；`T9JointDecoder.inc` 删除。其余注入（头文件、构造参数、`rime_best`、记住词图、`set_exhausted`、诊断日志）不变，留给 B 段。
- 九键回放无学习词、预置学习词：4374 条记录（`same_candidates.py`，只去掉耗时、路径、引擎哈希和每次部署的文件时间戳）逐字节相同，逐键工作量计数也完全相同。新引擎的第二次运行、基线的第三次运行与第一次也都相同。
- 26 键回归无学习词、学过 `learn.tsv`：5950 条用例只有显示当前时间的候选（`sj`、`ts`、`dt` 等）不同，基线自己两次之间也是这些不同；遮掉时间后完全相同，各组命中数一致。
- 速度（逐键处理耗时，ms，同一时段交替运行）：

| 场景 | 基线 p50 | 新 p50 | 基线 p95 | 新 p95 |
|---|---|---|---|---|
| 九键无学习词，三次/两次 | 2.395 / 2.483 / 2.406 | 2.411 / 2.508 | 19.24 / 20.31 / 19.87 | 18.34 / 19.56 |
| 九键预置学习词 | 2.901 | 2.926 | 36.51 | 35.05 |
| 26 键无学习词，三次/三次 | 0.939 / 0.897 / 0.923 | 0.868 / 0.910 / 0.899 | 4.73 / 4.36 / 4.52 | 4.21 / 4.35 / 4.25 |
| 26 键学过的词 | 1.099 | 1.109 | 6.78 | 6.74 |

九键 p50 每对都高约 1%（约 20 微秒），在基线自身 3.7% 的波动以内；p95 和平均值都更低。`t9_replay.py compare` 判定 `speed_gate: passed`，工作量增长 0%。按第 4 节的规则合格。
- Android：`./gradlew assembleDebug --quiet --max-workers=2` 通过，四个 ABI 的 `librime_jni.so` 都重新编译并含新目录的目标文件。没有打包装机（按协调安排随下一次合并安装）。

## 7. B 段第 6~8 步结果（2026-10-22）

- 做法：把前半段 Android 构建生成的 `cyime-script-translator.cc`、`cyime-pinyin-engine.cc`、`cyime-syllabifier.cc`、`cyime-dictionary.cc`、`cyime-table.cc`、`cyime-user_dictionary.cc` 原样写回 librime 工作区（每个文件保留原来的换行符）。只有三处不同：`script_translator.cc` 的头文件只留实际用到的 5 个；`syllabifier.cc` 恢复上游的 `#include "syllabifier.h"`；三个词典文件加上原来只在主机构建里插入的 `T9_WORK_*` 计数宏，Android 上这些宏展开为空。
- 两份补丁同步重写：`RimeDictionary.patch` 多了 `engine.cc`、`user_dictionary.cc` 两段，`cyime-librime.patch` 多了 `engine.cc`；共有的 18 个文件逐段相同，`cyime-librime.patch` 照旧多一个 `memory.cc`。`RimeDictionary.patch` 的 `user_dictionary.cc` 一段直接取自 `cyime-librime.patch`，所以连带收进了那边早已有的“纠错边不挡住学过的打字原词”一处修正。
- CMake：删掉 `PinyinSyllableSelection.cmake`、`T9SingleKeyRecall.cmake`、`T9LookupTrace.cmake`；`T9PhraseBeforeSentence.cmake` 改名为 `CyimeRimeSources.cmake`，只剩包含目录、源文件和 CyLM 编译选项，外加一道检查：librime 里找不到 `T9DecodeJoint(` 就停止构建（补丁没打上时不会悄悄少功能）。librime 的源码现在原样编译，不再生成副本；只剩 octagram（第 9 步）还用生成副本。
- 离线核对：两份补丁都能在工作区反向通过；用干净子模块加一份补丁、再加三个小补丁，得到的文件与工作区逐字节相同（只比换行统一后的内容）。
- 顺带发现一个早就存在的问题：在干净子模块上打完任一份 librime 补丁后，`T9DecodeCache.patch` 正向打不上，因为它的 `script_translator.h` 两行已经在大补丁里了，而它的 `user_dictionary.cc` 几行又不在。现有工作区不受影响（已全部打上，反向检查通过）；从零检出时 CMake 会在这一步报错停下。留给第 10 步收尾时把小补丁并进大补丁一起解决。
- 电脑端门禁（新引擎 `2fa4c321…`）：九键无学习词、预置学习词，以及另外两次重复运行，4374 条记录逐字节相同，工作量计数也相同；26 键无学习词、学过的词，遮掉当前时间候选后 5950 条全部相同。
- 速度（这一时段机器上同时有别的线程在跑模型，绝对值整体偏高；同一时段交替运行）：

| 场景 | 基线 p50 | B 段 p50 | 基线 p95 | B 段 p95 |
|---|---|---|---|---|
| 九键无学习词，两次/两次 | 2.744 / 2.832 | 2.865 / 2.420 | 22.12 / 22.32 | 22.39 / 19.60 |
| 26 键无学习词，两次/两次 | 1.212 / 1.138 | 1.170 / 1.104 | 5.77 / 5.35 | 5.62 / 5.31 |

九键两次平均 p50 2.79 → 2.64、p95 22.2 → 21.0；26 键中位数和 p95 两次都更低。参与编译的源码文本与前半段相同（计数宏原本就在主机构建里），没有逐个比对目标文件；按此推断这些差别是负载波动。

## 8. 第 9、10 步结果（2026-10-23）

- 第 9 步：octagram 的整词匹配和主机计数做成 `cmake/OctagramT9.patch`，原地打进 `librime-octagram` 子模块（和 librime 一样），插件复制时一并带上；删掉 `T9Grammar.cmake`。打完补丁的 `octagram.cc` 与原来的生成副本只差计数宏。`Rime.cmake` 开头重复的 Lua `liolib.c` 字符串补丁删掉：Gradle 的 `patches/cyime-lua-android.patch` 本来就打同一处，主机构建也不需要（只对 Android 有效）。
- 第 10 步：`T9DecodeCache.patch`、`T9EnglishLearning.patch`、`T9MenuSuppression.patch` 并进大补丁。现在 `cmake/RimeDictionary.patch` 和 `patches/cyime-librime.patch` 是同一份文件（22 个 librime 文件，按工作区整体重新生成），由 `CyimePatches.cmake` 的 `cyime_apply_patch` 统一应用：已打过就跳过，打不上就报错停构建。`RimeDictionary.cmake` 删掉。第 7 节说的“从零检出时小补丁打不上”随之消失：干净子模块打这一份补丁，得到的 22 个文件与工作区逐字节相同。
- 构建时对源码的字符串替换至此全部去掉；`T9WorkCounters.cmake` 只剩开关。
- 电脑端门禁（基线是 a3470ce8 时的工作区，新引擎是加上本步改动的同一工作区；两次快照之间只有本步的 12 个文件不同）：九键无学习词、预置学习词、重复一次，4374 条记录逐字节相同，工作量相同；26 键无学习词、学过的词、重复一次，遮掉当前时间候选后 5954 条全部相同。
- 编出来的东西：两次构建的 307 个目标文件里 306 个逐字节相同；只有 octagram 一个，因为文件名从 `cyime-octagram.cc` 变回 `octagram.cc`，静态初始化函数的符号名不同，反汇编的指令相同。
- 计时（ms）：

| 场景 | 基线 | 新 |
|---|---|---|
| 九键无学习词 p50，两次 | 2.369 / 2.663 | 2.485 / 2.464 |
| 九键无学习词 p95，两次 | 18.98 / 21.13 | 19.22 / 19.68 |
| 九键预置学习词 p50 / p95 | 2.848 / 23.98 | 2.730 / 24.03 |
| 26 键无学习词中位数，两次 | 0.936 / 1.036 | 0.945 / 0.978 |
| 26 键学过的词中位数，两次（第二次先跑新的） | 1.083 / 0.948 | 1.197 / 1.016 |
| 26 键学过的词 p95，两次 | 5.64 / 4.94 | 6.24 / 5.26 |

26 键学过的词两次都是新引擎慢 7~10%，但没超出基线自己两次之间 14% 的差距；而且除 octagram 的符号名外，参与链接的目标文件完全相同，所以判断是链接布局和机器负载的波动，不是代码变慢。其余场景在波动内。
