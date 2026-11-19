package com.kingzcheung.xime.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI 助手：接口地址怎么拼、模型列表怎么读、流式回复怎么取字。 */
class AiAssistantCoreTest {

    // ── 地址拼接 ──

    @Test
    fun `基址到 v1 或带斜杠都拼成正确接口`() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
            AiEndpoints.chatUrl("https://api.deepseek.com/v1"))
        assertEquals("https://api.deepseek.com/v1/chat/completions",
            AiEndpoints.chatUrl("https://api.deepseek.com/v1/"))
        assertEquals("https://api.deepseek.com/v1/models",
            AiEndpoints.modelsUrl("https://api.deepseek.com/v1/"))
    }

    @Test
    fun `已经填到具体接口的不重复拼`() {
        assertEquals("https://x.cn/v1/chat/completions",
            AiEndpoints.chatUrl("https://x.cn/v1/chat/completions"))
        assertEquals("https://x.cn/v1/chat/completions",
            AiEndpoints.chatUrl("https://x.cn/v1/models"))
        assertEquals("https://x.cn/v1/models",
            AiEndpoints.modelsUrl("https://x.cn/v1/chat/completions/"))
    }

    @Test
    fun `从整段说明里取第一个网址、去掉粘贴进密钥的空白`() {
        assertEquals("https://api.groq.com/openai/v1",
            AiEndpoints.cleanUrl("  https://api.groq.com/openai/v1 • API 密钥: sk-xxx "))
        assertEquals("sk-abc123", AiEndpoints.cleanToken(" sk-abc\u200B123\n"))
        assertTrue(AiEndpoints.cleanUrl("").isEmpty())
    }

    @Test
    fun `没写协议时补 https，空地址用默认`() {
        assertEquals("https://api.x.cn/v1/chat/completions", AiEndpoints.chatUrl("api.x.cn/v1"))
        assertEquals(AiEndpoints.DEFAULT_BASE_URL + "/chat/completions", AiEndpoints.chatUrl(""))
    }

    // ── 模型列表 ──

    @Test
    fun `OpenAI 形状的模型列表`() {
        val body = """{"object":"list","data":[{"id":"deepseek-chat"},{"id":"deepseek-reasoner"}]}"""
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), AiModelList.parse(body))
    }

    @Test
    fun `Ollama 的 models 与纯数组也认，重复去重`() {
        assertEquals(listOf("qwen2.5", "llama3"),
            AiModelList.parse("""{"models":[{"name":"qwen2.5"},{"name":"llama3"}]}"""))
        assertEquals(listOf("a", "b"), AiModelList.parse("""["a","b","a"]"""))
    }

    @Test
    fun `拿不到列表时返回空表而不是抛异常`() {
        assertTrue(AiModelList.parse("""{"error":{"message":"Invalid API key"}}""").isEmpty())
        assertTrue(AiModelList.parse("not json").isEmpty())
        assertTrue(AiModelList.parse("").isEmpty())
    }

    // ── 对话协议 ──

    @Test
    fun `请求体带模型、消息和流式开关`() {
        val body = AiChatProtocol.requestBody("deepseek-chat",
            listOf(AiMessage("user", "你好"), AiMessage("assistant", "在")), stream = true)
        assertTrue(body.contains(""""model":"deepseek-chat""""))
        assertTrue(body.contains(""""role":"user""""))
        assertTrue(body.contains(""""content":"你好""""))
        assertTrue(body.contains(""""stream":true"""))
    }

    @Test
    fun `流式返回里取增量文字，结束标记不算字`() {
        assertEquals("你好", AiChatProtocol.deltaOf(
            """{"choices":[{"delta":{"content":"你好"}}]}"""))
        assertEquals(null, AiChatProtocol.deltaOf("""{"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertEquals(null, AiChatProtocol.deltaOf(AiChatProtocol.DONE))
        assertTrue(AiChatProtocol.isDone(" [DONE] "))
    }

    @Test
    fun `非流式返回取整段文字，SSE 拼起来也取得到`() {
        val whole = """{"choices":[{"message":{"role":"assistant","content":"你好呀"}}]}"""
        assertEquals("你好呀", AiChatProtocol.wholeText(whole))
        val sse = """
            data: {"choices":[{"delta":{"content":"你"}}]}

            data: {"choices":[{"delta":{"content":"好"}}]}

            data: [DONE]
        """.trimIndent()
        assertEquals("你好", AiChatProtocol.wholeText(sse))
    }

    @Test
    fun `接口给的原因优先于 HTTP 状态码`() {
        assertEquals("Invalid API key",
            AiChatProtocol.errorMessage("""{"error":{"message":"Invalid API key"}}"""))
        assertEquals(null, AiChatProtocol.errorMessage("""{"choices":[]}"""))
    }

    // ── 同步码 ──

    @Test
    fun `同步码导出再导入还原配置`() {
        val config = AiConfig(
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-\"quoted\"",
            model = "deepseek-chat",
            models = listOf("deepseek-chat", "deepseek-reasoner"),
            providerLabel = "DeepSeek",
        )
        val code = AiConfigStore.exportCode(config)
        val parsed = AiConfigStore.parseCode(code)
        assertEquals(config.baseUrl, parsed?.baseUrl)
        assertEquals(config.apiKey, parsed?.apiKey)
        assertEquals(config.model, parsed?.model)
        assertEquals(config.models, parsed?.models)
    }

    @Test
    fun `同步码格式不对返回 null`() {
        assertEquals(null, AiConfigStore.parseCode("hello"))
        assertEquals(null, AiConfigStore.parseCode("""{"api_key":"x"}"""))
    }

    @Test
    fun `预设能按地址反查名字`() {
        assertEquals("DeepSeek", AiPresets.labelOf("https://api.deepseek.com/v1/"))
        assertEquals("自定义", AiPresets.labelOf("https://my.gateway/v1"))
    }
}
