package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.config.UiNodeType
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.http.SseHostApi
import com.kingzcheung.xime.plugin.core.lua.http.SseHostListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 验证 AI 插件端到端数据链路（Lua 侧）：
 * - ai-reply：同步 host.http.request 生成候选列表 → getPanelState 解析
 */
class LuaAiPluginTest {

    private class InMemoryConfigStore : PluginConfigStore {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private class MockHttpHostApi(var response: HttpResponse? = null) : HttpHostApi {
        var lastUrl: String? = null
        var lastTimeout: Int? = null
        override fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: ByteArray?,
            timeoutMillis: Int?
        ): HttpResponse? {
            lastUrl = url
            lastTimeout = timeoutMillis
            return response
        }
        override fun lastError(): String? = null
    }

    private class MockSseHostApi : SseHostApi {
        var listener: SseHostListener? = null
        var closedIds = mutableListOf<Int>()
        var returnId = 100
        var connectedMethod: String? = null
        var connectedBody: ByteArray? = null
        var connectedCount = 0
        override fun connect(
            url: String,
            headers: Map<String, String>,
            listener: SseHostListener,
            timeoutMillis: Int?,
            method: String,
            body: ByteArray?
        ): Int {
            connectedCount++
            connectedMethod = method
            connectedBody = body
            this.listener = listener
            return returnId
        }
        override fun close(sessionId: Int) { closedIds.add(sessionId) }
        override fun lastError(): String? = null
    }

    @Test
    fun `ai-reply 同步生成候选并解析到面板状态`() {
        val dir = File("../plugins/ai-reply")
        assertTrue("ai-reply 插件目录应存在: ${dir.absolutePath}", dir.exists())
        val store = InMemoryConfigStore()
        store.set("apiKey", "test-key")
        store.set("baseUrl", "https://api.example.com/v1")
        store.set("model", "gpt-test")
        val mockHttp = MockHttpHostApi(
            HttpResponse(
                status = 200,
                body = """
                    {"choices":[{"message":{"content":"好的！\n好的呀！\n没问题！"}}]}
                """.trimIndent().toByteArray()
            )
        )
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.ai_reply",
            dir,
            "main.lua",
            store,
            httpHostApi = mockHttp
        )
        assertTrue("main.lua 应能加载", runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.ai_reply", name = "AI 智能回复", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))

        adapter.onPanelInput("明天有空吗")
        adapter.onPanelAction("generate")

        val state = adapter.getPanelState("明天有空吗")
        assertTrue("应生成 3 条候选", state.items.size == 3)
        assertEquals("好的！", state.items[0].text)
        assertEquals("好的呀！", state.items[1].text)
        assertEquals("没问题！", state.items[2].text)
        assertFalse("同步生成后不在加载中", state.loading)
        assertTrue("请求应带长超时", (mockHttp.lastTimeout ?: 0) >= 60000)
        assertTrue("请求 URL 指向 chat/completions", mockHttp.lastUrl?.contains("/chat/completions") == true)
    }

    @Test
    fun `ai-reply 未配置 API Key 时生成失败`() {
        val dir = File("../plugins/ai-reply")
        assertTrue(dir.exists())
        val store = InMemoryConfigStore()
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.ai_reply", dir, "main.lua", store,
            httpHostApi = MockHttpHostApi()
        )
        assertTrue(runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.ai_reply", name = "AI 智能回复", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))

        adapter.onPanelInput("你好")
        adapter.onPanelAction("generate")
        assertTrue("未配置 Key 不应产生候选", adapter.getPanelState("你好").items.isEmpty())
    }

    @Test
    fun `ai-reply 点选上屏后重置面板状态`() {
        val dir = File("../plugins/ai-reply")
        assertTrue(dir.exists())
        val store = InMemoryConfigStore()
        store.set("apiKey", "test-key")
        store.set("baseUrl", "https://api.example.com/v1")
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.ai_reply", dir, "main.lua", store,
            httpHostApi = MockHttpHostApi(
                HttpResponse(
                    status = 200,
                    body = """{"choices":[{"message":{"content":"好的！\n好的呀！"}}]}""".toByteArray()
                )
            )
        )
        assertTrue(runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.ai_reply", name = "AI 智能回复", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))

        adapter.onPanelInput("明天有空吗")
        adapter.onPanelAction("generate")
        assertTrue("生成后应有候选", adapter.getPanelState("明天有空吗").items.isNotEmpty())

        // 点选上屏：状态重置，下次打开面板回到初始态
        adapter.onPanelItemClick("1")
        val state = adapter.getPanelState("")
        assertTrue("上屏后候选应清空", state.items.isEmpty())
        assertTrue("重置后应回到初始态（含生成按钮）", state.ui?.any { it.type == UiNodeType.BUTTON } == true)
    }

    @Test
    fun `ai-reply 上下文变化时旧候选自动失效`() {
        val dir = File("../plugins/ai-reply")
        assertTrue(dir.exists())
        val store = InMemoryConfigStore()
        store.set("apiKey", "test-key")
        store.set("baseUrl", "https://api.example.com/v1")
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.ai_reply", dir, "main.lua", store,
            httpHostApi = MockHttpHostApi(
                HttpResponse(
                    status = 200,
                    body = """{"choices":[{"message":{"content":"好的！"}}]}""".toByteArray()
                )
            )
        )
        assertTrue(runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.ai_reply", name = "AI 智能回复", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))

        adapter.onPanelInput("明天有空吗")
        adapter.onPanelAction("generate")
        assertTrue(adapter.getPanelState("明天有空吗").items.isNotEmpty())

        // 用户复制了新消息后重新打开面板（新上下文）→ 旧候选失效，回到初始态
        val state = adapter.getPanelState("周六怎么样")
        assertTrue("新上下文应清空旧候选", state.items.isEmpty())
        assertTrue("新上下文应回到初始态（含生成按钮）", state.ui?.any { it.type == UiNodeType.BUTTON } == true)
    }

    @Test
    fun `AI 插件均导出配置 schema（插件中心设置入口依据）`() {
        for (name in listOf("ai-reply")) {
            val dir = File("../plugins/$name")
            assertTrue("$name 插件目录应存在: ${dir.absolutePath}", dir.exists())
            val runtime = LuaScriptRuntime(
                "com.kingzcheung.xime.plugin.$name", dir, "main.lua", InMemoryConfigStore()
            )
            assertTrue("$name main.lua 应能加载", runtime.load())
            val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
                application = android.app.Application(),
                pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                    id = "com.kingzcheung.xime.plugin.$name", name = name, description = "测试",
                    iconResId = 0, versionCode = 1, versionName = "0.1.0",
                    path = File(dir, "main.lua").absolutePath, type = "tool"
                ),
                configStore = InMemoryConfigStore(),
            ))
            val fields = adapter.getSettingsSchema()
            assertTrue("$name 应至少 4 个配置字段（apiKey/baseUrl/model/prompt）", fields.size >= 4)
            val keyToField = fields.associateBy { it.key }
            assertTrue("$name 应包含 apiKey", keyToField.containsKey("apiKey"))
            assertTrue("$name 应包含 baseUrl", keyToField.containsKey("baseUrl"))
            val prompt = keyToField["prompt"]
            assertNotNull("$name 应包含 prompt", prompt)
            assertEquals(
                "$name prompt 应为 textarea 长文本编辑",
                com.kingzcheung.xime.plugin.core.config.UiNodeType.TEXTAREA,
                prompt!!.type
            )
        }
    }
}