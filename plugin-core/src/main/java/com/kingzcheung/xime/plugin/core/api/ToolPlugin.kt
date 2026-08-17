package com.kingzcheung.xime.plugin.core.api

import com.kingzcheung.xime.plugin.core.config.IPluginConfigurable

/** 工具面板候选条目（AI 生成结果等，点击上屏）。 */
data class ToolPanelItem(
    val id: String,
    val text: String,
)

/** 工具面板操作按钮（如"重新生成"）。 */
data class ToolPanelAction(
    val id: String,
    val label: String,
)

/** 工具面板状态（宿主渲染、插件给数据）。 */
data class ToolPanelState(
    val inputText: String = "",
    val items: List<ToolPanelItem> = emptyList(),
    val actions: List<ToolPanelAction> = emptyList(),
    /** 是否正在生成中（SSE 流式期间为 true，宿主据此展示 loading 并轮询刷新）。 */
    val loading: Boolean = false,
)

/**
 * 工具类插件（`type: tool`）契约：宿主渲染通用面板，插件提供数据与事件处理。
 *
 * - [getPanelState]：返回面板状态（输入框内容 + 候选列表 + 操作按钮）
 * - [onPanelInput]：面板输入框内容变化通知
 * - [onPanelAction]：面板操作按钮点击（如 `generate`）
 * - [onPanelItemClick]：点候选 → 宿主上屏（host 负责选区替换/追加）
 */
interface ToolPlugin : IPluginEntryClass, IPluginConfigurable {
    fun getPanelState(inputText: String): ToolPanelState

    fun onPanelInput(text: String) {}

    fun onPanelAction(actionId: String) {}

    fun onPanelItemClick(itemId: String) {}
}