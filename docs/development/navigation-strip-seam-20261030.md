# 换主题后键盘底下“悬空”一截（2026-10-30）

## 现象

用户转来的反馈：换了配色以后，键盘底下悬空 1 厘米左右。

## 原因

固定键盘时，系统导航区（三键导航约 48 dp，手势条约 24 dp）由服务里单独一条 `Box` 画背景，键盘卡片由 `KeyboardView` 画背景。两处都调用 `Modifier.keyboardBackground`，各按自己的尺寸铺：

- 纯色主题：两边同色，看不出来。
- 渐变主题（落日余晖、极光青蓝为 90° 竖向，iOS 渐变为 135°）：导航条在卡片底下从渐变的起始色重新开始，卡片最底下是终止色，接缝处断开一截。
- 图片主题（大漠流金）：导航条把整张图按 cover 塞进 48 dp 高的一条，截的是图中间，跟卡片底边对不上。
- 毛玻璃开着时卡片和导航条共用 `KeyboardBackdropHost` 的同一张窗口坐标背景，本来就连续，不受影响。

默认主题是纯色，所以“换了主题之后”才出现。

## 修改

- `KeyboardBackground.kt` 新增 `Modifier.keyboardBackgroundBelowCard`：导航条按卡片在窗口里的位置接着铺。
  - 渐变：用卡片同一条线性渐变（同样的起止点），延伸到导航条，超出部分夹在终止色。
  - 图片：卡片以外没有图，沿卡片底边镜像；遮罩照旧。窄于整宽的固定卡片两侧先铺主题底色。
  - 纯色、毛玻璃、卡片还没量到：和原来一样。
- 图片解码和遮罩抽成 `rememberThemeImage` / `drawImageOverlay`，原 `keyboardBackground` 行为不变；`drawImageBackground` 加了目标尺寸参数（默认仍是自身尺寸）。
- `XimeInputMethodService`：固定卡片的 `onCardPositioned` 同时记下卡片窗口坐标，导航条读它。悬浮模式和「底部圆角与透明导航栏」不画这条，不受影响。

## 检查

- `NavigationStripSeamRenderTest`：落日余晖、极光青蓝、iOS 渐变深浅各一份，逐点比接缝上下两行像素（新画法差值 ≤ 6）；同一测试用旧画法画一份，竖向渐变的旧画法差值必须 ≥ 12，证明这项检查抓得到原来的断层。设 `CYIME_RENDER_OUT` 时出对照图 `navigation-strip-seam.png`（含大漠流金，左旧右新）。
- 编写环境（云端）没有 Android SDK，以上编译、测试、渲染都未在编写环境运行，由电脑上的打包线程执行。真机未验收。
