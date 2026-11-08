# 11 月更新反馈复查

`cases.tsv` 每行对应一次用户操作、预期结果、相邻功能及仍缺的证据。电脑检查使用主目录当前代码。复现中的问题会令命令以非零状态结束。判断结果时看报告里具体失败的断言和待验项。

## 平板快速测悬浮底栏

从项目根目录运行，序号填已登记的空闲平板。脚本增量编译测试代码，在平板独立显示区打开实际的 KeyboardView。运行和截图均使用临时目录，结束时回收。日常版包名、版本、输入法选择和设置无需改动。

```powershell
python tools/feedback_audit/device_footer.py --serial <平板序号>
```

默认测手机竖屏、横屏、平板，以及手机尺寸深浅色贴底。脚本在截图里找到末排键帽的可见下沿，再分别量短横条、锁和渐变帧的上下留白，并检查底排回车和拖动。平板尺寸另取图检查约 3 秒的图标轮换。贴底一项长按短横条锁定和解锁，在按住、中途松手、退回和锁定静止时取图，并验证锁后拖不动、解锁后能拖动。结果自动放入新的 `D:/CyIME-Data/eval/floating-footer-日期-时间` 目录。加 `--compare` 可测本地保留的上一份 Release 编译类。运行需要现有的未混淆 ARM64 调试包提供 AndroidX 运行库，其他电脑用 `--runtime-apk` 指定。真实宿主、厂商系统和已安装日常输入法的端到端行为需另外验证。

同一平板入口支持英文输入和底排标点。用 `--input-checks all` 跑全部输入场景；用 `--input-checks settings` 只测句首大写。设置场景点真实的 26 键设置行，在 Android 编辑框核对开关前后、换行、句中插字及手动 Shift。其余场景在键盘上点按、拖动和长按，另以临时词库核对独立英文补全。

平板入口加 `--menu-checks layouts` 可从键盘齿轮验证布局显示、拖动排序和返回后的实际选用。`--menu-checks speech` 点按语音模型菜单的下载与重试，`--menu-checks toolbar` 检查手机和平板宽度的候选栏。加 `--bottom-calibration` 可检查纯手势贴底、三星系统按钮留位，以及尺寸调节后的保存。

## 电脑排查

```powershell
python tools/feedback_audit/run.py --case k70 --case english-dict --output D:/CyIME-Data/eval/feedback-editor-新日期
```

省略 `--case` 即检查全表。`--apk` 可指定已有交付包，与同目录构建回执核对文件指纹和压缩体积。`--archives` 指向既有实验目录，默认 `D:/CyIME-Data/eval`。结果含可阅读的 `report.md`、逐方法的 `results.json`、Gradle 任务计划及本次实际生成的 JUnit XML。只有请求的测试方法存在并执行才有通过记录；跳过、缺少材料和只有相邻测试的项目保留各自状态。多次指定 `--from-run` 可以汇总已留存的原始 XML，重复方法去重，有矛盾的结果拒绝合并。

`native.py` 在已有 Linux 主机引擎上重新部署当前笔画资源，查询「笔画」连续、分词和通配输入，并用当前九键音节表查 9464。需指定 `--library`、`--baseline`（只读部署基础文件）及新 `--output` 目录。`issue_video.py --output <新目录>` 单独读取 #228、#229、#230 的公开附件，生成带时刻的抽帧图。电脑回归本身不下载录屏。
