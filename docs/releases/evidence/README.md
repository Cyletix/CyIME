# 每版证据记录

每个公开版本一页：`docs/releases/evidence/<版本>.md`。回答四件事：

1. **源码**：安装包是从哪个提交打的，这个提交在不在 main 上，标签指不指向它，发布说明在不在里面。
2. **内容**：轻量版、完整版各自的文件名、安装序号、字节、SHA256；包里的九键模型、离线模型、词库文件逐个哈希，并标出是否与模型目录（`BuiltinModelCatalog.kt`、`CandidateModelCatalog.kt`、`SpeechModelCatalog.kt`）里登记的哈希一致。
3. **核对**：单元测试、公开包审计、真机检查、回放、Release 下载回来核对哈希，各写结果和证据位置。
4. **设备**：哪些设备装了哪个版本、安装序号，和哪个包逐字节相同。公开包按规则不装到我自己的手机和平板，这时写「没装」，真机检查写用的是哪个同源开发包。

## 怎么生成

在 `D:/GitHub/CyIME`，打完公开包之后：

```powershell
python scripts/release_evidence.py --adb --github          # 当前版本
python scripts/release_evidence.py --version 1.14.103      # 指定版本
python scripts/release_evidence.py --print                 # 只看，不写文件
```

脚本只读，从这些地方取数：

| 内容 | 来源 |
|---|---|
| 版本、安装序号、字节、SHA256、构建提交、尺寸门禁、九键模型 | `app/build/outputs/apk/editions/<版本>/<轻量 standard／完整 full>/release/latest.json`（`build-apk.ps1` 和打包器写的回执） |
| 包里的模型和词库 | 直接打开 APK，`assets/` 下 1 MiB 以上的文件，以及 `bundled-models/`、`cell_dictionaries/`、`gloss/`、`rime_chinese/t9_ngram.lm` 全部 |
| 公开内容检查 | 打包器同一个 `public_violations`（私人释义包、已淘汰释义引用） |
| 单元测试 | `app/build/test-results/**/TEST-*.xml` 汇总；只按文件时间，不保证就是构建提交那次，所以手写部分仍要写清楚 |
| 设备（`--adb`） | `dumpsys package` 读版本，`sha256sum base.apk` 比哈希；只读，不安装、不截屏 |
| Release（`--github`） | `gh release view` 读是否草稿、资产哈希；不改 Release |

## 规则

- 自动部分夹在 `<!-- 自动生成：开始 … -->` 和 `<!-- 自动生成：结束 -->` 之间，重跑只换这一段；下面的「核对」「未验收」是手写的，重跑不动。
- 只收公开包。回执里带私人释义包、开发工具或老人/朋友包时脚本拒绝写，私人开发包的记录留在 [打包与版本规则](../../packaging.md)。
- 不写绝对路径、设备序列号（只写机型），不写 `D:/CyIME-Data` 里的任何私人内容，不写私人模型的文件名。
- 没做的写「未做」，不写「通过」；写不出证据位置的结果标「只有线程记录」。
- 本机没有构建回执（例如在云端补写）时，自动段可以手填，但要写明「手填，来源…」，等电脑上跑一次脚本替换。

## 已有记录

- [1.14.103](1.14.103.md)（草稿，手填，待电脑上跑脚本核对）
