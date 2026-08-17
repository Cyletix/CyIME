# AI 功能平台设计方案（AI 智能回复 / AI 帮写 / AI 翻译）

> 状态：设计稿，未开始实现
> 来源：[#376 AI 智能回复](https://github.com/ximeiorg/Xime/issues/376)（用户扩展为 AI 能力平台）

## 一、背景与目标

issue #376 提出 AI 智能回复（类似 Lovekey 键盘），评论补充"可配置&管理 prompt 的 AI 帮写"。
本方案将需求扩展为统一的 **AI 能力平台**，一期覆盖三个功能：

1. **AI 智能回复**：给对方消息上下文 → 生成 3~5 条候选回复，点选上屏
2. **AI 帮写**：输入框草稿 + 用户指令（如"写好评，不少于20字"）→ 生成长文本，替换/追加
3. **AI 翻译**：选中/输入框文本 → 目标语言 → 结果替换/追加

三个功能都以**插件**形式提供：每个 AI 插件自带 prompt 模板、LLM 调用与面板数据，
宿主只提供通用面板框架（候选栏上方的输入框面板）与渲染/上屏能力。

## 二、核心设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| AI 功能 | **插件化**（`type: tool`，MULTI 激活，每个插件一个/多个功能） | 与 ASR 插件化决策一致；回复/帮写/翻译 = 三个插件（或一个插件多能力），用户按需安装；分类用 `tool`（工具类），不绑定 LLM 技术 |
| Prompt | **由插件提供**（插件内置模板，可经 configSchema 暴露给用户编辑） | 宿主不内置模板系统；「可配置&管理 prompt」诉求由插件的配置表单承载（复用现有 `IPluginConfigurable` + `PluginConfigFormScreen`） |
| LLM 接入 | **插件自含**（插件内 host.http 调 LLM API + NetworkPolicy 域名授权） | 插件自治；换 provider 即换插件，或改插件配置的 baseUrl/apiKey/model |
| UI 载体 | **Overlay 面板**，不做新键盘布局 | 项目已有 Overlay 机制；键盘布局代码零改动 |
| 面板形态 | **候选栏上方输入框面板** | 参考 `QuickSendFormArea` 同位置；一边看键盘一边调整上下文 |
| 工具栏入口 | **插件声明 toolbarButtons**（可选能力） | 不是所有插件都适合放工具栏，显式声明才有入口 |
| 模型 | 第一版非流式（同步 HTTP） | 复用 `host.http` 即可；流式（SSE）留二期 |

## 三、功能清单与数据链路

### 3.1 功能矩阵（每个功能 = 一个 AI 插件实例）

| 功能 | 触发 | 上下文来源 | Prompt（插件内置） | 产出形态 | 上屏方式 |
|---|---|---|---|---|---|
| AI 智能回复 | 插件工具栏按钮 | 对方消息（剪贴板 / 手动粘贴 / 输入框草稿） | 固定模板（人格+语气），可配置 | 3~5 条候选 | 点选上屏 |
| AI 帮写 | 插件工具栏按钮 | 输入框已有草稿 / 选中文本 + 用户指令 | 模板带 `{需求}` 占位符，可配置 | 1 条长文本 | 替换选区 / 追加 |
| AI 翻译 | 插件工具栏按钮 | **选中文本** / 输入框文本 | 模板 + 目标语言选项 | 1 条 | 替换选区 / 追加 |

**两种交互形式（通用）**：见 §3.3——形式 A「候选栏上方编辑框面板」（帮写/智能回复）与
形式 B「选区替换」（翻译/改写/总结）。三个功能都支持两种形式，宿主按上下文来源优先级
（选中文本 > 输入框全文 > 剪贴板 > 手动）决定预填与上屏目标。

三插件共用同一条数据链路，差异只在「上下文收集方式」「prompt 组装」和「模板配置项」。

### 3.2 统一数据链路

```
触发（插件工具栏按钮，action: open_panel）
  → 宿主打开该插件的通用面板（候选栏上方）
  → 收集上下文（按优先级取首个非空）：
      · 选中文本：ic.getSelectedText(0)，兜底 getExtractedText 按 selectionStart/End 截取
      · 输入框全文：ic.getTextBeforeCursor/AfterCursor（SAFE_TEXT_LIMIT）
      · 对方消息：剪贴板最近条目（ClipboardManager）
      · 用户补充：面板 EditText 手动输入
  → 宿主回调插件 Lua：
      onPanelInput(text)            输入变化
      onPanelAction("generate")     触发生成
  → 插件 Lua 组装 messages（自己的 prompt 模板 + 上下文）
      → host.http POST chat/completions → 解析 choices
  → 插件返回候选列表 → 宿主渲染到面板
  → 点选 → onPanelItemClick → 宿主上屏：
      · 有选区（面板打开时选中文本带入）→ setSelection 替换原选区
      · 无选区 → 光标处 commitText / 追加
```

### 3.3 两种交互形式

所有 AI 功能都归结为**两种交互形式**，共用同一套底座（Overlay 面板 + 数据链路 + 面板契约 + 上屏机制），差异只在「上下文来源」和「上屏目标」。

**形式 A：候选栏上方编辑框面板**
用户主动打开面板，在编辑框中提供/调整上下文（剪贴板预填 / 手动输入），生成结果上屏。

```
点工具栏按钮（open_panel）
  → 候选栏上方打开面板（编辑框 + 候选列表）
  → 上下文 = 剪贴板预填 / 手动输入 / 输入框草稿
  → 生成 → 点候选 → 光标处 commitText 追加（或替换面板打开时的选区）
```

适用：AI 帮写（写文案/好评）、AI 智能回复（粘贴对方消息）、需要用户给指令的场景。

**形式 B：选区替换（选中文本就地加工）**
用户先在输入框选中文本，再调插件功能——选中文本作为上下文预填，生成结果**替换原选区**。

```
选中输入框文本
  → 点插件工具栏按钮（open_panel）
  → 面板打开，上下文输入框自动预填选中文本
  → 生成/编辑后上屏 → setSelection(selStart, selEnd) 替换原选区
```

适用：AI 翻译（选中英文 → 中文替换）、改写、总结、纠错等"就地加工"场景。

| 维度 | 形式 A（编辑框面板） | 形式 B（选区替换） |
|---|---|---|
| 上下文来源 | 剪贴板预填 / 手动输入 / 输入框草稿 | 选中文本（优先级最高） |
| 面板打开时 | 输入框可自由编辑 | 输入框预填选中文本 |
| 上屏目标 | 光标处追加（无选区时） | 替换原选区 |
| 典型场景 | 帮写 / 智能回复 | 翻译 / 改写 / 总结 |

两者差异只在宿主侧：**上下文收集优先级**（选中文本 > 输入框全文 > 剪贴板 > 手动）与
**上屏目标**（替换选区 vs 追加）。面板、契约、LLM 调用完全相同，插件侧无需区分。

**选区实现依赖现有基础设施**：`ImeSchemaController` 已实现 select_all/选区操作与
`editSelAnchor` 选区锚点；`ImeKeyboardCallbacks:182` 已有 `getExtractedText` 取
selectionStart 的模式。AI 面板新增：读取选中文本（`getSelectedText(0)` +
extractedText 兜底）、记录选区起止、上屏前恢复选区（`setSelection`）。

## 四、架构：AI 功能插件自治 + 宿主通用面板框架

### 4.1 AI 功能插件（管"生成什么 + 连哪个 LLM"）

每个 AI 插件（`type: tool`，工具类插件，分类不绑定 AI 技术）是一个完整的功能单元：

| 能力 | 承载方式 |
|---|---|
| prompt 模板 | 插件内置（main.lua 常量）；可配置项经 `manifest.configSchema` 暴露（如模板文本、语气、目标语言列表），用户通过现有 `PluginConfigFormScreen` 编辑 |
| LLM 调用 | Lua `host.http` POST `{baseUrl}/chat/completions`，headers 带 apiKey；域名经 manifest `network.hosts` 声明 + 用户授权（`NetworkPolicy`） |
| 配置 | `apiKey/baseUrl/model` 存 `PluginConfigStore`（插件独立配置） |
| 面板数据 | Lua 契约（见 §6.4）：`getPanelState` / `onPanelInput` / `onPanelAction` / `onPanelItemClick` |
| 工具栏入口 | manifest `toolbarButtons` 声明（action: `open_panel`） |

激活模型：**MULTI**——多个 AI 插件可同时启用，各自有工具栏按钮与面板入口
（与 emoji 插件多开一致）。不需要 provider 单选偏好，插件完全自治。

### 4.2 宿主 AI 面板框架（管"怎么用"）

- 新增 `OverlayRoute.ToolPanel` + `ToolPanel` 通用面板，渲染在**候选栏上方**（KeyboardView Column 中 CandidateBar 之前，与 QuickSendFormArea 同位置）
- 结构（宿主渲染，内容数据来自插件）：上下文 EditText + 候选列表 + 操作行（重新生成/关闭）
- 宿主职责边界：面板框架、输入框、列表渲染、事件回调插件、commitText 上屏
- 宿主**不包含**任何 AI 业务逻辑（无内置 prompt、无内置 provider）

### 4.3 无宿主 Prompt 系统

- 宿主不实现全局模板存储/模板设置页
- 「可配置&管理 prompt」由插件的 `configSchema` 承载：插件声明模板配置字段，用户在自己的插件配置表单里编辑，宿主配置表单机制已存在（`IPluginConfigurable` + `PluginConfigFormScreen`），零新增

## 五、工具栏按钮插件适配

### 5.1 现状缺口（工具栏目前完全无法由插件提供）

| 位置 | 现状 | 缺口 |
|---|---|---|
| 渲染 | `KeyboardView.kt:292` `ToolbarButton.fromId(id) ?: return@mapNotNull null` | 插件 id 查不到枚举被丢弃 |
| 自定义 UI | `ToolbarCustomizeView.kt:53` `allButtons = ToolbarButton.entries` | 只列内置枚举 |
| 点击分发 | `KeyboardView.kt:297` `when(button)` 硬编码分支 | 插件 id 无入口 |
| 存储 | `getToolbarButtons` = `List<String>` id | ✅ 天然兼容插件 id |

### 5.2 插件声明（manifest.yaml）

```yaml
toolbarButtons:
  - id: ai_reply          # 全局限定 id（建议 plugin_id:ai_reply）
    label: AI 回复          # 显示：文字标签（图标加载失败兜底）
    icon: ai_reply.png     # 显示：resources/ 下图标文件
    action: open_panel     # 点击：打开该插件自己的面板（通用动作）
```

字段语义：`id` 管全链路（偏好存储/匹配），`icon`/`label` 管显示，`action` 管点击行为。

- `PluginInfo` 增加 `toolbarButtons: List<PluginToolbarButton>`（可选，默认空）
- `XmlManager` 序列化 + `InstallerManager` 解析 manifest（照抄 `network.hosts` 解析链路）

### 5.3 宿主渲染与分发

- 新增统一模型 `ToolbarButtonItem`（sealed）：`Builtin(ToolbarButton)` | `Plugin(id, label, icon, pluginId)`
- KeyboardView 渲染：内置 id 走 `fromId`，查不到时从「已启用插件的按钮」匹配
- ToolbarCustomizeView：`allButtons` = 内置枚举 + 已启用插件声明的按钮（合并列表，可开关）
- 点击分发：内置走现有 `when`；插件按钮 → `action: open_panel` → 打开该插件的通用面板
- 图标渲染：复用 `PluginIcon` 机制（`extractPluginIcon` 复制 `resources/` 图标到 `plugin_icons/`，coil `AsyncImage` 加载，参考 EmojiKeyboardLayout 插件图标）

### 5.4 两层显示控制

1. **插件启用开关**（插件中心）：决定候选池——只有启用插件的按钮进入可显示列表
2. **`toolbar_buttons` 偏好 + ToolbarCustomizeView 勾选**：决定最终显示哪些

完整链路：**插件声明（可选）→ 插件启用（插件中心）→ 用户勾选（工具栏自定义）→ 显示**。
插件被禁用/卸载后，偏好中残留的按钮 id 匹配不到插件 → 自动不显示（同 `HANDWRITING_LOOKUP` 无模型兜底）。

### 5.5 各插件类型与工具栏的关系

| 插件类型 | 消费入口 | 工具栏按钮 |
|---|---|---|
| emoji | 表情面板插件 tab（EmojiKeyboardLayout） | 不需要 |
| speech (ASR) | 语音按钮 + 语音转文字设置页 | 不需要 |
| clipboard_sync | 剪贴板同步设置页 | 不需要 |
| ai | 工具栏按钮 → 插件面板 | 声明才有 |

## 六、通用插件面板（候选栏上方输入框面板）

### 6.1 action 泛化

`action` 不写死 `open_ai_panel`，统一为 `open_panel`：宿主打开该插件声明的通用面板。
面板是什么、里面有什么，由插件决定；AI 插件只是第一个使用者。

### 6.2 面板位置与形态

KeyboardView.kt:213 的 Column 结构：

```
Column {
  if (showQuickSendForm) QuickSendFormArea(...)  // 候选栏上方
  CandidateBar(...)                              // 候选栏
  ...键盘主体
}
```

插件面板渲染在同位置（CandidateBar 之前），复用 `CandidateBarOverlayPanel` 容器
（标题 + 关闭按钮 + 卡片内容区，200dp 面板）。

### 6.3 与 QuickSendFormArea 的复用分析

| 部分 | 能否复用 | 说明 |
|---|---|---|
| `CandidateBarOverlayPanel` 容器 | ✅ 直接复用 | 框架完全吻合，内部替换内容 |
| 形态模式（位置/高度/EditText 配置） | ✅ 照抄 | 透明背景、16sp、`IME_ACTION_DONE`、顶部对齐 |
| EditText 静态 holder 机制 | ⚠️ 复制但独立 | 新建 `ToolPanelEditTextHolder`，不能共用 |
| `QuickSendFormArea` 本体 | ❌ 不可复用 | 绑定快捷发送数据流（add/updateQuickSendText）+ 内容区被 EditText 占满 |

**必须独立 holder 的原因**：`commitText()`（XimeInputMethodService:1709）、`ImeKeyRouter` 的
enter/delete 处理、`ImeKeyboardCallbacks:328` 都操作 `QuickSendFormEditTextHolder`（静态单例），
共用会互相覆盖。AI 面板需要：
- 独立的 `ToolPanelEditTextHolder`
- 面板输入焦点标志（仿 `quickSendFormFocused`，如 `toolPanelInputFocused`）
- 对应的按键路由分支：输入字符/退格注入面板 EditText；enter 触发生成

### 6.4 面板 Lua 契约（宿主渲染、插件给数据）

- `getPanelState()` → `{ inputText, items: [{id, text}], actions: [{id, label}] }`
  - inputText：面板输入框初始/更新内容（选中文本 / 输入框全文 / 剪贴板 / 手动输入）
  - items：候选列表（AI 生成结果，点击上屏）
  - actions：操作按钮（如重新生成）
- `onPanelInput(text)`（输入变化 → 插件可实时更新状态）
- `onPanelAction(actionId)`（按钮点击，如 `generate`）
- `onPanelItemClick(itemId)`（点候选 → 宿主上屏）
- `getSettingsSchema()` / `getOptions(key)`：复用已有配置契约，prompt 模板等配置项由此暴露

宿主上屏语义（在面板打开时采集一次输入框选区状态）：
- 面板打开时存在选区 → 上屏前 `setSelection(selStart, selEnd)` 替换原选区
- 无选区 → 光标处 `commitText`（追加）
- 面板输入框获得焦点后，后续上屏走光标处/面板内注入（同 QuickSendForm 的 holder 机制）

宿主负责：面板框架、输入框、候选列表渲染、上屏、事件回调。
插件负责：prompt 组装、LLM 调用、内容生成逻辑。

## 七、现有基础设施 vs 差距清单

### 可直接复用（已确认）

- `host.http` 完整 HTTP + `NetworkPolicy` 域名授权（HttpHostApiImpl.kt）
- Overlay 面板机制 + `CandidateBarOverlayPanel`（QuickSendFormArea 是现成样板）
- `commitText()` 上屏链路（XimeInputMethodService.kt:1706，含面板 EditText 注入分支）
- 输入框上下文：`getTextBeforeCursor/getTextAfterCursor`（ImeTextCommit.kt:27 已在用）
- **选区处理**：`ImeSchemaController` 的 select_all/选区操作与 `editSelAnchor` 锚点；`getExtractedText` 取 selectionStart 的模式（ImeKeyboardCallbacks.kt:182）——AI 面板读取选中文本/恢复选区直接复用该模式
- 剪贴板读取（ClipboardManager，含 consumed 过滤）
- 插件配置表单：`IPluginConfigurable` + `PluginConfigFormScreen`（prompt 模板等配置项由插件 configSchema 暴露，宿主零新增）
- `PluginIcon` 图标提取/渲染机制（extractPluginIcon + coil）
- `predictionManager.requestEpoch` 代际号模式（防旧 AI 结果回填闪动，宿主面板请求侧参考）

### 需要新增

1. 插件侧：
   - `PluginCategory.TOOL`（MULTI）+ `ToolPlugin` Lua 契约（`getPanelState`/`onPanelInput`/`onPanelAction`/`onPanelItemClick`）+ `LuaToolPluginAdapter`（参照 LuaAsrPluginAdapter）
   - 示例 AI 功能插件（如 AI 智能回复）：prompt 模板 + host.http 调 LLM + manifest `toolbarButtons`/`configSchema`/`network.hosts`
2. 宿主侧：
   - `PluginInfo.toolbarButtons` 字段 + XmlManager 序列化 + InstallerManager manifest 解析
   - `ToolbarButtonItem` 统一模型 + KeyboardView/ToolbarCustomizeView 渲染合并 + 点击分发（`open_panel`）
   - `OverlayRoute.ToolPanel` + `ToolPanel`（复用 CandidateBarOverlayPanel）+ `ToolPanelEditTextHolder` + 按键路由分支
   - 上下文收集：选中文本（`getSelectedText(0)` + extractedText 兜底）/ 输入框全文 / 剪贴板，按优先级预填面板输入框
   - 选区上屏：面板打开时记录选区起止，上屏前 `setSelection` 恢复选区替换
   - 面板请求编排 + 代际号（防旧结果回填闪动）
3. 宿主网络层：
   - **超时参数化**：`HttpHostApi.request` 增加可选超时（现默认 connect 10s / read 30s / write 30s），AI 长生成可声明更长超时
   - **SSE 流式接口**：新增 `host.http.stream(url, headers, callbacks)`，不在主计划外（见 §7.4）
4. 注意点：`host.http.request` 为同步阻塞调用（HttpHostApi.kt:10-11 明确），SSE 必须走独立流式原语，不能复用它；Lua 回调需在宿主侧线程 invoke（与 `host.ws` 一致）

### 7.4 SSE 流式接口契约（合入主计划）

SSE 采用与 `host.ws` 完全相同的**异步回调模型**：发起调用立即返回，数据/状态变化经 callbacks 表回调 Lua 函数（LuaScriptRuntime.kt:338-347 注册、151-157 后台线程 invoke 为现成范式）。

```
host.http.stream(url, headers, {
  onData  = function(text) end,        -- 每条 SSE event，按 data: 解析后的文本片段
  onDone  = function(fullText) end,    -- 流正常结束
  onError = function(message) end      -- 连接失败/授权拒绝/异常中断
})
host.http.lastError()                  -- 复用，stream 被拒/失败时读原因
```

- 宿主侧：后台线程发起 OkHttp 流式请求，逐行解析 `event:`/`data:`（`\n\n` 分隔），每条 `data` 回调 `onData`；结束/异常分别回调 `onDone`/`onError`
- 域名授权：复用 `NetworkPolicy` + 用户授权集合，同 HttpHostApiImpl:45-98 链路；未授权返回 false，`lastError()` 给原因
- 会话句柄：`stream` 返回一个会话 id（数字），`host.http.closeStream(id)` 可主动断开（重新生成/关闭面板时调用，防止旧请求继续回调）
- 线程模型：流解析线程调用 Lua 回调（与 wsListener 同模式）；面板代际号做最后一道防回填（见 §7 §2 面板请求编排）
- 错误传播：HTTP 非 2xx 或网络异常 → `onError`；插件 Lua 负责转成面板可读文案

## 八、实施顺序（单期，SSE 并入主链路）

依赖关系：面板底座依赖插件模型层（toolbarButtons）→ 插件契约依赖宿主面板与网络层 → 示例插件做端到端验证。每步可独立验证，前一步通过再进下一步。

| 步骤 | 内容 | 交付物 | 验证方式 |
|---|---|---|---|
| 1. 插件模型与 manifest | `PluginCategory.TOOL`（MULTI，label="工具"）+ `PluginInfo.toolbarButtons` 字段 + XmlManager 序列化 + InstallerManager manifest 解析（照抄 `network.hosts` 链路） | PluginCategory.kt、PluginInfo.kt、XmlManager.kt、InstallerManager.kt | 装一个带 `toolbarButtons` 的测试插件，重装后按钮声明仍保留 |
| 2. 宿主网络层 | `HttpHostApi.request` 超时参数化 + 新增 `SseHostApi` 与 `host.http.stream/closeStream`（照 ws 回调模型，NetworkPolicy 复用） | HttpHostApi.kt、HttpHostApiImpl.kt、LuaScriptRuntime.kt、SseHostApi.kt | 用测试插件调 `host.http.stream` 打外部 SSE 端点，验证 onData/onDone/onError 与 closeStream 中断 |
| 3. 工具栏适配 | `ToolbarButtonItem`（Builtin\|Plugin）+ KeyboardView 渲染合并（`fromId` 查不到走插件按钮）+ 点击分发 `open_panel` + ToolbarCustomizeView 合并 + PluginIcon 图标复用 | ToolbarButtonItem.kt、KeyboardView.kt、ToolbarCustomizeView.kt | 测试插件的工具栏按钮显示、可开关、点击触发 open_panel 回调 |
| 4. 面板底座 | `OverlayRoute.ToolPanel` + `ToolPanel`（复用 CandidateBarOverlayPanel）+ `ToolPanelEditTextHolder` + 按键路由分支 + commitText 注入 + 上下文收集（选中文本优先） + 选区替换上屏 + 面板请求代际号 | KeyboardPage.kt、ToolPanel.kt、XimeInputMethodService.kt、ImeKeyRouter.kt、InputUIState.kt | 用临时脚本插件（硬编码面板数据）验证：打开面板、输入、候选渲染、点击上屏、选区替换、退格/enter 路由 |
| 5. 插件契约 | `ToolPlugin` Lua 契约接口（getPanelState/onPanelInput/onPanelAction/onPanelItemClick）+ `LuaToolPluginAdapter` + PluginLifecycleManager 分支 | ToolPlugin.kt、LuaToolPluginAdapter.kt、PluginLifecycleManager.kt | 单元测试：mock Lua 脚本返回面板状态，断言宿主解析结果 |
| 6. 第一个插件：AI 智能回复 | manifest `toolbarButtons`/`configSchema`/`network.hosts` + prompt 模板 + **同步** `host.http` 生成 3~5 条候选，端到端全链路 | 示例插件目录 + 面板串接 | 真机：点按钮 → 面板打开预填上下文 → 生成 → 点候选上屏 |
| 7. 流式落地：AI 帮写 | 改用 `host.http.stream` 打字机效果；长文本生成不超时；中途可关停（closeStream）；验证代际号防旧流回填 | 帮写插件 + 面板流式渲染 | 真机：生成期间实时出字、可停止、切面板/重开后旧流不回填 |
| 8. 更多插件与打磨 | AI 翻译（选区替换优先）、通用配置表单完善、错误/loading/重试状态、面板交互细节 | 翻译插件、面板状态打磨 | 真机走查 + 补充单元测试 |

- 步骤 6 用同步 `host.http` 先打通全链路（验证成本低），步骤 7 再无缝切到 stream，两条路径并存、互不阻塞
- SSE 不在二期：步骤 2 的宿主原语与步骤 7 的端到端验证都在单期计划内；智能回复这类短候选仍走同步，长文本走 SSE，由插件自选

## 附：相关文件索引

- 工具栏：`app/src/main/java/com/kingzcheung/xime/keyboard/ToolbarButton.kt`、`ui/keyboard/KeyboardView.kt`、`ui/menubar/ToolbarCustomizeView.kt`
- 面板容器：`ui/keyboard/CandidateBarOverlayPanel.kt`、`ui/keyboard/QuickSendFormArea.kt`
- 快捷发送 EditText holder：`service/XimeInputMethodService.kt:134`、`service/ImeKeyRouter.kt`
- 插件：`plugin-core/.../model/PluginInfo.kt`、`runtime/installer/{XmlManager,InstallerManager}.kt`、`runtime/lifecycle/PluginLifecycleManager.kt`
- 插件网络：`app/.../plugin/http/HttpHostApiImpl.kt`、`plugin/ws/WsHostApiImpl.kt`、`plugin/ExtensionManager.kt`；宿主原语：`plugin-core/.../lua/http/HttpHostApi.kt`、`lua/ws/WsHostApi.kt`、`lua/LuaScriptRuntime.kt`（SSE 新增 `SseHostApi.kt` 与此同目录）
- AI 面板：`ui/keyboard/KeyboardView.kt:256`（候选栏上方位置）、`OverlayRoute.ToolPanel`（KeyboardPage.kt:29）、`ToolPanel.kt`／`ToolPanelEditTextHolder`（新增）、`service/ImeKeyRouter.kt`、`service/XimeInputMethodService.kt:1710`（commitText 注入）
- 插件配置表单：`plugin-core/.../config/IPluginConfigurable.kt`、`ui/settings/PluginConfigFormScreen.kt`
- ASR 模板：`plugin-core/.../lua/LuaAsrPluginAdapter.kt`、`ui/settings/SpeechToTextSettingsScreen.kt`
- 候选/联想：`service/PredictionManager.kt`、`service/ImeSessionController.kt`、`ui/keyboard/CandidateBarState.kt`
