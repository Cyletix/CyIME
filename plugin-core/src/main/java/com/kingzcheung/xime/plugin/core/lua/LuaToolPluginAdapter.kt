package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.ToolPanelAction
import com.kingzcheung.xime.plugin.core.api.ToolPanelItem
import com.kingzcheung.xime.plugin.core.api.ToolPanelState
import com.kingzcheung.xime.plugin.core.api.ToolPlugin
import com.kingzcheung.xime.plugin.core.model.PluginContext
import org.luaj.vm2.LuaValue

/** tool 类型 Lua 插件的宿主侧适配器：实现 ToolPlugin 接口。 */
class LuaToolPluginAdapter(
    runtime: LuaScriptRuntime,
    pluginContext: PluginContext
) : LuaPluginAdapter(runtime, pluginContext), ToolPlugin {

    override fun getPanelState(inputText: String): ToolPanelState {
        val result = runtime.call("getPanelState", LuaValue.valueOf(inputText))
        if (!result.istable()) return ToolPanelState()
        val map = LuaScriptRuntime.tableToMap(result)
        val input = map["inputText"]?.tojstring()?.takeIf { it.isNotBlank() } ?: inputText
        return ToolPanelState(
            inputText = input,
            items = parseItems(map["items"] ?: LuaValue.NIL),
            actions = parseActions(map["actions"] ?: LuaValue.NIL),
            loading = map["loading"]?.toboolean() ?: false,
        )
    }

    override fun onPanelInput(text: String) {
        runtime.call("onPanelInput", LuaValue.valueOf(text))
    }

    override fun onPanelAction(actionId: String) {
        runtime.call("onPanelAction", LuaValue.valueOf(actionId))
    }

    override fun onPanelItemClick(itemId: String) {
        runtime.call("onPanelItemClick", LuaValue.valueOf(itemId))
    }

    private fun parseItems(value: LuaValue): List<ToolPanelItem> {
        if (!value.istable()) return emptyList()
        return LuaScriptRuntime.tableToList(value).mapNotNull { entry ->
            val m = LuaScriptRuntime.tableToMap(entry)
            val id = m["id"]?.tojstring() ?: return@mapNotNull null
            val text = m["text"]?.tojstring() ?: return@mapNotNull null
            ToolPanelItem(id = id, text = text)
        }
    }

    private fun parseActions(value: LuaValue): List<ToolPanelAction> {
        if (!value.istable()) return emptyList()
        return LuaScriptRuntime.tableToList(value).mapNotNull { entry ->
            val m = LuaScriptRuntime.tableToMap(entry)
            val id = m["id"]?.tojstring() ?: return@mapNotNull null
            val label = m["label"]?.tojstring() ?: id
            ToolPanelAction(id = id, label = label)
        }
    }
}