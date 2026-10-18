# 剪贴板误收录相机照片修复（2026-10-07）

## 原因与规则

用户反馈拍照后大量照片占满剪贴板。代码中“启用最近照片”开启后，`ClipboardImages.queryRecentPhotos()` 只按 MIME 读取 MediaStore 最新 60 张图片，与真正复制的图片历史合并；它没有判断截图来源。不是系统把每张相机照片都复制进了剪贴板。

本轮只收紧这条可选相册来源，用户的显式图片操作保持不变：

- 相册自动来源仅显示最近截图，菜单改为“启用最近截图／关闭最近截图”。保留原有开关与权限状态，旧 `recent_photos` 键继续读取，但语义收紧。
- 截图依据为目录/相册名（例如 `DCIM/Screenshots`、`Pictures/Screenshots`、截图、截屏、スクリーンショット），或以常见截图前缀和日期开头的文件名。不能把任意包含 screenshot 的下载图片都当截图，也不依赖 JPEG/PNG、图片尺寸或像素内容猜测。
- 在 MediaStore 查询层先筛候选，再逐项严格验证，最终最多显示 60 张合格截图；不先截取最新 60 张相册照片再过滤，避免连续拍照挤掉稍早截图。Android 9 不请求 Android 10 才加入的 `RELATIVE_PATH`，仍使用相册名/文件名。
- 真正写入系统 ClipData 的照片、手动选图、已有图片历史和固定内容不按截图规则删除。自动相册列表刷新后普通照片不再混入；不删除相册原图或清空用户已复制历史。
- 新复制图片的单次粘贴提示仍由原 ClipData 路径触发，最近截图列表不会自动制造复制事件或反复提示。

## 能力边界

这是明确的元数据筛选，不是从像素辨别拍照与截图：被移到截图目录的普通照片可能满足规则；重命名且移出截图目录的截图可能漏掉，仍可显式复制/选图。三星键盘内部规则未核实，不以“和三星完全一样”作交付结论。

[MediaStore 的公开字段](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns)可读取名称、目录等信息；[Android 14 截图回调](https://developer.android.com/about/versions/14/features/screenshot-detection)只针对可见 Activity 且不返回截图图像，不能把它当成输入法全局读取其他应用截图的接口。本轮不新增权限或设备截图监听 API。

## 验证与交付

本轮目标为兼容修复 **1.9.7 / 20261099**，上轮 1.9.6 已实际安装，故递增 PATCH。唯一开发/构建目录 `D:/GitHub/CyIME`。

- 新增来源正反例：三星/Pixel 文件名、改过保存目录的截图、裁剪/改名截图、中文/日文目录、元数据缺失；普通 Camera/PXL/IMG、下载示例图和 OCR 导出不得误收录。
- 显式复制普通相机照片仍可识别为图片；原一次提示与关闭行为继续回归。
- 新增 Android 私有内存 SQLite 查询用例，覆盖 90 张较新相机照片及 70 个误导文件名不会挤掉较早截图，兼容有/无 `RELATIVE_PATH`。测试不触碰用户相册。
- 20 项相关 JVM 测试通过（来源筛选 6 项、显式复制来源 4 项、一次提示状态 10 项），日志 `.codex-artifacts/clipboard-screenshots-20261007/check.log`。
- 用已编译的生产 `ScreenshotImageSource` 输出查询及逐项判定，在电脑 SQLite 中回放 Android 9/10+ 两种字段形态；每组 163 行含 90 张较新相机照片、70 个误导命名，最终只保留 3 张截图。相机照片没有进入 SQL 候选；误导命名在取 60 张前被严格过滤。结果 `.codex-artifacts/clipboard-screenshots-20261007/host-query-results.json`，复跑命令 `python .codex-artifacts/clipboard-screenshots-20261007/check_query.py`（需已编译 Debug 类）。这是电脑同源逻辑/SQL验证，不等于设备 MediaProvider 验收。
- Android 查询用例已编译成测试 APK；检查期间及打包后 ADB 均无设备，未执行设备查询、系统相册及菜单实际渲染验证，不以单测或编译代替。

固定入口 `auto.cmd` 已构建并验证 **1.9.7 / 20261099 轻量 Release**，签名、实际版本及源码指纹检查通过，输出为 `INSTALL-PENDING`；未安装、未公开发布。本轮仅改来源判断和菜单文案，无布局尺寸改动，未跑整套尺寸门禁。

- 固定留档：`D:/CyIME-Data/deliveries/1.9.7/20261099/CyIME-1.9.7-arm64-v8a.apk`。
- 文件大小：111,043,125 字节。
- SHA256：`e30960bd3003c77fba08b508eaa204b0557ee271e30e4c2e2c1015e0f37896d7`。
- 构建源码指纹：`8a9fb056a0609951789bfc8853ab2c3aa0b5b9c124d91e3fe07a2e997f4c0dc1`；同目录保留 `latest.json` 和 `delivery.json`，复制后哈希一致。
- 构建日志：`.codex-artifacts/clipboard-screenshots-20261007/delivery.log`。连接已授权设备后使用同一固定入口覆盖安装，不为重试另升号。
