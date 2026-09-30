# CyIME 图形工作台

在仓库根目录执行 `./scripts/start-visual-lab.ps1`，打开 http://127.0.0.1:4319 。只监听本机，不需要安装网页依赖。

左侧粘贴／修改代码，右侧在停止输入约 100ms 后更新。两种输入：

- **共享参数 JSON**：七段独立图标路径与渐变、四组图标配色、三层材质参数。可以查看透明图标、明暗键盘及设置卡片，20 个样片可逐项放大。
- **自由 SVG**：直接预览生成的静态 SVG 代码；可导出 SVG 和 PNG。脚本和外部资源不执行。

“编译 Android 资源”将当前 JSON 保存为 `design.json`，生成 `MaterialRecipes.kt` 和四份 `cyime_mark_*.xml`。重新构建 APK 后生效，编译按钮本身不安装／发布应用。CLI 等价命令：`node tools/visual-lab/compile.mjs`。

`node tools/visual-lab/compile.mjs --check` 检查生成文件是否与源参数一致；`node --test tools/visual-lab/renderer.test.mjs` 检查源一致性、参数顺序、图标无底板、材质分层、20 个输出。

网页键盘是共享材质参数的示意图，不是 Android 布局模拟器。主题色通过 `resolveKeyboardPalette` 和 `XimeTheme` 从 Android 导出；工具栏及功能键图标来自项目原有的 ImageVector，以透明 PNG 掩膜保留形状和双调透明度，再按所选主题着色。禁止用 Unicode 字符代替功能图标，禁止另写一套预览配色。

配色选择与视觉样式分别控制。工作台列出本次 Android 导出的纯色主题，可独立切换明暗；动态配色是导出设备当时的壁纸色快照，不会自动读取未连接手机的设置。图片、渐变背景尚未在网页模拟。材质的边缘和文字栅格化仍以 Android 实际截图为准。

`VisualLabResourcesTest` 生成 `visual-lab-resources.json`，尺寸门禁通过后自动拉取为 `android-resources.json`。缺少真实资源时预览报错，不能退回虚构图标或颜色。下方 Android 截图是上次验收结果，与实时预览分开展示。品牌图形的三条折带、四个亮面各自定义路径和渐变；右侧亮面使用独立的圆角斜四边形。

普通键 BASE、功能键／设置卡片 RAISED 均禁止辉光和内圈。候选内容使用 BASE；空闲工具栏才使用 FLOATING。材质不控制系统明暗及用户主题颜色；按压粒子动画仍由原有“炫光”开关控制。
