package com.kingzcheung.xime.speech

import com.kingzcheung.xime.plugin.core.api.AsrPluginBackend
import com.kingzcheung.xime.plugin.core.api.AsrPluginListener
import com.kingzcheung.xime.plugin.core.model.PluginCapabilities
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 语音插件适配：整段上传的插件失败时这段录音改用本地模型；只有插件自己的结果保留标点；语言交给声明了 any 的插件。 */
class PluginAsrBackendAdapterTest {

    private class FakePlugin(val error: String? = null, val text: String = "你好。") : AsrPluginBackend {
        var callback: AsrPluginListener? = null
        var startedWith: String? = null
        val received = ArrayList<Byte>()
        override val isRunning: Boolean = false
        override fun setListener(listener: AsrPluginListener) { callback = listener }
        override fun initialize(): Boolean = true
        override fun start(): Boolean { startedWith = ""; return true }
        override fun start(languageId: String): Boolean { startedWith = languageId; return true }
        override fun processAudioChunk(pcm: ByteArray) { received += pcm.toList() }
        override fun stop() { if (error != null) callback?.onError(error) else callback?.onFinal(text) }
        override fun cancel() {}
        override fun release() {}
    }

    private class FakeLocal(val text: String = "本地结果") : AsrBackend {
        override val name = "本地"
        var bytes = 0
        private var onResult: ((String) -> Unit)? = null
        override fun setCallbacks(onResult: (String) -> Unit, onPartialResult: ((String) -> Unit)?,
                                  onStateChange: (RecognitionState) -> Unit, onError: (String) -> Unit) { this.onResult = onResult }
        override fun initialize(): Boolean = true
        override fun start(): Boolean = true
        override fun start(language: InputLanguage): Boolean = true
        override fun processAudioChunk(buffer: ByteArray) { bytes += buffer.size }
        override fun stop() { onResult?.invoke(text) }
        override fun cancel() {}
        override fun release() {}
        override fun isAvailable(): Boolean = true
    }

    private val batch = PluginCapabilities.SpeechCapabilities(inputMode = "batch", supportsPartialResults = false,
        keepsPunctuation = true, anyLanguage = true)

    private class Sink {
        val results = ArrayList<String>()
        val errors = ArrayList<String>()
        val states = ArrayList<RecognitionState>()
        val notices = ArrayList<String>()
        fun bind(backend: AsrBackend) = backend.setCallbacks(onResult = { results += it }, onPartialResult = null,
            onStateChange = { states += it }, onError = { errors += it })
    }

    @Test
    fun `插件结果保留标点，收尾等 30 秒`() {
        val sink = Sink()
        val adapter = PluginAsrBackendAdapter("在线语音", FakePlugin(), batch, onNotice = { sink.notices += it })
        sink.bind(adapter)
        assertTrue(adapter.start(InputLanguage.ENGLISH))
        adapter.processAudioChunk(ByteArray(320))
        adapter.stop()
        assertEquals(listOf("你好。"), sink.results)
        assertTrue(adapter.resultKeepsPunctuation)
        assertEquals(30_000L, adapter.finishTimeoutMs)
        assertTrue(sink.notices.isEmpty())
    }

    @Test
    fun `整段上传失败时这段录音改用本地模型并提示`() {
        val sink = Sink()
        val local = FakeLocal()
        val adapter = PluginAsrBackendAdapter("在线语音", FakePlugin(error = "密钥不对或没有权限（401）"), batch,
            localFallback = { local }, onNotice = { sink.notices += it })
        sink.bind(adapter)
        adapter.start(InputLanguage.CHINESE)
        adapter.processAudioChunk(ByteArray(6400))
        adapter.stop()
        assertEquals(listOf("本地结果"), sink.results)
        assertEquals(6400, local.bytes)
        assertFalse("本地补识别的那段没有服务端标点", adapter.resultKeepsPunctuation)
        assertTrue(sink.errors.isEmpty())
        assertTrue(sink.notices.single().contains("401"))
    }

    @Test
    fun `本地也不可用时报错`() {
        val sink = Sink()
        val adapter = PluginAsrBackendAdapter("在线语音", FakePlugin(error = "连不上接口"), batch)
        sink.bind(adapter)
        adapter.start(InputLanguage.CHINESE)
        adapter.processAudioChunk(ByteArray(320))
        adapter.stop()
        assertTrue(sink.results.isEmpty())
        assertTrue(sink.errors.single().contains("连不上接口"))
        assertEquals(RecognitionState.ERROR, sink.states.last())
    }

    @Test
    fun `流式插件照旧：错误直接报，不改用本地，只认中文`() {
        val sink = Sink()
        val local = FakeLocal()
        val plugin = FakePlugin(error = "断线")
        val adapter = PluginAsrBackendAdapter("腾讯云", plugin, localFallback = { local })
        sink.bind(adapter)
        adapter.start(InputLanguage.CHINESE)
        assertEquals("", plugin.startedWith)
        adapter.processAudioChunk(ByteArray(320))
        adapter.stop()
        assertEquals(listOf("断线"), sink.errors)
        assertEquals(0, local.bytes)
        assertFalse(adapter.resultKeepsPunctuation)
        assertTrue(runCatching { adapter.start(InputLanguage.ENGLISH) }.isFailure)
    }

    @Test
    fun `声明 languages any 的插件收到录音语言`() {
        val plugin = FakePlugin()
        val adapter = PluginAsrBackendAdapter("在线语音", plugin, batch)
        Sink().bind(adapter)
        adapter.start(InputLanguage.JAPANESE)
        assertEquals("ja", plugin.startedWith)
    }
}
