package com.kingzcheung.xime.speech

import com.kingzcheung.xime.settings.InputLanguage

interface AsrBackend {
    val name: String
    
    fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit
    )
    
    fun initialize(): Boolean
    fun start(): Boolean
    /** Legacy online plugins have no language contract; preserve Chinese and reject unverified routing. */
    fun start(language: InputLanguage): Boolean {
        require(language == InputLanguage.CHINESE) { "当前语音后端尚未声明${language.displayName}支持，请使用本地多语言模型" }
        return start()
    }
    fun processAudioChunk(buffer: ByteArray)
    fun stop()
    fun cancel()
    fun release()
    fun isAvailable(): Boolean
}
