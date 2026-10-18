# Windows 剪贴板失败提示增量（2026-10-05）

本次仅改 Android 的 `WindowsSecureTransport`、`ClipboardSyncBridge` 及对应传输测试；不是 Windows UI、输入法或图标的 Android 待办。

唯一连接设备 SM-X800 / R52T800SKWZ 当前为 1.8.0 / 20261076。只读日志中存在重复 `IllegalStateException`，未记录 HTTP 状态，无法据此认定占用、认证或具体同步方向是根因。没有读取剪贴板文本，也没有接管设备 UI。

新增 `TransferFailure(status)`，区分配对撤销、临时冲突、超长文本、格式不兼容、剪贴板占用；上传与下载均保留实际响应码。日志只包含异常类别及 HTTP 数字，不含文字、请求地址、设备凭据或任意异常正文。已有 Toast 节流保持，通知采用固定原因文案；不修改认证或弱化证书验证。

针对 `WindowsSecureTransportTest` 的主机检查通过，包含 401/409/413/503 上传及下载错误、连接更改时拒绝旧回复、权限映射及取消传播。日志 `.codex-artifacts/windows-sync-unit-20261005.log`。最初直接 Gradle 启动遇到 Java Unix socket 错误，随后沿用固定交付入口已有的完整 JDK 21 与禁用 Unix-domain IPC 参数，未修改系统 Java 或清理缓存。

按默认授权运行 `auto.cmd` 构建轻量 Release 并覆盖安装。执行时源版本已为 1.8.0 / 20261077，本任务没有覆盖这一版本选择。构建失败在同时变化的 `OcrRecognitionService.kt`：它引用 `DocumentOcrEngine.Session`、`recognize`、`close`，当时源代码没有对应接口。没有替换相关 OCR 实现或用旧 APK 冒充新版本，设备保持 20261076；本轮失败提示未安装、未验收。日志 `.codex-artifacts/windows-sync-delivery-20261005.log`。

随后发现 OCR 接口已由其他工作补齐，重试同一固定入口。重试开始时源版本为 1.8.0 / 20261078，Release 编译成功（5 分 50 秒）；但构建期间 `DocumentOcrActivity.kt`、`OcrCameraPanel.kt` 及版本源文件再次变化，入口明确拒绝安装来源不确定的产物。保留该保护，没有直接 ADB 安装绕过检查，也没有还原其他工作。日志 `.codex-artifacts/windows-sync-delivery-20261005-retry.log`。Android 工作区持续改动期间不反复重构，待其交付批次稳定后由固定入口统一包含这三处同步修改。

最终只读复核发现设备已由其他工作更新为 1.8.0 / 20261078：实际 APK SHA-256 为 `c507f20ee9dbcd6e23eaa429379e2ad6ae3fec80fdc21c4a78e526b8bcc3fa81`，与本轮生成文件的 `9053808acf8f8cd8c88e1681f6cebb55dbc38e451de897e31b5ebabd7baeeeb3` 不同；源码版本此时又为 20261079。不能据同一个版本号把其他安装记为本轮成功；新增错误提示是否包含在实际 APK 尚未确认。之后没有新的 ClipboardSync 错误记录可用于判断实际 HTTP 码，故同步故障根因仍待定位。
