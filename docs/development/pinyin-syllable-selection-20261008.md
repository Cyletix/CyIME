# 2026-10-08 全拼、双拼及14键首音节选择

候选展开页可按当前方案的真实拼音音节筛选中文候选，保留原始编码、已确认汉字和应用光标。当前实现选择**第一个尚未确认汉字的音节**，可重选；确认对应汉字后再处理后续音节。它不等同于九键连续锁定多个音节后再整句选词的流程。

## 行为契约

| 操作 | 结果 |
|---|---|
| 选择／重选读音 | 约束当前 schema、prism、原始输入区间和 syllable ID；不把双拼改成全拼，不把大写组合键改成小写精确键 |
| 后缀追加、删除、替换 | 原 `[0,end)` 未变则保留已选音节；其余后缀继续按当前方案解码 |
| 编辑进入锁定区间 | 解除音节约束；重新输入相同串不能复活旧约束 |
| 预编辑光标移入锁定区间 | 临时按可见前缀构图，保留约束；光标移回恢复，不改应用光标 |
| 确认汉字 | 已完成的音节约束移除；原来的已确认汉字仍受保护 |
| 清空、切换 schema／编辑会话 | 旧约束和旧回调失效 |
| 陈旧 revision、原码、schema、已确认文本或无效区间 | 拒绝选择，不改变原组合 |
| 九键、英文独立词表 | 九键继续使用原控制器；独立英文词表不与中文 syllable ID 混用 |

Kotlin 请求与选择复用按键 FIFO，在取得 Rime 锁后再次检查输入会话；选择后的刷新不写 InputConnection 的 composing text 或光标。前端请求按 session、schema、revision 取消旧响应。

## 解码与持久接线

- 音节选项来自当前 dictionary/prism 的 `CommonPrefixSearch` 和 `QuerySpelling`，只接受完整拼写，不能仅用显示文字猜编码。
- 有约束时，将选中区间与后缀交给原 translator 的同一 `Syllabifier` 分别构图，再合并回原始字节坐标；补全、纠错和严格拼写配置沿用当前方案。边端点、属性位置与歧义起点均平移，指针索引最后重建。
- 无约束时仍调用原建图路径。受限 WordGraph 不进入单句或多句增量缓存，当前选择也不复用旧的无约束缓存。
- 在生成的 `engine.cc` 中集中接入 `OnContextUpdate` 和 `OnSelect`，使后缀编辑保留约束、清空及时清除，避免清空后重输同串的 ABA 问题。仅主 `script_translator` 读取约束。
- 接线位于 `PinyinSyllableSelection.cmake`，由既有 `T9PhraseBeforeSentence.cmake` 引入；Android 和本机回放消费同一 helper 与生成编译单元，没有仅修改依赖源码而遗漏打包。
- 选择必须收到实际 translator 的应用回执且仍有候选；失败恢复旧 property 和 composition/menu，不通过再次刷新丢失原高亮位置。

## 本机实测

固定工具扩展现有 `tools/ime_lab/host_replay/MergedLetterProbe.java`，使用生产 JNI、实际部署的四个 schema、当前词库的独立测试目录；没有使用手机或平板验证算法。

已保存两个失败反例，再用同一词库修复验证：

1. 原来选择 `ni` 后追加 `n`，选中状态立即消失：`probe-prefix-before.log`。
2. 仅保留 property 仍不够：`BUn` 全串建图只留下 `bin`、`nin`，`ni` 被提前剪掉；生命周期中间版同样失败：`probe-boundary-before.log`、`probe-boundary-intermediate.log`。

最终 `probe-final.log` 退出码 **0**，18 次真实音节选择及相关断言通过：

- 14键全拼 `BUGaO`、14键双拼 `BUGJ` 的 `ni`／`bu` 重选；精确小写 `b` 不出现 `ni`。
- 普通小鹤 `lo` 分别锁定 `lo`（ID 210）与 `luo`（ID 216）；全拼 `xian` 分别锁定 `xian`（ID 376）与 `xi`（ID 374），实际中文候选的首音节 ID 必须吻合。
- 已确认“你”后保留 `niBUGaO` 的原码与已选文本，继续选择并追加后缀。
- `BU` 选择 `ni` 后追加成 `BUn`，仍按 `ni+n` 解码；还能重选 `nin` 再改回 `ni`。
- `BU'GaO` 显式分隔符、光标进入后返回、追加及删除后缀均保留约束；删除进入锁段、替换为不完整前缀、清空、换 schema 后不会复活。
- 每次选择核对原码、Rime caret、confirmed 位置和 confirmed 汉字不变；错误原码／schema／span／syllable ID 被拒绝。

证据目录：`.codex-artifacts/pinyin-syllable-selection-20261008/`。最终本机库 SHA-256 为 `39674bda9ff107f0bb7436b1630fac7a5dea6aad388179a733893376c612f0af`；源指纹为 `26fbef0a5c8ac1f9015505e9274f6ddfe72bcc3504d9650004e9b9bb48a63bcb`。三轮指纹及失败日志均保留，两轮过渡源码／构建缓存已清理，最终同源缓存保留。

固定九键回放已完成：

```powershell
python tools/ime_lab/t9_replay.py run --output .codex-artifacts/t9-pinyin-selection-20261008 --baseline .codex-artifacts/t9-correction-final-20261005
```

同一固定语料、35 ms 逐键间隔、连打／停顿两种节奏和全新用户数据共运行 130 条 × 2，比较 4,370 个事件，执行错误为 0。首选变化、目标名次退化及新增回归均为 0；9 处候选列表差异均只在第 66 位，将动态日期词 `今天(10月5日)` 更新为 `今天(10月8日)`，其余位置一致。当前九键部署源 schema 与基线相同，本机 JNI 使用上面的最终库。

基线已记录的 33 个语言质量异常本轮仍全部存在，`quality.json` 和比较门禁的退出码均为 **1**。因此本轮证据仅支持“首音节选择没有引入固定九键回放中的新增回归”，不支持“九键语言质量已通过”。原始结果及比较在 `.codex-artifacts/t9-pinyin-selection-20261008/`。

Android 界面、构建安装及用户验收状态由本批交付记录分别说明；本报告中的本机算法通过不替代这些检查。
