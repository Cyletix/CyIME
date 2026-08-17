# 本次会话：AI 功能平台（2026-08-17）

## 设计文档

- `docs/ai-feature-plan.md`：AI 功能平台设计方案（工具类插件 + 通用面板 + SSE 单期）。分类统一为 `TOOL`（不绑定 LLM 技术），overlay/契约/面板命名同步改为 ToolPanel 系列；SSE 并入主链路（`host.http.stream` 异步回调模型，照 ws 范式）；实施顺序改为 8 步单期表

## 已完成

1. **步骤 1/8：插件模型与 manifest**
   - `PluginCategory.TOOL("tool", "工具", MULTI)`（插件中心自动落入"工具"分组）
   - `PluginInfo.toolbarButtons: List<PluginToolbarButton>` + 新模型 `PluginToolbarButton(id, label, icon, action="open_panel")`
   - `InstallerManager`：manifest `toolbarButtons` 解析（`ToolbarButtonConfig`）+ `isValidToolbarButtonId` 校验（禁逗号［偏好分隔］/XML 特殊字符/空白控制符，≤64）
   - `XmlManager`：toolbarButtons 序列化（`<toolbarButton id label icon action/>` 属性形式，escapeXml）/ 反序列化（属性提取 + unescapeXml）照抄 networkHosts 链路
   - `PluginsSettingsScreen.getCategoryIcon` 补 TOOL 分支（AutoFixHigh）——穷举 when 无 else，枚举新增编译强制要求
   - 测试：`ManifestParseTest` +5（toolbarButtons 解析/缺省空/action 空白回落 open_panel/非法 id 过滤/id 校验）、`PluginCategoryTest` +tool 映射与 activation、`PluginInfoTest` +3（toolbarButtons 默认空/保留 + TOOL 分类/按钮默认值）
   - 验证：`./gradlew test` + `assembleDebug` 全绿

2. **步骤 2/8：宿主网络层（SSE + 超时参数化）**
   - `HttpHostApi.request` 增加 `timeoutMillis` 参数（AI 长生成可覆盖 30s 默认）；`HttpHostApiImpl` 生效（临时 builder 覆盖 connect/read/write + `call.timeout()`
   - 新增 `SseHostApi`/`SseHostListener`（plugin-core）：异步回调模型（onData/onDone/onError）+ 会话句柄（connect 返回 id / close 中断），与 host.ws 互补
   - `LuaScriptRuntime`：注入 `sseHostApi` + `host.http.stream(url, headers, {onData,onDone,onError}, timeout)` / `host.http.closeStream(id)`，回调经 `sseCallbacks` 表桥接（异常不破解析线程）
   - `PluginManager.sseHostApiFactory` + `PluginLifecycleManager` 传入 + `XimeApplication` 注入
   - 新增 `SseHostApiImpl`（app）：NetworkPolicy 授权（含 allowCustomHosts 自动授权，同 HttpHostApiImpl）+ **okhttp-sse 5.4.0** `EventSources.createFactory`/`newEventSource` 解析 + `Session` 句柄管理 + close 主动 cancel 静默终止
   - 依赖：新增 `com.squareup.okhttp3:okhttp-sse:5.4.0`（与 okhttp 5.4.0 对齐）。**版本结论**：5.5.0 需 compileSdk≥37，本机 SDK/AGP 不支持（AGP 9.1.0 最高 36）；已 javap 对比确认 5.4/5.5 API 完全一致（5.0 Kotlin 重写后定型），未来升 5.5.0 代码零改动、仅需工具链升级
   - 测试：`LuaSseHostTest` +4（stream 回调桥接与 closeStream/connect 失败返回 -1/request timeout 透传/空回调表不崩溃）+ 旧测试 mock 补 timeoutMillis 参数；`test` + `assembleDebug` 全绿

## 未完成（下次从这里继续）

> 起点：**步骤 3/8 工具栏适配**。设计文档见 `docs/ai-feature-plan.md` §八 实施顺序表（单期，SSE 已并入）。当前代码基线：`test` + `assembleDebug` 全绿。

