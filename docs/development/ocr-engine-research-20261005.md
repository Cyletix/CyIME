# 相机 OCR 识别质量复查与替代方案

## 结论与当前状态

### 2026-10-05 键帽实拍反例

用户在 20261086 安装后提供 `1000614814.jpg`：中央框内清楚可见分散的 Z、X、C、V、B、N，预览却显示未识别。20261086 修复的是相册返回被键盘初始化覆盖，不包含识别引擎改善，不能以其合成图流程测试推断真实拍摄质量合格。

已将原图作为固定输入 `app/src/androidTest/assets/ocr/keycaps-reported.jpg`，只取截图中央识别框内部（660×1280 原图的 x=36、y=1017、w=589、h=77），排除边框、其他键盘行和界面提示。这些坐标与预期字母只在回归样本中使用，生产算法没有键盘字母表、按键顺序或此图坐标分支。

**已在电脑执行真实模型验证，并更换应用识别路径。** 旧 Tesseract 5.5.1 / Leptonica 1.85.0 与 APK 相同版本、相同 chi_sim+eng fast 数据，在原始条带上输出 `FS`、平均置信度31，被55门限挡掉；放大、分割模式与自适应阈值处理仍夹杂键帽边框乱码。不能用降低门限解决。记录在 `.codex-artifacts/ocr-host-tesseract-baseline.json` 和 `ocr-host-preprocess.json`。

PP-OCRv4 仍漏字；PP-OCRv5 mobile 的检测→逐区域透视裁切→CTC识别，在足够的文字像素分辨率下完整输出 `Z X C V B N`。应用的 `DocumentOcrPipeline.java` 与电脑回归编译使用同一份源码，不把 Python 原型结果代替移植后的验证。模型随包离线，CameraX、固定识别框、相册缩放与剪贴板编辑流程不变。原 Tesseract native 依赖和比较用 ML Kit Debug 依赖移除；旧训练数据只留作基线，不再进入 APK。ONNX Runtime Java与既有C++部分均使用1.28.0，已核对arm64原生库SHA256相同。

电脑回归固定入口：

```powershell
python tools/ocr_lab/run.py --output .codex-artifacts/ocr-host-final
# 额外的倾斜反例仍失败，保留严格预期，不按实际错误输出改断言：
python tools/ocr_lab/run.py --exploratory --output .codex-artifacts/ocr-host-exploratory
```

脚本优先使用仓库构建已装的 JDK21，按哈希复用 D:/CyIME-Data/ocr-lab 中的桌面依赖，既不调用ADB，也不写C盘大型模型/包。最初电脑入口误选系统JDK17，ONNX DLL初始化失败；固定入口改为优先选择构建JDK21，失败不会伪装成识别通过。ONNX Java读取模型元数据时会截断补充平面Unicode字典，已改为随模型附带原始UTF-8字符表并核对CTC维度，防止文字索引整体错位。

验证与边界：

- 生产Java代码的13个电脑样本通过：用户原图、0.8×/1.5×缩放、真实无字暗纹理、黑/灰/白、暗噪声、低对比度噪声、渐变、黑/白底标点英文、中英多行。原图6个区域实际置信度约0.888、0.842、0.919、0.877、0.984、0.980，输出为 `Z X C V B N`。
- 结果与代码/模型/输入哈希：`.codex-artifacts/ocr-host-final/{manifest.json,results/results.json}`；定位图为 `results/keycaps-reported.png`，已实际查看。初次单独运行原图约430–500ms；并行构建时更慢。**这些是电脑时间，不是手机帧率**。
- 额外旋转±8°样本仍漏字，结果为 `Z C V B N` / `Z C B N`；保留失败。宽倾斜区域识别失败时最多再次检测一轮已校正区域，不无限重试。不宣称全部旋转/透视照片已合格。
- Android主代码、Android测试源码编译通过；11项OCR裁切/图片几何/质量门控JVM测试通过，日志 `.codex-artifacts/ocr-ppocr-compile.log`。新增用户实图Android测试已编译，按用户指示未使用ADB执行。桌面OpenCV为4.9，Android为4.12，仍需设备运行确认平台差异与实时耗时。
- OCR继续在独立`:ocr`进程的串行后台worker中运行；ONNX每session两线程且禁用空转，OpenCV单线程，检测输入约1MP上限，最多64区域且仅一次倾斜区域重检。保留曝光/低纹理预检及连续帧稳定机制，不将OCR任务加入打字队列。
- 交付目标沿用当前批次1.8.0，递增为20261087；本轮只生成轻量包，未做ADB安装和真机体验验收，不覆盖已安装的20261086归档。最终包回执见本文末尾。

