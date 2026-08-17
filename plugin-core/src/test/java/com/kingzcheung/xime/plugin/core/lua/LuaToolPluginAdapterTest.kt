package com.kingzcheung.xime.plugin.core.lua

import android.app.Application
import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.model.PluginContext
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 验证 tool 类型插件 Lua 契约（getPanelState/onPanelInput/onPanelAction/onPanelItemClick）
 * 宿主侧解析结果正确。
 */
class LuaToolPluginAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemoryConfigStore : PluginConfigStore {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private fun writeScript(content: String): File {
        val dir = tempFolder.newFolder("tool-plugin")
        val entry = File(dir, "main.lua")
        entry.writeText(content)
        return dir
    }

    private fun createAdapter(dir: File, pluginId: String = "com.test.tool"): LuaToolPluginAdapter {
        val runtime = LuaScriptRuntime(
            pluginId = pluginId,
            pluginDir = dir,
            entryScript = "main.lua",
            configStore = InMemoryConfigStore(),
        )
        val info = PluginInfo(id = pluginId, name = "测试工具", description = "测试", iconResId = 0, versionCode = 1, versionName = "1.0", path = File(dir, "main.lua").absolutePath, type = "tool")
        val pluginContext = PluginContext(application = Application(), pluginInfo = info, configStore = InMemoryConfigStore())
        val adapter = LuaToolPluginAdapter(runtime, pluginContext)
        adapter.onLoad(pluginContext)
        return adapter
    }

    @Test
    fun `getPanelState 解析输入框候选与操作按钮`() {
        val dir = writeScript(
            """
            local plugin = {}
            function plugin.getPanelState(inputText)
              return {
                inputText = "预填:" .. inputText,
                items = {
                  { id = "1", text = "候选一" },
                  { id = "2", text = "候选二" },
                },
                actions = {
                  { id = "generate", label = "生成" },
                },
              }
            end
            return plugin
            """.trimIndent()
        )
        val adapter = createAdapter(dir)

        val state = adapter.getPanelState("hello")
        assertEquals("预填:hello", state.inputText)
        assertEquals(2, state.items.size)
        assertEquals("候选一", state.items[0].text)
        assertEquals("2", state.items[1].id)
        assertEquals(1, state.actions.size)
        assertEquals("generate", state.actions[0].id)
        assertEquals("生成", state.actions[0].label)
    }

    @Test
    fun `getPanelState 未实现时返回空状态`() {
        val dir = writeScript(
            """
            local plugin = {}
            function plugin.onLoad() end
            return plugin
            """.trimIndent()
        )
        val adapter = createAdapter(dir)
        val state = adapter.getPanelState("x")
        assertEquals("", state.inputText)
        assertTrue(state.items.isEmpty())
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `面板事件回调桥接到 Lua`() {
        val dir = writeScript(
            """
            local plugin = {}
            local lastInput = ""
            local lastAction = ""
            local lastItem = ""
            function plugin.getPanelState() return {} end
            function plugin.onPanelInput(text) lastInput = text end
            function plugin.onPanelAction(actionId) lastAction = actionId end
            function plugin.onPanelItemClick(itemId) lastItem = itemId end
            function plugin._lastInput() return lastInput end
            function plugin._lastAction() return lastAction end
            function plugin._lastItem() return lastItem end
            return plugin
            """.trimIndent()
        )
        val runtime = LuaScriptRuntime(
            pluginId = "com.test.tool",
            pluginDir = dir,
            entryScript = "main.lua",
            configStore = InMemoryConfigStore(),
        )
        assertTrue(runtime.load())
        val info = PluginInfo(id = "com.test.tool", name = "测试工具", description = "测试", iconResId = 0, versionCode = 1, versionName = "1.0", path = File(dir, "main.lua").absolutePath, type = "tool")
        val adapter = LuaToolPluginAdapter(runtime, PluginContext(application = Application(), pluginInfo = info, configStore = InMemoryConfigStore()))

        adapter.onPanelInput("你好")
        assertEquals("你好", runtime.call("_lastInput").tojstring())

        adapter.onPanelAction("generate")
        assertEquals("generate", runtime.call("_lastAction").tojstring())

        adapter.onPanelItemClick("7")
        assertEquals("7", runtime.call("_lastItem").tojstring())
    }
}