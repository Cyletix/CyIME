package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import com.kingzcheung.xime.plugin.core.api.ClipboardSyncPlugin
import com.kingzcheung.xime.plugin.core.model.PluginContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/** Protocol bridge. No-change is null; transport/execution failures propagate to retry handling. */
class LuaClipboardSyncPluginAdapter(
    runtime: LuaScriptRuntime,
    pluginContext: PluginContext
) : LuaPluginAdapter(runtime, pluginContext), ClipboardSyncPlugin {
    override suspend fun push(profile: ClipboardProfile): Boolean = withContext(Dispatchers.IO) {
        val table = LuaTable()
        table.set("type", profile.type)
        table.set("hash", profile.hash)
        table.set("text", profile.text)
        table.set("has_data", LuaValue.valueOf(profile.hasData))
        if (profile.dataName != null) table.set("data_name", profile.dataName)
        table.set("size", LuaValue.valueOf(profile.size.toDouble()))
        if (profile.source != null) table.set("source", profile.source)
        runtime.callChecked("push", table).toboolean()
    }

    override suspend fun pull(): ClipboardProfile? = withContext(Dispatchers.IO) {
        val result = runtime.callChecked("pull")
        if (result.isnil()) return@withContext null
        check(result.istable()) { "pull must return a profile or nil" }
        val map = LuaScriptRuntime.tableToMap(result)
        val text = map["text"]?.checkjstring() ?: error("Missing clipboard text")
        // The legacy WebDAV plugin supports plain text without a supplied hash.
        val hash = map["hash"]?.tojstring()?.takeIf { it.isNotEmpty() }
            ?: ClipboardProfile.sha256Hex(text.toByteArray(Charsets.UTF_8))
        ClipboardProfile(
            type = map["type"]?.tojstring() ?: "text",
            hash = hash,
            text = text,
            hasData = map["has_data"]?.toboolean() ?: false,
            dataName = map["data_name"]?.tojstring(),
            size = map["size"]?.tolong() ?: text.toByteArray(Charsets.UTF_8).size.toLong(),
            source = map["source"]?.tojstring()
        )
    }

    override suspend fun acknowledgePull(hash: String) = withContext(Dispatchers.IO) {
        runtime.callChecked("acknowledgePull", LuaValue.valueOf(hash))
        Unit
    }

    override suspend fun testConnection(): String? = withContext(Dispatchers.IO) {
        try {
            val result = runtime.callChecked("testConnection")
            if (result.isnil()) null else result.tojstring().takeIf { it.isNotBlank() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { error.message ?: "连接测试失败" }
    }

    // The settings button uses onAction, not the typed testConnection entry point.
    override suspend fun onAction(action: String): String? =
        if (action == "testConnection") testConnection() else super<LuaPluginAdapter>.onAction(action)
}
