# 剪贴板实时扫码与相机复用

最新进展：用户在入口补修后的平板测试包明确反馈“ok, 扫码连接成功”。因此本次实际扫码配对通过；原来因旧配对记录隐藏入口的问题也已修复。关闭/重开扫码再切换OCR的测试已重跑通过，当前累计修复与下一交付见[本轮跟进](layout-preview-ocr-followup-20261005.md)。下文20261081属于前序检查历史，不再指示安装旧包。

## 目标与边界

点“扫码连接”后直接在 CyIME 内实时预览，对准 Windows 配对码自动解码并交给原配对流程；不再走系统相机拍照、大图保存、再解码。复用 Extract Text 的相机采集/对焦/释放，二维码仍使用独立解码器，不调用 OCR 或下载模型。不改变已有设备信任、证书校验、同步配置或 Windows 安装。

主目录当前源码已经存在 CameraX 扫码入口，不能仅依据用户描述就断言本轮设备运行的是当前源码；手机此前未安装最近的平板测试包。原先测试只有整张干净的 QR 和90度旋转，没有验证相机 Y 平面、真实预览生命周期、透视和噪声，属于检查范围不足。

## 实现

- OCR 与扫码共用 `CameraPreviewSession`：同一预览/分析 ViewPort，KEEP_ONLY_LATEST，工作线程串行分析，每帧必关闭，只释放自身 use case；中央自动对焦、点画面对焦，关闭时熄灯。
- 扫码首选约720p分析，每150ms最多分析一帧；直接读取 Y 灰度平面并处理 buffer offset、rowStride、pixelStride、cropRect，不转 JPEG/RGB 或写照片文件；即使设备选择更大尺寸，分析长边仍限制1280像素。
- 使用已有 ZXing 3.5.4 QRCodeReader，先局部自适应二值化，失败后全局直方图，再尝试反色；不人为压低全部对比度或抬高统一阈值，避免细二维码网格丢失。
- 相册导入先读尺寸，采样至长边不超过1600，读图/解码不占主线程；导入期间暂停实时相机。
- 扫到有效配对码只回调一次；普通二维码和不支持的配对码分别提示，不当作“未识别”；普通链接不会提交给连接逻辑。二维码过期由原配对流程明确提示。
- 拒绝权限可重试，从系统设置返回重新检查权限；相机启动失败可重试或导入图片。

Windows 当前 `PairingWindow.RenderCode` 使用 QRCoder 1.8.0、ECC M、4像素/模块，有白边并以最近邻显示；保留简化二进制 v2 与旧 JSON v2兼容。测试图片由相同 Windows 库、相同参数和公开假凭据向量生成，不读取或保存真实配对秘密。

## 参考依据

- [ZXing HybridBinarizer](https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/common/HybridBinarizer.java)：局部阈值针对阴影/亮度渐变设计，低动态区域不按普通平均值制造黑白噪声。
- [ZXing QR Detector](https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/qrcode/detector/Detector.java)：已有定位与透视处理；因此不再编写自制 QR 检测算法。
- [Android CameraX ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze)：生命周期绑定、逐帧分析、最新帧背压及关闭 ImageProxy 的要求。

本轮无新第三方依赖。所用参数是受控的采集/计算预算，不是“人能看见就必然识别”的保证。

## 检查与交付

本地16项回归通过：CameraLuminance 3、PairingQrCamera 4、WindowsPairingProtocol 5、OcrScanGeometry 4；覆盖带padding/偏移/像素步长的相机平面、缺失尾部检查、超尺寸上限、旋转/反色、透视叠加噪声和亮度梯度、空画面及两版配对协议。日志 `.codex-artifacts/pairing-scanner-build-final.log`。

SM-X800 安装同签名 **1.8.0 / 20261081 Debug**，数据保留。真实Android解码器读取Windows QRCoder生成的244×244测试图片通过，本次冷读116ms（单次数据，不代表相机端到端延迟）。实际扫码相机已收到帧，关闭后重开也收到帧；已查看 `.codex-artifacts/pairing-scanner-verification/scanner-first.png` 与 `scanner-reopened.png`，取景、引导框、对焦说明及底部操作无裁切。该图使用测试Activity的默认MaterialTheme，不能当作所有应用主题已验收。

设备测试总体为2项中1通过、1异常：后者的两次扫码预览/关闭步骤完成，随后切换OCR时测试轮询在短暂无Compose根节点的转场抛异常，未完成OCR复用检查；已将轮询改为允许空根等待，尚未重跑。不能把扫码子步骤成功写成整项测试通过。日志 `.codex-artifacts/pairing-scanner-device-tests.log`。

用户在此时明确表示将亲自扫码测试；已通知测试包可用，并停止所有前台设备自动操作，不在用户扫码过程中安装覆盖或运行测试抢占相机。轻量Release继续后台构建，安装待本次用户扫码结束；真实屏幕拍摄、配对成功及OCR切换待验收。

随后 `auto.cmd --build-only` 成功完成 **1.8.0 / 20261081** 轻量签名 Release，APK 88,680,219字节，SHA256 `964bf242ea198e8c3e9e86ebc9814fec22401250d3c88117ac9b99252da333fd`；固定留档 `D:/CyIME-Data/deliveries/1.8.0/20261081/CyIME-1.8.0-arm64-v8a.apk`。平板仍是同源码Debug测试构建，正式包尚未覆盖以免打断用户扫码；未发布GitHub，手机未安装。后续用户扫码结束后沿用 `auto.cmd --install-only`，不重建同一源码产物。

构建过程：首次直接调用 Gradle 未带统一入口已有的 Windows Java IPC 环境修复，在项目编译前失败；使用同一 JDK/IPC 参数恢复。随后发现手动调试构建未采用已有 ARM64 配置，及时中止多架构构建，改用 `scripts/phone-release-abi.gradle`。默认交付仍使用 `auto.cmd`，没有新增让用户试错的交付脚本。
