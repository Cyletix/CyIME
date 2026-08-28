package com.kingzcheung.xime.plugin.core.api

import com.kingzcheung.xime.plugin.core.config.IPluginConfigurable

/**
 * 工具面板结果显示方式：宿主按插件元数据（manifest.capabilities.tool.display）决策
 * 结果交互（直接上屏 or 全屏页面候选选择），插件侧不再返回。
 * manifest 取值小写：`direct` | `select` | `passive`（与 inputMode 风格一致）。
 */
enum class ToolResult {
    /** 结果生成结束直接上屏（如 AI 翻译）。 */
    DIRECT,

    /** 结果生成结束打开全屏结果页面（AiResultPanel），用户点击候选上屏（如 AI 智能回复）。 */
    SELECT,

    /**
     * 纯展示面板（InfoPanel）：无输入框、无生成动作（enter 不触发 generate）、
     * 点击节点不上屏。插件通过 [ToolPanelState.ui] 声明式描述内容
     * （白名单节点：section/text/metric/divider/action），宿主统一渲染。
     */
    PASSIVE,
}

/**
 * 工具面板状态（宿主渲染、插件给数据）。
 */
data class ToolPanelState(
    val inputText: String = "",
    val items: List<PluginResultItem> = emptyList(),
    /** 是否正在生成中（SSE 流式期间为 true，宿主据此展示 loading 并轮询刷新）。 */
    val loading: Boolean = false,
    /**
     * passive 纯展示节点树（声明式 UI）。每节点 = { type: String, ...字段 }，
     * 白名单 type：section(title) / text(content, style) / metric(label, value, unit?) /
     * divider / action(label, actionId)。未知 type 宿主降级为文本渲染，不崩溃。
     */
    val ui: List<Map<*, *>>? = null,
)

/**
 * 工具类插件（`type: tool`）契约：宿主渲染通用面板（ToolPanel + AiResultPanel），插件提供数据与事件处理。
 *
 * ## 返回协议（宿主强制校验：非法数据将被丢弃并输出协议错误日志）
 *
 * [getPanelState] 必须返回 Lua table，字段：
 * - `items`（必填，数组）：候选结果，每个元素 `{ id: string, text: string, insertText?: string, imageUrl?: string }`；
 *   `id` 必须非空且全表唯一，`text` 必须非空，否则该元素被宿主丢弃。
 *   多候选（MULTIPLE）时建议 id 用序号；单条（SINGLE）时可用固定值（如 `"result"`）。
 * - `loading`（布尔）：生成期间为 true，宿主据此轮询刷新直至 false。
 * - `inputText`（字符串，可选）：面板输入框内容回显，缺省回退为宿主传入的输入。
 * - `ui`（数组，可选）：纯展示节点树，仅 `display: passive` 时由宿主渲染（InfoPanel）。
 *   节点 = `{ type: string, ...字段 }`，白名单：
 *   `section(title)` / `text(content, style?)` / `metric(label, value, unit?)` /
 *   `divider` / `action(label, actionId)`；未知 type 降级为文本，不崩溃。
 *
 * 传输方式（同步 HTTP / SSE 流式）与结果呈现由插件元数据声明，宿主只消费上述结构化状态。
 *
 * - [getPanelState]：返回面板状态（输入框内容 + 候选列表）
 * - [onPanelInput]：面板输入框内容变化通知
 * - [onPanelAction]：`generate` 触发生成（宿主保留 action id）
 * - [onPanelItemClick]：点候选 → 宿主上屏（host 负责选区替换/追加）
 */
interface ToolPlugin : IPluginEntryClass, IPluginConfigurable {
    fun getPanelState(inputText: String): ToolPanelState

    fun onPanelInput(text: String) {}

    fun onPanelAction(actionId: String) {}

    fun onPanelItemClick(itemId: String) {}
}