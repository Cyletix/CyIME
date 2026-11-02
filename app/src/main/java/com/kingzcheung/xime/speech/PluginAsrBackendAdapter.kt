package com.kingzcheung.xime.speech

import com.kingzcheung.xime.plugin.core.api.AsrPluginBackend
import com.kingzcheung.xime.plugin.core.api.AsrPluginListener
import com.kingzcheung.xime.plugin.core.api.AsrPluginState
import com.kingzcheung.xime.plugin.core.model.PluginCapabilities
import com.kingzcheung.xime.settings.InputLanguage
import java.io.ByteArrayOutputStream

/**
 * 语音插件 → 宿主语音后端。按插件的能力声明（manifest capabilities.speech）多做三件事：
 * - `languages: any`：录音语言（zh/en/ja）交给插件，不再只限中文；
 * - `keepsPunctuation: true`：插件给的最终结果自带标点（跟随「自动加标点」和逐项开关）；
 * - `inputMode: batch`（录完整段上传）：收尾等 30 秒；停止时插件报错，就把这段录音交给本地模型
 *   识别，并用 [onNotice] 在键盘上说明，不会无声无息地结束。本地也不行才报错。
 */
class PluginAsrBackendAdapter(
    private val pluginName: String,
    private val pluginBackend: AsrPluginBackend,
    private val capabilities: PluginCapabilities.SpeechCapabilities = PluginCapabilities.SpeechCapabilities(),
    private val localFallback: () -> AsrBackend? = { null },
    private val onNotice: (String) -> Unit = {},
) : AsrBackend {

    override val name: String = pluginName

    override val finishTimeoutMs: Long
        get() = if (capabilities.isBatch) BATCH_FINISH_TIMEOUT_MS else super.finishTimeoutMs

    /** 只有插件自己的结果带标点；本地模型补识别的那段没有。 */
    @Volatile private var keepsPunctuation = false
    override val resultKeepsPunctuation: Boolean get() = keepsPunctuation

    private var resultCallback: ((String) -> Unit)? = null
    private var partialResultCallback: ((String) -> Unit)? = null
    private var stateCallback: ((RecognitionState) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    /** batch 插件这段的录音，留给失败时本地补识别。 */
    private val audio = ByteArrayOutputStream()
    private var language = InputLanguage.CHINESE
    @Volatile private var stopping = false
    @Volatile private var stopFailure: String? = null

    private val listener = object : AsrPluginListener {
        override fun onFinal(text: String) {
            keepsPunctuation = capabilities.keepsPunctuation
            resultCallback?.invoke(text)
        }

        override fun onPartial(text: String) {
            partialResultCallback?.invoke(text)
        }

        override fun onError(message: String) {
            // 整段上传在停止时失败：先记下，stop() 里改用本地模型。
            if (capabilities.isBatch && stopping) {
                stopFailure = message
                return
            }
            errorCallback?.invoke(message)
        }

        override fun onStateChanged(state: AsrPluginState) {
            stateCallback?.invoke(state.toRecognitionState())
        }
    }

    override fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)?,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit
    ) {
        resultCallback = onResult
        partialResultCallback = onPartialResult
        stateCallback = onStateChange
        errorCallback = onError
        pluginBackend.setListener(listener)
    }

    override fun initialize(): Boolean = pluginBackend.initialize()

    override fun start(): Boolean {
        language = InputLanguage.CHINESE
        resetAudio()
        return pluginBackend.start()
    }

    override fun start(language: InputLanguage): Boolean {
        if (!capabilities.anyLanguage) return super.start(language)
        this.language = language
        resetAudio()
        return pluginBackend.start(language.id)
    }

    override fun processAudioChunk(buffer: ByteArray) {
        if (capabilities.isBatch) synchronized(audio) { audio.write(buffer) }
        pluginBackend.processAudioChunk(buffer)
    }

    override fun stop() {
        stopFailure = null
        stopping = true
        try {
            pluginBackend.stop()
        } finally {
            stopping = false
        }
        val failure = stopFailure
        stopFailure = null
        val pcm = synchronized(audio) { audio.toByteArray().also { audio.reset() } }
        if (failure == null) return
        if (recognizeLocally(pcm)) {
            onNotice("$pluginName 没识别出来（$failure），这次已改用本地模型")
        } else {
            errorCallback?.invoke("$pluginName：$failure；本地模型也没能识别")
            stateCallback?.invoke(RecognitionState.ERROR)
        }
    }

    /** 把这段录音交给本地模型识别；本地也不可用时返回 false。 */
    private fun recognizeLocally(pcm: ByteArray): Boolean {
        if (pcm.isEmpty()) return false
        val local = runCatching { localFallback() }.getOrNull() ?: return false
        return try {
            var text: String? = null
            local.setCallbacks(onResult = { text = (text.orEmpty() + it) }, onPartialResult = null,
                onStateChange = {}, onError = {})
            if (!local.initialize(language) || !local.start(language)) return false
            pcm.asList().chunked(3200).forEach { local.processAudioChunk(it.toByteArray()) }
            local.stop()
            keepsPunctuation = false
            resultCallback?.invoke(text.orEmpty())
            stateCallback?.invoke(RecognitionState.IDLE)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { local.release() }
        }
    }

    override fun cancel() {
        resetAudio()
        pluginBackend.cancel()
    }

    override fun release() {
        resetAudio()
        pluginBackend.release()
    }

    override fun isAvailable(): Boolean = true

    private fun resetAudio() {
        synchronized(audio) { audio.reset() }
    }

    private fun AsrPluginState.toRecognitionState(): RecognitionState = when (this) {
        AsrPluginState.IDLE -> RecognitionState.IDLE
        AsrPluginState.LISTENING -> RecognitionState.LISTENING
        AsrPluginState.PROCESSING -> RecognitionState.PROCESSING
        AsrPluginState.ERROR -> RecognitionState.ERROR
    }

    private companion object {
        const val BATCH_FINISH_TIMEOUT_MS = 30_000L
    }
}