### 步骤 3/8：工具栏适配（下一步）
- 目标：插件工具栏按钮可显示、可开关、点击分发到插件面板入口
- 需新增统一模型 `ToolbarButtonItem`（sealed：`Builtin(ToolbarButton)` | `Plugin(id, label, icon, pluginId)`）
- `KeyboardView.kt:292`：`ToolbarButton.fromId(id) ?: return@mapNotNull null` 改为「内置枚举查不到时，从已启用插件的按钮匹配」
- `KeyboardView.kt:297` `when(button)`：新增插件按钮分支 → `action: open_panel` 打开该插件通用面板
- `ToolbarCustomizeView.kt:53`：`allButtons` = 内置枚举 + 启用插件声明的按钮合并（可开关）；`:121` 预览 `fromId` 同样要支持插件按钮
- 两层显示控制：插件启用开关（候选池）+ `toolbar_buttons` 偏好（最终显示）；插件禁用/卸载后残留 id 自动不显示
- 图标：复用 `ExtensionManager.extractPluginIcon` + coil `AsyncImage`；无图标用 label 文字兜底
- 验收：装一个带 `toolbarButtons` 的测试插件 → 工具栏出现按钮、自定义视图可勾选、点击触发 open_panel 回调

### 步骤 4/8：面板底座
- 目标：通用插件面板（候选栏上方）可打开、输入、渲染列表、上屏、选区替换
- `KeyboardPage.kt:29` `OverlayRoute` 新增 `ToolPanel`；新建 `ToolPanel`（复用 `CandidateBarOverlayPanel`，照 `QuickSendFormArea.kt` 写），渲染在 `KeyboardView.kt:256` 同位置（CandidateBar 之前）
- 新建**独立** `ToolPanelEditTextHolder`（不能共用 `QuickSendFormEditTextHolder`，XimeInputMethodService.kt:134，共用会互相覆盖）+ `toolPanelInputFocused` 标志（InputUIState.kt）
- `ImeKeyRouter.kt:26`：aiPanel 分支（字符/退格注入面板 EditText；enter 触发生成）；`commitText()`（XimeInputMethodService.kt:1710）加面板注入分支
- 上下文收集：`getSelectedText(0)` + extractedText 兜底（项目目前无 getSelectedText 调用）+ 输入框全文 + 剪贴板，按优先级预填
- 选区上屏：面板打开时记录选区起止，上屏前 `setSelection` 恢复替换；无选区光标处 commitText 追加
- 面板请求代际号（参照 `predictionManager.requestEpoch` 防旧结果回填闪动）
- 验收：临时脚本插件（硬编码面板数据）验证打开/输入/候选渲染/点击上屏/选区替换/退格与 enter 路由

### 步骤 5/8：插件契约
- 目标：`type: tool` 插件可被宿主加载为面板插件
- `ToolPlugin` Lua 契约接口（`getPanelState`/`onPanelInput`/`onPanelAction`/`onPanelItemClick`）+ `LuaToolPluginAdapter`（参照 `LuaAsrPluginAdapter.kt` 结构）
- `PluginLifecycleManager.kt:174` `when(plugin.category)` 加 `PluginCategory.TOOL` 分支
- 验收：单测 mock Lua 脚本返回面板状态，断言宿主解析结果

### 步骤 6/8：AI 智能回复插件（同步全链路）
- manifest `toolbarButtons`/`configSchema`/`network.hosts` + prompt 模板 + **同步** `host.http.request(..., timeoutMillis)` 生成 3~5 条候选
- 真机端到端：点按钮 → 面板打开预填上下文 → 生成 → 点候选上屏

### 步骤 7/8：AI 帮写（SSE 流式落地）
- 改用 `host.http.stream` 打字机效果（宿主原语步骤 2 已就绪，等步骤 4 面板渲染）；中途可关停 `closeStream`；代际号防旧流回填

### 步骤 8/8：AI 翻译 + 打磨
- 选区替换优先；错误/loading/重试状态；面板交互细节

### 遗留注意点（踩过的坑）
- **okhttp 版本**：okhttp/okhttp-sse 锁 5.4.0；5.5.0 需 compileSdk≥37（本机 AGP 9.1.0 最高 36，无 android-37 平台），但 5.4/5.5 API 完全一致，升级只需工具链
- **okhttp-sse 5.x API**：用 `EventSources.createFactory(client)` + `factory.newEventSource(request, listener)`（不是 4.x 的 `processAsync`）；`EventSourceListener` 是抽象类，`onEvent` 的 id/type 可空、data 非空
- **SSE 回调线程**：SseHostApiImpl 回调在后台线程 invoke Lua（与 host.ws 同模式）；`host.http.stream` 挂在 `host.http` 表下，注入条件 `httpHostApi != null`（Sse 测试踩过）
- **host.http.request 超时**：默认 connect 10s/read 30s/write 30s；长生成传 `timeoutMillis`（Lua 第 5 参）

---