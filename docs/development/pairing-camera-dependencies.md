# 配对扫码依赖记录

2026-10-04，按本地 Gradle 已解析 POM／AAR 核对；不是新 APK 分发声明。

- 直接依赖：`androidx.camera:camera-camera2:1.6.2`、`camera-lifecycle:1.6.2`、`camera-view:1.6.2`，来自 Google Maven；用于 CameraX 预览和逐帧分析。源码不修改。原有 ZXing 用于 QR 解码，没有再次引入二维码库。
- CameraX 传递模块：`camera-core`、`camera-video`、`camera-camera2-pipe`，均为 1.6.2；此外包括 `androidx.camera.featurecombinationquery:featurecombinationquery:1.6.2` 和 `androidx.camera.viewfinder:viewfinder-core:1.5.1`。
- POM 还声明 Media3 1.9.0、Dagger 2.59、atomicfu 0.28.0，以及已有 AndroidX lifecycle/core/concurrent/tracing/annotation、Guava、Kotlin/coroutines 等依赖。POM 下限不是项目冲突解析后的最终版本；下一次明确打包时以实际 resolved graph／APK 为准，不将此列表称为完整依赖锁。
- CameraX POM 声明 Apache-2.0，camera-core 另声明 BSD-3-Clause；已将六个 CameraX AAR 原始许可证逐字保存到 `app/src/main/assets/licenses/camerax/`，并保存 LibYuv 上游 BSD 版权及条款，更新应用 NOTICE。保留这些许可证与版权，不将原作者署名抹去。
- 来源：[AndroidX CameraX 1.6.2](https://developer.android.com/jetpack/androidx/releases/camera#1.6.2)、[AndroidX 源码](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/camera/)、[LibYuv 许可证](https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/LICENSE)。AAR 来自本机 `~/.gradle/caches/modules-2/files-2.1/androidx.camera/`，本次读取的是 1.6.2 对应产物；LibYuv 链接保留上游版权来源，不声称已锁定 CameraX 所用的全部原生传递源码版本。

摄像头权限只在用户打开扫码时请求；设备无需具有摄像头才能安装，仍有相册入口。Android 真机扫码、权限拒绝恢复和实际 APK 内容尚未验收。
