# CyIME 打包器

双击仓库根目录的 `packager.cmd` 打开图形界面；带参数运行就是命令行。界面是 `scripts/packager/web/index.html`，样式沿用 CyIME-Windows 设置页（颜色、圆角、整行开关，跟随系统深浅色），由 `scripts/packager/server.py` 在 127.0.0.1 上提供（每次启动随机口令）并用 Edge 应用窗口打开；窗口关闭且没有任务在跑时服务自动退出。界面只负责选项和显示日志，实际工作都调用 `scripts/packager/cyime_packager.py`，输出区会显示每次操作对应的命令行写法。只用 Python 标准库，不新增依赖。

开始菜单入口：运行一次 `powershell -ExecutionPolicy Bypass -File scripts/packager/install-shortcut.ps1`，会在开始菜单添加带 CyIME 图标（与 CyIME-Windows 相同的 facet 图标）的“CyIME 打包器”，可固定到开始屏幕或任务栏；仓库或 Python 位置变了就重跑。界面服务固定用 127.0.0.1:47821（被占用时随机端口），子进程统一放在一个隐藏控制台里运行，打包时不会闪出命令行窗口。页面声明了 `darkreader-lock`，Dark Reader 之类的扩展不会再改它的颜色。

运行状态：打包时显示第几步、进度条、当前 Gradle 任务和已用时间，按钮变灰；结束时写明成功（包的位置、SHA256，可打开所在文件夹）或失败（从 Gradle 的 “What went wrong”、Python 异常和 STOP 信息里提取真正原因）。

## 能选什么

| 选项 | 命令行 | 说明 |
| --- | --- | --- |
| 预设 | `--preset 装到我的设备` | 预设在 `scripts/packager/config.json`：装到我的设备（自己试用，旧名“日常测试包”仍可用）、轻量发布包（发 GitHub，不带九键模型）、离线完整版（模型全打进包）；都是正式版，其后的参数可覆盖预设。调试版用 `--debug`，界面里在“高级”中，只能装到自己设备 |
| 正式版 / 调试版 | `--release` / `--debug` | 调试版同样用正式签名，可覆盖安装；调试版不能发布 |
| 离线模型 | `--offline-models`；`--offline-model ID`（可重复） | 只写 `--offline-models` 放全部离线模型（以前的完整版）；用 `--offline-model` 只放选中的几个（ID 见 `info` 的 offline），经 `-PbundledModelIds` 传给 `prepare-bundled-models.py --models`。应用接受部分内置，没内置的默认模型照常联网下载。界面按最近一次 APK 实测的压缩比实时估算安装包大小，并标出各模型许可（config.json 的 modelNotes） |
| 九键词组模型 | `--t9-model <文件名或路径或 none>` | 从 `D:/CyIME-Data/models/*.lm` 选，预设默认用紧凑版 `t9_ngram-model-v3-ls-tri.c3.lm`（内容同 v3，212.7 MB，1.12.27 起，APK 约 246 MB）；构建时硬链接到 `app/src/main/assets/rime_chinese/t9_ngram.lm`，结束（含失败）后移除；被强行中断时下次运行自动清理。已有别人放的不同文件时停止，不覆盖 |
| 内置词库 | `--exclude-dict builtin_arknights` | 去掉的词库只在本次构建里用资源忽略规则排除，不动源码；应用缺这个词库时跳过 |
| 版本号 | `--bump auto/patch/minor/none` | auto：当前版本已装到过设备，或已经打过另一种内容的正式包（只出包、发给别人的也算）时升 PATCH；同一组选项重打不升。versionCode 同时 +1 |
| 安装 | `--serial`、`--no-install` | `adb install -r`，保留数据，不降级不卸载 |
| 手写训练题库 | `--push-banks` / `--no-push-banks` | 题库（公式、英语、日语）不进任何安装包。“装到我的设备”默认在安装成功后把 `config.json` 的 `handwritingBanks.dir`（`D:/CyIME-Data/handwriting_training/banks/*.tsv`）推到设备 `Android/data/com.cyletix.cyime/files/handwriting_training/`；目录不存在或推送失败只在结果里注明“题库未放置”，不影响安装。轻量发布包、离线完整版不推 |
| 发布 | `--publish [--latest] [--prerelease] --yes` | 先建草稿、上传、校验哈希再公开；要求源码已提交推送，不覆盖已发布文件 |
| 检查 / 干跑 | `--check`、`--dry-run` | 干跑只列计划，不改任何文件 |

上传资源（模型、词库包等）到资源 Release（默认标签 `models`，不设为 Latest）：

```
packager.cmd upload D:/CyIME-Data/models/t9_ngram-model-v4-ls-p5e-8.lm --name t9_ngram-v4-ls-p5e-8.lm --dry-run
```

同名同内容自动跳过；同名不同内容默认停止，`--replace` 才覆盖。拒绝上传 `.codex-artifacts`、`.git` 里的文件。完成后打印下载地址、SHA256 和大小，填进商店目录或模型清单。

`packager.cmd info` 输出 JSON（版本、上次交付、设备、模型、词库、预设），给别的脚本或 Agent 用。

## 和 auto.cmd 的关系

构建、签名校验、版本与哈希核对、安装、发布都复用 `scripts/auto-deliver.py` 的同一套代码，两者共用同一把锁，不会同时构建。打包器的构建记录在 `.gradle/packager/state.json`，版本是否已交付同时读两边的记录。产物仍在 `app/build/outputs/apk/editions/<版本>/<standard|full>/<release|debug>/`，回执 `latest.json` 多一个 `packager` 字段记录本次选项；选项不同不会误用旧包。

测试：`py -3 -B scripts/packager/test_packager.py`。
