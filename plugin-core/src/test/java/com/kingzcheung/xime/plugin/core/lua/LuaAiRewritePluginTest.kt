package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.io.File

/**
 * AI 改写插件（plugins/ai-rewrite）：真实 Lua 运行时 + 假接口。
 * 结果通过校验才出现在面板里；答非所问、套话、丢数字的结果被拦下，原文不动。
 */
class LuaAiRewritePluginTest {

    private class InMemoryConfigStore : PluginConfigStore {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private class FakeApi : HttpHostApi {
        var content: String = ""
        var status: Int = 200
        var lastUrl: String? = null
        var lastBody: String? = null
        override fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: ByteArray?,
            timeoutMillis: Int?
        ): HttpResponse? {
            lastUrl = url
            lastBody = body?.toString(Charsets.UTF_8)
            val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            return HttpResponse(status = status,
                body = """{"choices":[{"message":{"content":"$escaped"}}]}""".toByteArray())
        }
        override fun lastError(): String? = null
    }

    private val source = "这个方案我觉得还行吧，不过预算那块可能得再看看，别超了。"

    private fun setup(): Triple<LuaScriptRuntime, LuaToolPluginAdapter, FakeApi> {
        val dir = File("../plugins/ai-rewrite")
        assertTrue("ai-rewrite 插件目录应存在: ${dir.absolutePath}", dir.exists())
        val store = InMemoryConfigStore()
        store.set("apiKey", "test-key")
        store.set("baseUrl", "https://api.example.com/v1/")
        val api = FakeApi()
        val runtime = LuaScriptRuntime("com.kingzcheung.xime.plugin.ai_rewrite", dir, "main.lua", store, httpHostApi = api)
        assertTrue("main.lua 应能加载", runtime.load())
        val adapter = LuaToolPluginAdapter(runtime, com.kingzcheung.xime.plugin.core.model.PluginContext(
            application = android.app.Application(),
            pluginInfo = com.kingzcheung.xime.plugin.core.model.PluginInfo(
                id = "com.kingzcheung.xime.plugin.ai_rewrite", name = "AI 改写", description = "测试",
                iconResId = 0, versionCode = 1, versionName = "0.1.0",
                path = File(dir, "main.lua").absolutePath, type = "tool"
            ),
            configStore = store,
        ))
        return Triple(runtime, adapter, api)
    }

    private fun validate(runtime: LuaScriptRuntime, mode: String, src: String, raw: String): String? {
        val cleaned = runtime.call("_cleanOutput", LuaValue.valueOf(raw), LuaValue.valueOf(src))
        val reason = runtime.call("_validate", LuaValue.valueOf(mode), LuaValue.valueOf(src), cleaned)
        return if (reason.isnil()) null else reason.tojstring()
    }

    @Test
    fun `润色结果通过校验后出现在面板里`() {
        val (_, adapter, api) = setup()
        adapter.getPanelState(source)
        api.content = "以下是润色后的内容：\n“这个方案我觉得还不错，不过预算部分可能需要再看看，别超支了。”"
        adapter.onPanelAction("mode:polish")
        val state = adapter.getPanelState("")
        assertEquals(1, state.items.size)
        assertEquals("这个方案我觉得还不错，不过预算部分可能需要再看看，别超支了。", state.items[0].text)
        assertEquals("https://api.example.com/v1/chat/completions", api.lastUrl)
        assertTrue("原文要用标记框起来", api.lastBody?.contains("<原文>") == true)
    }

    @Test
    fun `答非所问的结果被拦下，不给替换`() {
        val (_, adapter, api) = setup()
        adapter.getPanelState(source)
        api.content = "今天天气晴朗，适合出门散步，记得带伞以防万一下雨。"
        adapter.onPanelAction("mode:polish")
        assertTrue(adapter.getPanelState("").items.isEmpty())
    }

    @Test
    fun `校验规则`() {
        val (runtime, _, _) = setup()
        val q = "你们明天几点开会呀，我想旁听一下可以吗？"
        assertEquals(null, validate(runtime, "fix", "我明天早上八点到公司，你门先开会把。", "我明天早上八点到公司，你们先开会吧。"))
        assertEquals(null, validate(runtime, "polish", q, "你们明天几点开会？我想旁听一下，可以吗？"))
        assertTrue(validate(runtime, "polish", q, "明天上午十点开会，欢迎旁听。") != null)
        assertTrue(validate(runtime, "polish", source, "抱歉，我无法帮你判断预算。") != null)
        assertTrue(validate(runtime, "polish", source, "当然可以！这个方案我觉得还不错，不过预算部分可能需要再看看。") != null)
        assertTrue(validate(runtime, "polish", "下午3点在5号会议室，记得带电脑过来哈", "下午在会议室，记得带电脑过来。") != null)
        assertTrue(validate(runtime, "shorten", source, source + "我们下周再讨论。") != null)
        assertEquals(null, validate(runtime, "fix", "i has a apple and two banana", "I have an apple and two bananas"))
        // 翻译（原「AI 翻译」并进来）：换了语言不比字，只拦答非所问和套话
        assertEquals(null, validate(runtime, "translate", "明天晚上我们去哪吃饭？", "Where are we having dinner tomorrow night?"))
        assertTrue(validate(runtime, "translate", "明天晚上我们去哪吃饭？", "We could go to the hot pot place.") != null)
        assertEquals(null, validate(runtime, "translate", "对不起，我迟到了。", "I'm sorry, I'm late."))
        assertEquals(null, validate(runtime, "translate", "Sure, see you tomorrow.", "当然，明天见。"))
        assertTrue(validate(runtime, "translate", "去哪吃？", "好的，这是翻译：Where to eat?") != null)
    }

    @Test
    fun `接口报错或没填密钥时不给结果`() {
        val (_, adapter, api) = setup()
        adapter.getPanelState(source)
        api.status = 401
        api.content = source
        adapter.onPanelAction("mode:fix")
        assertTrue(adapter.getPanelState("").items.isEmpty())
    }

    @Test
    fun `打开面板时没有字就清掉上一次的原文和结果`() {
        val (_, adapter, api) = setup()
        adapter.getPanelState(source)
        api.content = "这个方案我觉得还不错，不过预算部分可能需要再看看，别超支了。"
        adapter.onPanelAction("mode:polish")
        assertEquals(1, adapter.getPanelState("").items.size)
        val reopened = adapter.getPanelState("")
        assertTrue(reopened.items.isEmpty())
        assertEquals("没有要改的字", reopened.ui?.firstOrNull()?.label)
    }
}
