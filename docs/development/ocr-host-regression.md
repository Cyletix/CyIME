# OCR 实图回归

在电脑运行应用的 `DocumentOcrPipeline.java`，无需连接手机。输入使用用户失败原图的中央框，输出JSON、模型/源码哈希和实际检测区域图；生产代码不包含样本坐标或预期字母。

```powershell
python tools/ocr_lab/run.py --output .codex-artifacts/ocr-host-check
```

需要Python/Pillow和JDK21；默认优先复用`~/.gradle/jdks/jdk-21*`，也可传`--java-home`。依赖下载和native临时解压默认放在`D:/CyIME-Data/ocr-lab`（可传`--cache`），按固定SHA256验证。模型使用仓库内`app/src/main/assets/ocr/ppocr-v5`，不下载另一套未验证的权重。此检查不触发打包、安装和输入法切换。

默认17例包括原图与缩放、无字实拍纹理、空白/暗噪声/渐变、黑白底标点、中英混排和键帽符号（`M < > ? Shift`，检测模型不给孤立符号出框，靠补找漏掉的小符号并连同本行文字识别），任何不匹配均返回非零退出码。`--exploratory`额外运行尚有漏字的±8°旋转图（预期全部六个字母）、单独出框却被读成`V`的`>`、以及双字符键帽下排的`, . /`；已知失败不会当成通过。详情见[识别质量记录](ocr-engine-research-20261005.md)。

电脑使用ONNX Runtime1.28/OpenCV4.9，Android使用相同模型与Java源码、ONNX Runtime1.28/OpenCV4.12；本机识别通过不等于Android运行和用户体验已验收。
