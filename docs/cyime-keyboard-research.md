# CyletixKeyboard → CyIME：研究结论与应用

整理日期：2026-09-23。原研究主笔记创建于 2024-06-17；源码最近本地提交 `2a2f429`（2025-03-04）。来源：[Cyletix-Keyboard](https://github.com/Cyletix/Cyletix-Keyboard)、`keyboard3.ipynb`、`keyboard4.ipynb`、`output/learning*.json` 和恢复的 Obsidian 主笔记。本轮没有重跑历史训练，也没有开展打字速度实验。

## 采用什么

1. 优化目标必须同时考虑输入代价、重复使用同一手指、学习成本与快捷键习惯；不能仅按移动距离选冠军。
2. 将英文、中文拼音、日语罗马字、代码分别报告，先在每类语料按有效输入长度归一化，再设置任务权重。历史混合语料的原始总距离不能跨实验直接比较。
3. 实体多指、手机单指、双拇指和横屏分体应使用独立模型。旧 Notebook 默认八个手指分区；双指和单指仅有注释示例，未证明手机收益。
4. 不把“元音必须集中”作为所有语言的硬约束；保留为可比较的实验条件。输入法需评估选词、纠错和切换成本，不能只评估字母序列。
5. CyIME 默认保留熟悉的 QWERTY；提供可回退的实验配置。日语九键、拼音九键、手写、语音分别维护，不受英文实验影响。

## 候选结论与证据强度

| 候选 | 可用结论 | 本轮用途 |
| --- | --- | --- |
| Cyletix10 | 手动设计与长期适应的历史基线 | 保留对照 |
| GA_4 | README 同一表中中文距离 285,563、重复计数 115,694，优于该表 GA_15 对应项 | 中文侧候选，不称通用最优 |
| GA_7 | 主笔记称历史综合表现突出，但与其他版本的语料/评分口径不统一 | 待复现实验候选 |
| GA_10 v4 | 主笔记明确写了“最终决定使用这个布局试一试”；Notebook 有可核对的键序 | 首个可选英文实验配置 |
| GA_15 | README 记录较少改键与收益的折中；笔记、日志对身份有冲突 | 暂不直接产品化 |

README 所写 GA_15 的 41.80% 和 GA_4 的 43.14% 是历史模型中的“效率”字段，不是已测得的打字速度提升。该表“重复率”给出的是计数，不能无分母地当概率解释。

## 已发现的记录冲突

- 主笔记的 GA_10 v3/v4 字符串相同，但 v4 描述写了交换 e/o。`keyboard3.ipynb` 中 v4 为 `qazwsxyecrfvdgb;hnljmki,uo.pt/`；本轮采用该代码定义，保留冲突说明，未改写原笔记。
- 主笔记 GA_14 与 GA_15 使用同一串 `qazwsxyecrfvjgb?h;unmdt,ol.pik`，而 `output/learning15.json` 最后记录的 best 为 `qazwsxedcrfvtgbyhnujmik,ol.p;?`。文件序号不能直接映射成布局编号。
- `learning4.json`、`learning7.json` 的最后 best 也不同于 Notebook 注释中的同号候选；不混用日志分数与布局名称。
- 历史布局有 `/` 与 `?` 的编码差异，`genome_to_keyboard` 只给 `/` 自动加入 `?` 别名；比较前必须统一字符覆盖及 Shift 成本。
- `finger_repeat` 遇到布局之外字符时不重置上一手指，可跨空格统计；后续评测需明确按键流定义。
- 不同版本切换过语料与评分权重；日本语完成情况在笔记和 README 也有差异，需要复现后再下量化结论。

## 实验配置如何用

文件：[`ga10-v4-english.custom.yaml`](../app/src/main/assets/layout-presets/ga10-v4-english.custom.yaml)。仅覆盖英文 `keyboard.qwerty_en`，不是默认设置。它把旧的按列存储键序转为三行：

```text
q w y r d ; l k u p
a s e f g h j i o t
z x c v b n m , . /
```

保留 30 个符号位而非删除标点硬塞进 26 键；手机几何、按键宽度仍不同于实体键盘。上滑数字按当前顶行从 1 到 0 排列；标点长按提供相应 Shift 符号。原有功能键由默认配置提供。

先备份用户目录 `rime/xime.custom.yaml`；若已有此文件，只将示例的 `keyboard.qwerty_en` 子树合入现有 `keyboard`，不要覆盖其他设置，也不要创建重复 YAML 键。若没有自定义文件，可将此示例复制为 `rime/xime.custom.yaml`。通过应用的配置文件管理或既有导入方式放入用户目录后重新部署，切换到英文验证。回退时恢复备份，或只移除本次加入的 `qwerty_en` 子树，再重新部署。本轮未更改任何设备配置。

候选原始键序、代码来源与 SHA-256 见 [layout-candidates.json](research/layout-candidates.json)。`ga10-v4` 是历史定义的复用，不代表手机优化已经完成。

## 下一阶段的验收标准

- 固定语料快照、预处理、覆盖率、字符映射、随机种子、预算和训练/验证划分；同预算对照 QWERTY、Colemak、Cyletix10、GA_4、GA_7、GA_10 v4。
- 手机分别记录单双拇指、横竖屏的任务完成时间、纠错次数、未纠错错误率、候选操作数及适应时长；物理键盘单独报告。
- 必测 Shift/符号、长按、滑动数字、编辑快捷键、切换中日英、候选取消、横屏/悬浮与原配置回退。
- 只有同条件测试确认收益且不破坏输入流程，才考虑默认布局迁移。

## 本轮验证（2026-09-23）

- `CyimeResearchPresetTest` 2/2、`KeyboardGestureConfigTest` 36/36、`KeysConfigLayoutReloadTest` 1/1：共 39 项通过。
- 验证历史 30 键行序、英文覆盖范围、逐键 tap、顶行上滑数字、中文不变、功能键继承及移除自定义后的回退。
- `assembleDebug --quiet` 成功，生成 arm64-v8a、armeabi-v7a、x86、x86_64 及 universal 包。
- arm64 APK：`app/build/outputs/apk/debug/CyIME-2.8.10-arm64-v8a.apk`，144342183 字节；SHA-256 `9CA902C2C596CDFD55271227BBEDBBA6DDA11CA1F04B40CC642CB058D6E00E64`。
- aapt 确认应用名 CyIME、versionName 2.8.10、versionCode 20260927、applicationId com.kingzcheung.xime；APK v2 签名验证通过。
- 6 个候选字符排列/行列转换、整理文档内部链接、10 份笔记原件哈希与 `git diff --check` 全部通过。
- 未安装设备，未验证新预设的触摸几何、真机输入速度或升级数据保留；配置解析通过不等于手机人体工学已验证。未提交、推送或修改远端。
