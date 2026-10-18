# Extract Text 相册返回修复

2026-10-05，用户反馈选图后直接退回普通键盘。目标是选图后继续在键盘内移动、缩放图片，固定中央框识别，再沿用剪贴板编辑浮窗保存。

## 原因与修改

- 相册返回会打开 OCR，但异步的编辑器/引擎准备稍后调用 `resetKeyboard` 或切换手写主页，覆盖刚打开的工具；打开请求已消费，最终只剩普通键盘。不能只验证图像解码器或直接展示 OCR 组件。
- 保留已有输入会话校验；同一会话的准备结果只更新当前工具背后的键盘。以完成时的可见页面为准，取消 OCR 后不重新打开，用户切到其他工具时也不抢回 OCR。普通的新输入框初始化保持原规则。
- 相册结果回调重新保持待打开请求，等待窗口恢复焦点，包含取消选择及 Activity 恢复情况。
- 不改相机识别、裁切、编辑浮窗、键盘尺寸、候选序号规则和用户配置。版本名沿用 1.8.0，交付序号递增至 20261086。

## 检查

- JVM 22 项通过：页面状态 6、引擎准备 5、图像质量 3、相册坐标 4、中央框几何 4。Android 测试编译通过。
- SM-S9180 实际运行设备回归 5 项通过：延迟初始化后图片保留、移动时框不变、取消不重开；中央框裁切两项；复用剪贴板编辑浮窗及取消/重拍两项。测试没有模拟系统选择器作为已通过依据。
- Debug 真机操作：从键盘菜单进入拍照识别 → 相册 → 系统选择器选择专用 `GALLERY RETURN 123` 图片 → 完成 → 图片返回键盘内预览；横向移动后文字移动、中央框保持原位；点击识别得到完整 `GALLERY RETURN 123` → 原剪贴板样式编辑浮窗 → 保存 → 显示“已复制”及对应粘贴提示。未把文字输入或发送到其他应用。
- 已查看设备渲染：`.codex-artifacts/ocr-gallery-return-debug.png`、`ocr-gallery-moved-debug.png`、`ocr-gallery-review-debug.png`、`ocr-gallery-saved-debug.png`。自动测试的 `ocr-gallery-after-editor-preparation.png` 是把图片移出后的空白框，对应取消/移动边界，不用来冒充文字预览证据。
- 日志：`.codex-artifacts/ocr-gallery-return-checks-final-20261005.log`、`ocr-gallery-device-build-20261005.log`、`ocr-gallery-phone-tests-20261005.log`。
- 首次直接启动 Gradle 遗漏固定入口的 Windows UnixDomain 回退环境，报 `Unable to establish loopback connection`，未到编译。使用现有 `scripts/auto-deliver.py` 的同一 JDK/环境后通过；未改全局环境或新增交付入口。

## 交付与边界

主目录 `auto.cmd --serial R5CW13EYCSF` 已构建轻量签名 Release（5分48秒），2026-10-05 19:35:18 保留数据覆盖安装到 SM-S9180；固定入口回读设备 APK SHA-256 一致，独立读取版本为 1.8.0 / 20261086，包标记不含 DEBUGGABLE。文件为 88,716,591 字节，SHA-256 `d8275efcc4d45141fc6aa7407658dd35a8ccb0c0f53b776457199472c5b70d4e`，固定留档 `D:/CyIME-Data/deliveries/1.8.0/20261086/CyIME-1.8.0-arm64-v8a.apk`；构建回执同目录 `build-receipt.json`，安装日志 `.codex-artifacts/ocr-gallery-20261086-delivery.log`。源码指纹 `c6db7b5be3aaad6af10a772b109fc6c3259de7b69892047468878a0563bc921a`。

Debug 已实际检查上面的合成图完整流程；Release 安装后已打开设置页，进一步选图复核被用户新反馈中断，不能把 Debug 检查写成 Release 重复检查。用户随后提供实拍键帽字母无法识别的图，转入同图质量对比。用户实际图片验收仍保留未勾选；平板本轮未连接。黑屏实拍、旋转/倾斜/分散文字识别质量不属于这次页面恢复修复，原有未通过事项不改为完成。未提交 Git、未发布 GitHub Release。
