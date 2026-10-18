# Xime 上游源码对比（阶段性，2026-10-08）

只读对比 CyIME 主目录工作区与上游 ximeiorg/Xime HEAD `5b04255`（2026-10-07，v3.0.0）。分叉点 `9452b16`（2026-09-21）；上游此后 94 个非合并提交，CyIME 未拣选任何上游提交。上游浅克隆在 `D:/CyIME-Data/competitors/xime/Xime`。本文只收录已有源码证据的结论；架构性能、插件生态、上游缺陷审计、提交逐条对照因调研中断尚未完成，其他开源输入法调研也未完成。

## CyIME 落后或存在缺陷（建议按序处理）

| # | 问题 | 证据（CyIME） | 上游 | 代价 |
|---|---|---|---|---|
| 1 | 系统标记为敏感的剪贴板内容（密码管理器复制的密码）会写进剪贴板历史 | `clipboard/ClipboardManager.kt` 的 `readClipboard`/`addItem` 无 `IS_SENSITIVE` 判断；仅同步桥检查 | 采集时跳过（`clipboard/ClipboardManager.kt:137-142,189-196`）；fcitx5-android、FlorisBoard、FUTO 同样过滤 | 低 |
| 2 | 长按候选上屏 label 而非 value；配置未知 action 时选中该项 `gesture.action!!` 空指针崩溃。自带 `xime.yaml:362` 示例 `{label:"大写", value:"A"}` 实际上屏“大写” | `ui/keyboard/KeyboardLayout.kt:1073-1080,1106-1114`（另 673-706、821-857、1894-1927 同类） | `longPressDisplayItems`/`longPressActionMap`（786dba3） | 低 |
| 3 | 长按 `display` 默认 `key` 时完全触发不到（只有 bubble 生效） | `settings/KeysConfigHelper.kt:219,228`；`KeyboardLayout.kt:1074` | 强制按 bubble 处理 | 低 |
| 4 | 26 键 `switch_route: clipboard` 被静默丢弃；`swipe_down` 不写 label 不触发；字母键 `tap.action` 被忽略 | `KeyboardLayout.kt:202-209,1380-1387,521,689,840,1091,1908,518,1088,1905` | 统一分发实现 | 低 |
| 5 | 深色模式功能键文字写死白色，`special_key_text_color_dark` 从未被读取；浅色功能键底配白字 | `KeyboardLayout.kt:181,1353`、`T9KeyboardLayout.kt:350`、`KeyboardLayoutScreen.kt:66`、`KeyboardPalette.kt:29`、`ThemePreviewSheet.kt:283` | 按底色亮度自适应（`KeyboardTheme.kt:472`） | 低 |
| 6 | 符号、表情、颜文字“最近使用”点按即重排，连续点同一项时位置跳动 | `EmojiKeyboardLayout.kt:79-87,174-176`、`SymbolKeyboardLayout.kt:84-91`、`KaomojiKeyboard.kt:33,57-58` | 点按只写记录，下次打开才重排（e20faa2） | 低 |
| 7 | 含 `\n` 的多行键面提示被截成一行（自带 flypy 示例 `"ue\nve"`） | `KeyButton.kt:544-567`（`take(4)`、`maxLines=1`） | 多行渲染（6e99299） | 低 |
| 8 | 读屏无障碍：字母键无 `Role.Button` 与语义点击，Shift 无状态描述 | `KeyButton.kt:131-420,639-723`、`KeyboardLayout.kt:1284-1287` | 统一 `keySemantics`（6e99299） | 中 |
| 9 | 缺少用户词库同步、导出、导入（4 个原生接口及设置页） | 无 `syncUserData`/`exportUserDict` 等 | 256ce51、47b3774 | 中 |
| 10 | 缺少布局市场（上游唯一的远程主题/布局分发渠道，带 sha256 与防 zip-slip） | `MarketHubScreen.kt:157-180` 只有三个标签 | 79197d4 | 中 |
| 11 | 每键原生调用更多：全键盘 2→4 次、九键 4→9 次，另有每键 64 候选预取与九键 100ms 精修轮询；`rime_jni` 硬链接 onnxruntime，加载引擎即加载推理库 | `RimeEngine.kt:415-428,1103-1112`；`jni/CMakeLists.txt:177-179` | 更轻 | 中 |
| 12 | 文档示例 `docs/config_examples/theme/xime.custom.yaml:43,50` 有两个顶层 `color_schemes:` | — | 已删除 | 极低 |

插件运行时已分道：上游迁到 JS 运行时与插件 API v3，CyIME 仍为 Lua；上游新插件能否装进 CyIME 未核实。

## CyIME 已领先

- 引擎并发：输入 FIFO 队列、九键按键在同一次加锁内原子完成、候选版本号拒绝过期选择；上游部分配置调用在主线程且不加锁，锁忙时把字母原样上屏。
- 部署：只有活会话里能看到方案才算就绪，失败退避重试，增量维护后重建会话；上游失败也标记完成。
- 恢复备份先停引擎；快照读取不再吞掉待提交文本；修复 `ApplySchema` 后悬空指针并跳过冗余重建。
- 主题：完整调色板、回车键与功能键文字单独配色、低版本动态主题回退；真实窗口模糊（上游无任何模糊实现）。

## 两边共同的风险

原生部署无限等待且持锁；`ensureSession` 持锁轮询最长 180 秒；`onDestroy` 在主线程阻塞调用 `destroy()`；`IME_FLAG_NO_PERSONALIZED_LEARNING` 只用于插件事件，没有用来关闭引擎学习与剪贴板采集（fcitx5-android 映射为 Sensitive 能力）。
