# Windows 剪贴板已配对但不传输：Android 修复

2026-10-05。用户明确授权从 Windows 任务进入 `D:/GitHub/CyIME` 修复 Android 端。本轮修改直接位于主目录，保留此前未提交内容，没有清数据、改配对、接管手机、打包、安装或提交。

## 原因和改动

原生 Windows transport 的 `push` / `pull` 直接同步调用 OkHttp，而 `ClipboardSyncBridge` 的会话在 `Dispatchers.Main.immediate` 执行。配对与连接探测调用者使用 IO 调度，因此连接成功不意味着文本请求路径可用。此前异常仅写入类型日志，用户看不到失败提示。

- `WindowsSecureTransport.kt`：上传、拉取和探测都在 transport 自己的 IO 边界内执行网络请求；返回后继续在调用方线程校验设备身份、处理 ETag。保留证书固定、局域网限制、不重定向、令牌授权及正文校验。探测不修改传输去重状态；取消异常继续传播，不能伪装成普通连接失败。
- `ClipboardSyncBridge.kt`：原生 Windows 同步失败增加系统 Toast，每 30 秒至多一次，不包含正文、地址或凭据。剪贴板读取、比较和写入仍属于主线程，会话序列和回声抑制保持原样。
- `WindowsSecureTransportTest.kt`：增加可注入网络边界的定向测试，使用假请求与内存剪贴板，不读写真实设备或桌面剪贴板。

## 后台检查

Gradle 离线串行执行，单 worker，单次 JVM 上限 2 GiB，Kotlin 编译在进程内；没有运行 assemble/package/install 任务。

| 检查 | 结果 |
| --- | --- |
| app Debug Kotlin、Java 与单测编译 | 通过 |
| WindowsSecureTransportTest | 3 项通过：实际会话的网络离开调用线程、剪贴板读写返回 owner；Unicode 上传/拉取；在途切换设备拒绝旧结果；探活保留 ETag；取消异常不吞掉 |
| WindowsPairingProtocolTest | 5 项通过：二维码与身份、扫码解码、局域网地址、UTF-8/哈希、连接码 |
| ClipboardSyncSessionTest | 13 项通过：既有会话、回声抑制、冲突及重试行为 |

第一轮 Gradle 启动失败于本机 JDK `UnixDomainSockets.connect`（`Invalid argument: connect`），尚未进入项目编译。将本次命令的 `TEMP` / `TMP` / `java.io.tmpdir` / `jdk.net.unixdomain.tmpdir` 指向仓库 `.codex-artifacts/java-tmp` 后通过；没有修改系统环境变量或用户 Gradle 配置。最终日志：`.codex-artifacts/clipboard-io-tests-final-20261005.log`；包含会话测试的前轮日志：`.codex-artifacts/clipboard-io-tests-20261004.log`。原有废弃 API 等编译警告未作为本轮无关重构处理。

没有安装新 APK；现有手机仍使用旧代码，需后续更新包才能生效。真实手机复制到电脑和失败 Toast 显示仍未验收。本任务的待办与手动确认沿用用户指定的 `D:/OneDrive/Notebook/@Inbox/2026-10-04 162231 CyIME Windows todo.md`，没有往 Android 调试 todo 写 Windows 项目条目。

## 同批继续：自动发现优先及同步方向

附近连接的电脑确认成功后自动保存手机凭据，取消手机第二次重复确认；仅保存失败时保留重试入口。发现启动失败与已信任设备认证失败按 1/3/8/15/30/60 秒退避；新地址和唤起键盘立即唤醒探测。旧发现回调通过代次隔离，避免停止后污染当前列表。NSD 仍由系统管理，未改为高频主动扫描网段，也未建立未经确认的信任。

Windows status/403 返回真实方向与暂停状态，手机转换为自身收发权限。ClipboardSyncSession 的可选 allowPush 默认 true，不改变旧插件行为；禁止上传时丢弃待上传项，让允许的接收继续进行，不积累暂停期间的复制。显式暂停不弹失败 Toast，断网等真实错误仍限频提示。传输和探活仅在内部网络阶段离开调用线程，状态回到原会话线程处理。

本批检查：`.codex-artifacts/clipboard-lan-first-20261005.log` 中 transport 5、协议 5、会话 13 项全部通过（新增方向映射、接收模式不被手机副本阻塞）。随后将 WindowsDevices 探活外层多余的 IO 包装移除，让权限更新回到 Main；最终复查先被 `applyCyimeSourcePatches` 中现有 librime 补丁冲突阻塞。确认该任务只处理原生源码及 Rime 资源后，仅 JVM 单测排除该任务，再因离线缓存缺少 `cz.adaptech.tesseract4android:tesseract4android:4.9.0` 失败于 AIDL 依赖解析，未进入最终 Kotlin 编译。没有修改原生补丁、OCR 依赖或其他会话文件；不能把先前通过等同最后完整构建通过。相关失败日志为 `clipboard-lan-first-final-20261005.log`、`clipboard-lan-first-jvm-20261005.log`。

Windows 页面、权限及状态记录见 `D:/GitHub/CyIME-Windows/docs/CLIPBOARD_PAIRING.md`。两端仍未安装、未验收；网络换网恢复及首次电脑确认流程需要后续真机验收。