本轮走弯路：明明已有用户原图，却先加Android专用对比测试并要求连接ADB，导致把可在本机完成的算法验证错误地绑到设备上。比较Debug包21:12构建完成但没有实际运行，不能算准确率证据。该比较依赖与临时测试已移除，保留固定电脑入口；以后先在真实失败样本上验证，再接应用与设备，不以合成图通过推断真实拍摄合格。

### 更早版本的研究记录

以下描述为替换前的历史状态。2026-10-05 较早版本已增加识别前质量检查和连续帧预览稳定，相关19项本地与11项设备回归曾通过；见[跟进记录](layout-preview-ocr-followup-20261005.md)。这些历史测试没有覆盖本次键帽图，也不代表所有实拍已通过。

用户实际拍摄反馈：旋转、倾斜、多处独立文字识别失败，纯黑画面产生大量符号。当前 Tesseract 版本的识别质量**验收未通过**。先前通过的中英文正向合成图测试只能证明模型能执行，不能证明实时拍摄可用；此前验收范围选择错误。

本轮在 SM-X800 的键盘相机中也实际看到暗色、低纹理取景产生大量无意义字符，图像证据为 `.codex-artifacts/ocr-entry-verification/ocr-no-text-false-positive.png`。该图证明真实预览存在误识别，不把它等同于标准纯黑测试图或完整角度测试。

本次查阅 GitHub 项目、官方文档及识别流水线源码，尚未以同一批真机照片对三个替代引擎做准确率、延迟、耗电比较，不能将下文的适配判断写成实测结论，也没有据此直接更换生产引擎。

## 原 Tesseract 实现的具体缺口（历史）

- `DocumentOcrEngine.Session.recognize` 在相机分支使用 `PSM_SINGLE_BLOCK`，把中央区域当单一规整段落；中央取景区域不等于单一文本块。
- 没有独立的文字检测与逐块四边形校正，分散、倾斜的文字直接交给识别器。
- 相机传感器朝向已转正，但纸张/被摄物旋转是另一个问题，没有文本方向判断。
- `getUTF8Text` 原样返回，没有使用识别置信度、空画面/低对比度判断或预览稳定机制，低质量结果直接展示。
- 预先截取细长条会截断倾斜文字。后续应在有适当上下文的画面检测，再按中央框筛选文字块、校正和识别；仍只输出中央框选中的内容，不能擅自扩大到整个画面。

Tesseract 官方明确指出倾斜会明显破坏行分割，且默认分割假定整页文本；这支持上述原因分析，但不能证明用户每一帧失败的唯一原因。[官方质量说明](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html)

## 候选方案

