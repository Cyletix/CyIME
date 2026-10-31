package com.kingzcheung.xime.speech

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.InputLanguage
import java.io.BufferedInputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 内置在线语音的标点能力：接口原文（服务端已加标点）声明 [AsrBackend.resultKeepsPunctuation]，
 * 在线失败改用本地模型的这段不声明——上层只保留真实识别器给的标点，本地回退仍按原空格化。
 */
class OnlineApiAsrBackendPunctuationTest {

    private fun contextFor(baseUrl: String): Context {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getString(eq("base_url"), anyOrNull())).thenReturn(baseUrl)
        whenever(prefs.getString(eq("api_key"), anyOrNull())).thenReturn("test-key")
        whenever(prefs.getString(eq("model"), anyOrNull())).thenReturn("whisper-1")
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        return context
    }

    /** 只做“录音→整段识别”的本地替身，模拟在线失败后回退到本地模型。 */
    private class FakeLocalBackend : AsrBackend {
        override val name = "本地替身"
        private var onResult: ((String) -> Unit)? = null
        override fun setCallbacks(onResult: (String) -> Unit, onPartialResult: ((String) -> Unit)?,
            onStateChange: (RecognitionState) -> Unit, onError: (String) -> Unit) {
            this.onResult = onResult
        }
        override fun initialize(): Boolean = true
        override fun start(): Boolean = true
        override fun processAudioChunk(buffer: ByteArray) = Unit
        // 本地模型这段录音的结果自己也可能带标点，但它不是接口原文：能力位保持默认 false。
        override fun stop() { onResult?.invoke("本地，备用。") }
        override fun cancel() = Unit
        override fun release() = Unit
        override fun isAvailable(): Boolean = true
    }

    private fun record(backend: OnlineApiAsrBackend): MutableList<String> {
        val results = mutableListOf<String>()
        backend.setCallbacks(onResult = { results += it }, onPartialResult = null,
            onStateChange = {}, onError = { results += "错误:$it" })
        backend.start(InputLanguage.CHINESE)
        backend.processAudioChunk(ByteArray(3200) { 0x10 })
        backend.stop()
        return results
    }

    /** 一次性本机假接口：读完请求体后回 [json]，模拟 OpenAI 兼容的 /audio/transcriptions。 */
    private fun serveOnce(json: String): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    val head = StringBuilder()
                    val terminator = byteArrayOf(13, 10, 13, 10)
                    var matched = 0
                    while (matched < terminator.size) {
                        val b = input.read()
                        if (b < 0) break
                        head.append(b.toChar())
                        matched = when {
                            b.toByte() == terminator[matched] -> matched + 1
                            b.toByte() == terminator[0] -> 1
                            else -> 0
                        }
                    }
                    var remaining = Regex("(?i)content-length:\\s*(\\d+)")
                        .find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    val sink = ByteArray(8192)
                    while (remaining > 0) {
                        val n = input.read(sink, 0, minOf(remaining, sink.size))
                        if (n < 0) break
                        remaining -= n
                    }
                    val body = json.toByteArray()
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
        return server
    }

    @Test
    fun `interface final keeps the server's punctuation`() {
        val server = serveOnce("""{"text":"你好，世界。今天天气不错。"}""")
        try {
            val backend = OnlineApiAsrBackend(contextFor("http://127.0.0.1:${server.localPort}/v1"),
                localFallback = { FakeLocalBackend() })
            assertEquals(listOf("你好，世界。今天天气不错。"), record(backend))
            assertTrue("接口原文带标点，上层应保留", backend.resultKeepsPunctuation)
        } finally {
            server.close()
        }
    }

    @Test
    fun `local fallback after a failed request claims no punctuation`() {
        val notices = mutableListOf<String>()
        // 本机未监听的端口：连接立刻被拒，走本地回退，不需要真的联网。
        val backend = OnlineApiAsrBackend(contextFor("http://127.0.0.1:1/v1"),
            localFallback = { FakeLocalBackend() }, onNotice = { notices += it })
        assertEquals(listOf("本地，备用。"), record(backend))
        assertFalse("本地回退的那段不能按在线原文保留标点", backend.resultKeepsPunctuation)
        assertEquals(1, notices.size)
    }

    @Test
    fun `backends that produce no server marks keep the old spacing contract`() {
        assertFalse(FakeLocalBackend().resultKeepsPunctuation)
    }
}
