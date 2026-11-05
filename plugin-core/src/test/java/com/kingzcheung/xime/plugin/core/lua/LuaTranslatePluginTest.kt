package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.config.UiNodeType
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.http.SseHostApi
import com.kingzcheung.xime.plugin.core.lua.http.SseHostListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 翻译插件（plugins/translate，翻译栏 display: live）：真实 Lua 运行时 + 假接口。
 * 栏里的字流式译出来，生成中就有一段；新的一句把没完的旧请求断掉；圆钮换目标语言。
 */
class LuaTranslatePluginTest {

    private class InMemoryConfigStore : PluginConfigStore {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private class FakeHttp : HttpHostApi {
        var content = "Hello."
        override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?, timeoutMillis: Int?) =
            HttpResponse(status = 200, body = """{"choices":[{"message":{"content":"$content"}}]}""".toByteArray())
        override fun lastError(): String? = null
    }

    private class FakeSse : SseHostApi {
        val listeners = mutableListOf<SseHostListener>()
        val bodies = mutableListOf<String>()
        val closed = mutableListOf<Int>()
        override fun connect(url: String, headers: Map<String, String>, listener: SseHostListener,
                             timeoutMillis: Int?, method: String, body: ByteArray?): Int {
            listeners += listener
            bodies += body?.toString(Charsets.UTF_8).orEmpty()
            return listeners.size - 1
        }
        override fun close(sessionId: Int) { closed += sessionId }
        override fun lastError(): String? = null
    }

    private fun chunk(text: String) = """{"choices":[{"delta":{"content":"$text"}}]}"""

    private fun setup(key: String? = "test-key", stream: Boolean = true): Triple<LuaToolPluginAdapter, FakeSse, InMemoryConfigStore> {
        val dir = File("../plugins/translate")
        assertTrue("translate 插件目录应存在: ${dir.absolutePath}", dir.exists())
        val store = InMemoryConfigStore()
        key?.let { store.set("apiKey", it) }
        val sse = FakeSse()
        val runtime = LuaScriptRuntime("com.kingzcheung.xime.plugin.translate", dir, "main.lua", store,
            httpHostApi = FakeHttp(), sseHostApi = if (stream) sse else null)
        assertTrue("main.lua 应能加载", runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.translate", name = "翻译", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))
        return Triple(adapter, sse, store)
    }

    @Test
    fun `边收边出译文，收完是整句`() {
        val (adapter, sse, _) = setup()
        val chip = adapter.getPanelState("").ui!!.first { it.type == UiNodeType.BUTTON }
        assertEquals("EN", chip.label)
        adapter.onPanelInput("你好，明天见")
        adapter.onPanelAction("generate")
        assertTrue(adapter.getPanelState("").loading)
        assertTrue("提示词带目标语言和原文", sse.bodies[0].contains("翻译成英文") && sse.bodies[0].contains("你好，明天见"))
        sse.listeners[0].onData(chunk("Hello, "))
        assertEquals("Hello,", adapter.getPanelState("").items.single().text)
        sse.listeners[0].onData(chunk("see you tomorrow."))
        sse.listeners[0].onData("[DONE]")
        sse.listeners[0].onDone("")
        val done = adapter.getPanelState("")
        assertFalse(done.loading)
        assertEquals("Hello, see you tomorrow.", done.items.single().text)
    }

    @Test
    fun `新的一句断掉旧请求，旧回包不再写`() {
        val (adapter, sse, _) = setup()
        adapter.onPanelInput("你好")
        adapter.onPanelAction("generate")
        adapter.onPanelInput("你好，在吗")
        adapter.onPanelAction("generate")
        assertEquals(listOf(0), sse.closed)
        sse.listeners[0].onData(chunk("STALE"))
        assertTrue(adapter.getPanelState("").items.isEmpty())
        sse.listeners[1].onData(chunk("“Hi, are you there?”"))
        sse.listeners[1].onDone("")
        assertEquals("Hi, are you there?", adapter.getPanelState("").items.single().text)
    }

    @Test
    fun `圆钮换目标语言，存进设置`() {
        val (adapter, sse, store) = setup()
        adapter.onPanelAction("lang")
        assertEquals("日文", store.get("targetLang"))
        assertEquals("日", adapter.getPanelState("").ui!!.first { it.type == UiNodeType.BUTTON }.label)
        adapter.onPanelInput("谢谢")
        adapter.onPanelAction("generate")
        assertTrue(sse.bodies.last().contains("翻译成日文"))
    }

    @Test
    fun `没填密钥：圆钮是设置，不发请求`() {
        val (adapter, sse, _) = setup(key = null)
        val ui = adapter.getPanelState("").ui!!
        assertEquals("host:settings", ui.first { it.type == UiNodeType.BUTTON }.key)
        adapter.onPanelInput("你好")
        adapter.onPanelAction("generate")
        val state = adapter.getPanelState("")
        assertFalse(state.loading)
        assertTrue(state.items.isEmpty())
        assertTrue(sse.listeners.isEmpty())
    }

    @Test
    fun `接口出错：提示原因，没有译文`() {
        val (adapter, sse, _) = setup()
        adapter.onPanelInput("你好")
        adapter.onPanelAction("generate")
        sse.listeners[0].onError("HTTP 401")
        val state = adapter.getPanelState("")
        assertFalse(state.loading)
        assertTrue(state.items.isEmpty())
        assertTrue(state.ui!!.first { it.type == UiNodeType.TEXT }.value!!.contains("401"))
    }

    @Test
    fun `宿主没有流式接口时一次请求`() {
        val (adapter, _, _) = setup(stream = false)
        adapter.onPanelInput("你好")
        adapter.onPanelAction("generate")
        val state = adapter.getPanelState("")
        assertFalse(state.loading)
        assertEquals("Hello.", state.items.single().text)
    }
}