| 方案 | 已核实能力 | 本项目适配与限制 |
| --- | --- | --- |
| [PaddleOCR 官方 Android SDK](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/inference_deployment/cross_platform/android_deployment.md) | Kotlin/Compose 示例，ONNX Runtime，检测→四边形裁切→逐块识别，返回坐标和置信度，SDK/AAR 可独立接入 | 优先考察的开源路线；当前示例主流程没有独立方向分类，不能声称直接支持所有倒置情况。默认识别置信阈值为 0，不能原样照搬；移动端模型与线程/内存需实测。 |
| [Google 官方 ML Kit 实时相机示例](https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/README.md) | 相机/CameraX实时识别；中文可选随包内置模型，结果有块/行、角点、置信度 | 适合作为实时相机基准，接入成本较低；示例开源但识别 SDK 并非开源引擎。使用 bundled 依赖避免运行时下载；中文实时速度与倾斜/倒置正确率需测。 |
| [RapidOCR Android Kotlin/Compose 示例](https://github.com/RapidAI/RapidOcrAndroidOnnxCompose) | ONNX+OpenCV，检测、方向分类、识别；逐块透视变换和高竖框旋转 | 最接近现有技术栈的参考实现；README记录2022年的依赖和PP-OCRv3模型，不能整份直接搬入。方向分类实际为正/倒二分类，并非万能方向/版面理解。 |

源码核对：

- [Paddle OCREngine](https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/ppocr-android/ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt)：先检测，无框即空结果；排序、裁切、识别后按分数过滤。
- [RapidOCR OcrEngine](https://github.com/RapidAI/RapidOcrAndroidOnnxCompose/blob/main/OcrLibrary/src/main/java/com/benjaminwan/ocrlibrary/OcrEngine.kt) 与 [OcrUtils](https://github.com/RapidAI/RapidOcrAndroidOnnxCompose/blob/main/OcrLibrary/src/main/java/com/benjaminwan/ocrlibrary/OcrUtils.kt)：三阶段处理、逐块几何校正，方向分类命中后旋转180°。
- [ML Kit Android 接入](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)：内置与运行时下载两种依赖要区分，内置模型无需首次下载；官方估计每文字体系每架构增加约4MB，不是CyIME最终包增量实测。
- [ML Kit Text.Line](https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Line)：角度、角点和0～1置信度；SDK返回角度不意味着任意倾斜都能准确识别。

## 下一次引擎替换的通过条件

保留键盘内预览、中央框、编辑、确认复制的产品流程。识别底层先做同图对比，再选择引擎；不得只把 Tesseract 换成一个名字就宣称完成。

1. 中英文正向、±15°/±30°倾斜、90°/180°/270°旋转、两至三处独立文字、透视拍摄分别记录漏字/错字与块顺序。
2. 纯黑、纯白、遮镜头低照度噪声、无字纹理应返回无文字；不能仅删除符号，因为真实文稿也有标点、公式和编号。
3. 传感器旋转、被摄物旋转分别测试；中央框内外混合文字不越界输出，倾斜块不因先裁切而丢字。
4. 预览结果应稳定，确认识别取当前帧；切换/关闭取消过期结果，不让OCR积压阻塞打字。
5. 同一设备记录冷启动、热帧、内存及实际输入响应；保留独立进程和单任务在途，不能为了识字质量损害输入稳定性。

以上为研究与待验证的实施依据，不是已完成的功能清单。

## 20261087 构建与交付回执

- `auto.cmd --build-only` 从 `D:/GitHub/CyIME` 完成轻量 Release 构建（8分36秒），固定入口校验版本、签名、非Debug、源码指纹后返回 DONE；没有调用ADB安装或发布GitHub。
- 归档：`D:/CyIME-Data/deliveries/1.8.0/20261087/CyIME-1.8.0-arm64-v8a.apk`，110,997,213字节，SHA256 `ff524b52878d7f82334a4ff166bf1502155c0476f7ec739b20b171375b71a01c`。
- 构建来源指纹：`7c900e4539f3962e84e7d4b6a3a9ad987dde064ca8b38803441e3f7c79907139`。原始回执与额外包内容检查分别为同目录 `build-receipt.json`、`ocr-verification.json`。
- APK仅含arm64 ABI，三个模型/字典文件与电脑已验证输入逐字节相同；没有旧.traineddata、Tesseract库或用户回归照片。OpenCV arm64的ELF LOAD对齐为16KB。ONNX AAR与C++使用的未strip原库一致，APK则与Gradle已strip输出一致；首次额外检查误拿APK的已strip字节对比原库而失败，已改为分别核对“输入→合并”和“strip→APK”，不是忽略哈希差异。
- 编译成功、13个本机样本通过、11项JVM测试通过、Android仪器测试只编译、真机未运行、用户未验收，分别记录，不互相替代。
- 本轮结束时尝试清理已退出的OCR桌面进程留下的native临时解压目录；所有目标均限制在`D:/CyIME-Data/ocr-lab`、限定工具生成的名称且排除重解析点，但自动审批仍拒绝，工具只返回`blocked by policy`，未说明更具体原因。未改用其他命令绕过，临时目录保留。此事不影响OCR结果或APK校验。