package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.AsrPluginListener
import com.kingzcheung.xime.plugin.core.config.PluginConfigStore
import com.kingzcheung.xime.plugin.core.lua.crypto.CryptoHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpHostApi
import com.kingzcheung.xime.plugin.core.lua.http.HttpResponse
import com.kingzcheung.xime.plugin.core.lua.sdk.SimpleJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaValue
import java.io.File
import java.util.Base64

/**
 * 验证 online-asr：OpenAI 兼容（multipart → /audio/transcriptions）与
 * 腾讯 TokenHub 同步语音识别（JSON {model, data = WAV Base64} → 原样地址，文字在 output.text）。
 */
class LuaOnlineAsrPluginTest {

    private class InMemoryConfigStore : PluginConfigStore {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun set(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private class FakeHttp : HttpHostApi {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: ByteArray = ByteArray(0)
        var status = 200
        var reply = "{}"
        override fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: ByteArray?,
            timeoutMillis: Int?
        ): HttpResponse {
            this.url = url
            this.headers = headers
            this.body = body ?: ByteArray(0)
            return HttpResponse(status, emptyMap(), reply.toByteArray(Charsets.UTF_8))
        }
        override fun lastError(): String? = null
    }

    private class JvmCrypto : CryptoHostApi {
        override fun sha256(data: ByteArray): ByteArray = ByteArray(32)
        override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = ByteArray(32)
        override fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray = ByteArray(20)
        override fun hex(data: ByteArray): String = ""
        override fun base64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)
        override fun utcTime(format: String): String = ""
        override fun epochSeconds(): Long = 0
    }

    private class Collector : AsrPluginListener {
        var finalText: String? = null
        var error: String? = null
        override fun onFinal(text: String) { finalText = text }
        override fun onPartial(text: String) {}
        override fun onError(message: String) { error = message }
    }

    private fun runtime(store: PluginConfigStore, http: HttpHostApi): LuaScriptRuntime =
        LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.online_asr",
            File("../plugins/online-asr"),
            "main.lua",
            store,
            httpHostApi = http,
            cryptoHostApi = JvmCrypto(),
        ).also { assertTrue("main.lua 应能加载", it.load()) }

    private fun speak(rt: LuaScriptRuntime, pcm: ByteArray): Collector {
        val collector = Collector()
        rt.asrResultCallback = collector
        rt.call("start", LuaValue.valueOf("zh"))
        rt.call("processAudioChunk", LuaString.valueOf(pcm))
        rt.call("stop")
        return collector
    }

    private fun testConnection(rt: LuaScriptRuntime): String? =
        rt.call("testConnection").let { if (it.isnil()) null else it.tojstring() }

    @Test
    fun `tencent tokenhub sends json base64 wav to the address as entered`() {
        val store = InMemoryConfigStore().apply {
            set("apiKey", "k1")
            set("baseUrl", "https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe")
            set("model", "hy-asr-3.0-preview")
        }
        val http = FakeHttp().apply {
            reply = """{"id":"th_1","object":"asr.task","status":"completed","output":{"source":"zh",""" +
                """"duration_ms":1000,"text":"你好世界","sentences":[{"begin_ms":0,"end_ms":900,"text":"你好世界"}]},""" +
                """"usage":{"total_token":7}}"""
        }
        val rt = runtime(store, http)
        val pcm = ByteArray(320) { (it % 7).toByte() }
        val result = speak(rt, pcm)

        assertEquals("https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe", http.url)
        assertEquals("application/json", http.headers["Content-Type"])
        assertEquals("Bearer k1", http.headers["Authorization"])
        @Suppress("UNCHECKED_CAST")
        val body = SimpleJson.decode(http.body.toString(Charsets.UTF_8)) as Map<String, Any?>
        assertEquals("hy-asr-3.0-preview", body["model"])
        assertEquals("wav", body["voice_encode_format"])
        val wav = Base64.getDecoder().decode(body["data"] as String)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(44 + pcm.size, wav.size)
        assertTrue(wav.copyOfRange(44, wav.size).contentEquals(pcm))
        assertEquals("你好世界", result.finalText)
        assertNull(result.error)
    }

    @Test
    fun `tencent silence test passes and errors show the server reason`() {
        val store = InMemoryConfigStore().apply {
            set("apiKey", "k1")
            set("baseUrl", "https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe")
            set("model", "hy-asr-3.0-preview")
        }
        val http = FakeHttp()
        val rt = runtime(store, http)

        http.reply = """{"status":"completed","output":{"duration_ms":1000}}"""
        assertNull(testConnection(rt))

        http.status = 400
        http.reply = """{"error":{"message":"invalid model","code":"InvalidParameter"}}"""
        assertEquals("接口不认这个请求（400）：invalid model", testConnection(rt))

        http.status = 401
        http.reply = """{"message":"无效的 API Key"}"""
        assertEquals("密钥不对或没有权限（401）：无效的 API Key", testConnection(rt))

        http.status = 200
        http.reply = """{"status":"failed","message":"音频解码失败"}"""
        assertEquals("接口报错：音频解码失败", testConnection(rt))
    }

    @Test
    fun `openai compatible endpoints keep multipart upload`() {
        val store = InMemoryConfigStore().apply {
            set("apiKey", "k2")
            set("baseUrl", "https://api.groq.com/openai/v1/")
            set("model", "whisper-large-v3")
        }
        val http = FakeHttp().apply { reply = """{"text":" hello "}""" }
        val rt = runtime(store, http)
        val result = speak(rt, ByteArray(320))

        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", http.url)
        assertTrue(http.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
        val text = http.body.toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("name=\"model\"\r\n\r\nwhisper-large-v3"))
        assertTrue(text.contains("name=\"language\"\r\n\r\nzh"))
        assertTrue(text.contains("filename=\"speech.wav\""))
        assertEquals("hello", result.finalText)

        // 地址填到 /audio/transcriptions 也不重复拼
        store.set("baseUrl", "https://api.siliconflow.cn/v1/audio/transcriptions")
        assertNull(testConnection(rt))
        assertEquals("https://api.siliconflow.cn/v1/audio/transcriptions", http.url)
    }
}
