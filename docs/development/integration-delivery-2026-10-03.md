# 1.4.1 集成交付（2026-10-03）

基于 main `81fa341b`，集成 `hardware-keyboard-follow`、`language-layout-selection`、`clipboard-toolbar-visibility` 三个功能目录。当前源码位于 `codex/ime-fixes-integration`；未提交 Git，未上传 GitHub Release。版本 1.4.1，交付序号 20261055；Release 普通包已覆盖安装到 SM-X800，保留数据。

## 本次补修

物理键盘浮条的触摸接收区域随视觉浮条移动，会使连续等距移动在局部坐标中看似静止，导致交替丢失 MOVE。54 诊断记录中，60px 后的 80px MOVE 未进入回调，直到 100px 才继续。55 将小范围拖动接收区域与视觉位移分离，手势期间接收区域固定，松手后跟随新位置；宿主空白仍可穿透，操作按钮保持独立。

## 验证

- 53：72 项相关 JVM 检查通过。亮屏设备检查共 16 项，14 通过、连续拖动 1 失败、实体键盘用例 1 跳过。最初息屏检查无 Compose 界面，结果作废；没有将其记成应用通过。
- 55：SM-X800 上工具条 6 项全部通过，覆盖连续五段等距移动、边缘吸附、横竖切换、窄窗入口和空白穿透。未降低位移断言容差。
- 语言选择 4 项与剪贴板 1 项已在 53 真机通过，55 未改其代码；包括大字体、低高度、确认按钮可达，剪贴板三入口和展开手写状态下导航保留及切换操作。
- 候选显示 4 项在 53 真机通过，55 未改候选代码；共享单行候选、长词滚动和位置避让正常。
- 当前无外接实体键盘，实际 IME 光标订阅与六键翻页综合用例跳过。此前三星笔记拒绝光标订阅并返回零坐标，跨应用跟随问题仍未解决；不宣称通用兼容或用户验收。
- 完整尺寸门禁按约定未运行。未清除用户配置或历史。

## 复现材料

本目录 `.codex-artifacts/` 保存 `integration-device53-awake.log`、`integration-device55-toolbar.log`、`integration-build55.log`、`integration-release55.log`。`integration-sources.json` 记录集成来源；`integration-source-manifest55.json` 和 `integration-source55.patch` 记录打包源码。

原生引擎未改，复用已校验正式 1.4.0 的四个本地 JNI 库，其他库由锁定依赖提供。53 的七个原生库逐一比对正式包一致，见 `native-verification53.json`；避免混入主目录正在修改的九键原生代码。资源准备和词库哈希检查实际运行，未使用跳过资产准备的 UI 编译脚本。

## 最终交付

- Release 构建成功，版本 1.4.1 / 20261055，arm64-v8a 普通包，470963361 字节；非内置模型版。
- 包位置：`D:/CyIME-Data/deliveries/1.4.1/20261055/CyIME-1.4.1-arm64-v8a.apk`。
- SHA-256：`822d1b4c376782f9d64892e5b220402c71beb8b23ff2feb133046720dc534d5c`。设备 `pm path` 返回的实际 base.apk 校验一致，版本核对一致。
- apksigner 验签通过，与正式 1.4.0 的证书指纹一致：`e0d419cbfc7715e0be5bd98e7e5a26ab7d147a67b15d0961797197e6c76c05e2`。既有证书 DN 为 Android Debug，本轮沿用原身份，没有更换密钥；构建变体为压缩后的 Release。
- 最终包七个原生库与正式 1.4.0 全部一致，记录 `native-verification55.json`。
- Release 安装后实际打开自带测试框：九键出现，剪贴板顶部八个工具入口可见；点击语言与布局后分别显示语言、输入方案、键盘布局及确认按钮，未更改组合。截图 `final55-screen.png`、`final55-clipboard.png`、`final55-language.png`；本次启动时段没有 AndroidRuntime 崩溃。未操作或清空剪贴板内容。
- 最终源码仅相对通过测试的 Debug 55 做注释和缩进整理，不改变逻辑。实体键盘未连接，相关待验收限制仍成立；不把截图核对当用户验收。
