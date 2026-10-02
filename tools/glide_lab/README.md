# 滑行输入实验

2026-10-03：**仅电脑离线原型，未接入 Android，未验收。** 本目录不在应用 source set 内，不修改键盘、候选、Rime、配置或词库，不运行 Gradle，不打包、不安装、不联系设备，不自动联网或下载依赖。

## 本轮具备什么

- 原创、无 Android 依赖的 Kotlin 轨迹／布局／词表契约，可在以后审查后迁入应用；当前包名 `com.kingzcheung.xime.glidelab` 明确标识实验。
- 按真实传入坐标与键距处理轨迹，弧长重采样、模板预计算、DTW 路径比较、端点代价及有界先验；所有候选返回可解释分项分数，**不是置信度**。
- 拒绝点按、过短轨迹、无效时间／坐标、过量点和明显离开键盘的轨迹；阈值只是未调优的实验参数。
- 英文整词和中文全拼编码两个独立词表／样例；同码不同文字保留为不同条目。
- 固定 Kotlin 检查及 Python 回放，记录目标名次、拒识、误激活、分项分数、电脑耗时和源文件／样例／编译器哈希；可对同样例旧报告做逐条比较。

这不是经过训练的模型，没有上下文语言模型、个性化、真实词频、Rime 汉字解码、输入会话仲裁和生产 UI。逐词全表 DTW 只适合小词表基线，复杂度约为 `词条数 × 重采样点数²`，不能据此承诺大词库实时运行。重复字母折叠后 `of/off`、`to/too` 等轨迹可能一样；拼音 `xian`、同音词也不能靠几何强行区分。

## 运行

需要本地 Python 3.10+、JDK 17+，以及 `gradle/libs.versions.toml` 所列 Kotlin 编译器及其依赖的既有 Gradle 缓存。工具仅读缓存；缺依赖会报未运行，不下载、不调用构建、不改用设备。

在仓库根目录：

```powershell
python -B -m unittest discover -s tools/glide_lab -p test_run.py
python -B tools/glide_lab/run.py --output .codex-artifacts/glide-preparation-20261003
```

每次使用**新目录**，已有目录会拒绝覆盖。产物包括 `compile.log`、`checks.log`、`glide-lab.jar`、中间 TSV 和 `report.json`；均留在指定目录。脚本用独立 Java 进程，不使用 Gradle daemon、应用 build 目录或共享 Kotlin 编译输出。

后续比较（目录名仅示例，应更换为实际新目录）：

```powershell
python -B tools/glide_lab/run.py --output .codex-artifacts/glide-next --baseline .codex-artifacts/glide-preparation-20261003/report.json
```

`--fixture <JSON>` 可重复指定；省略时读取本目录 fixtures 下所有 JSON。`--gradle-cache <目录>` 指向 `caches/modules-2/files-2.1` 层级。返回码：0 为当前固定检查与所有样例断言通过，1 为回放不符合预期，2 为输入／环境／执行失败。Kotlin 检查失败也返回2，具体见 `checks.log`。

## 样例契约 v1

| 字段 | 含义 |
|---|---|
| `schema` / `kind` | `cyime.glide.fixture.v1` / `synthetic`，目前只接收明确标注的合成样例 |
| `id`, `language`, `scheme` | 样例标识及独立语言／编码方案；实验只允许 `en/direct`、`zh/pinyin` |
| `layout.revision` | 该坐标快照的版本；实际接入还需绑定输入会话与配置版本 |
| `layout.key_unit`, `keys` | 正数键距尺度、每个小写拉丁字符的中心坐标；同一坐标系且一字符一个中心 |
| `lexicon` | `id/code/text/prior_cost`；code 为布局支持的字母，prior_cost 在0～1，越小越有利 |
| `cases` | `id/description/expected_ids/points`；点为 `[x,y,单调毫秒]`，空 expected_ids 表示不应产生候选 |

每份文件限4 MiB、1～10,000词条、1～1,000轨迹、每轨迹1～2,048点、编码最长64字符。Python先验证文件边界，Kotlin另验证解码边界；既不信任 TSV 以外的应用输入，也不将样例中的语言字段当成应用能力注册。

`fixtures/english.json`、`fixtures/pinyin.json` 是本轮自写玩具词表与合成路径，包含键中心、轻微偏移、停顿、重复字母、同码歧义、点按、静止、微动和越界。路径按词的键中心生成并做轻微扰动，与模板天然接近，**不是独立测试集**，不可将通过比例称为真实识别率。预期集合允许明确歧义，报告的是集合首个命中排名；这不代表区分了集合内的词。

电脑耗时为3次预热、逐轨迹7次运行的中位数；只含 decode，不含模板加载、触摸、调度、渲染、词库IO或 Rime。小词表和同机复跑的数字只用于诊断，不能换算手机延迟或声称用户体验已达标。基线比较拒绝不同样例字节／报告协议，报告排名和断言变化；旧问题仍然是失败，不因基线相同而算通过。

不采集日常输入、剪贴板或个人词库。将来真实采样应使用单独、明确同意的练习任务，另升级数据协议并区分训练、调参和留出集。

完整调研、生产接入边界和验收矩阵见 [准备报告](../../docs/development/glide-input-preparation.md)。本轮代码与样例为原创，未复制上游源码、外置二进制、训练集或词库；遵循仓库现有许可。
