package com.kingzcheung.xime.speech

import com.kingzcheung.xime.settings.InputLanguage

interface AsrBackend {
    val name: String
    /** Bounded finalization allowance; capture releases the microphone before waiting. */
    val finishTimeoutMs: Long get() = 8000L
    
    fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit
    )
    
    fun initialize(): Boolean
    /** Local warmup uses the same language as the upcoming recording; legacy plugins ignore it. */
    fun initialize(language: InputLanguage): Boolean = initialize()
    /** On-demand capture must not wait for optional model warmup. */
    fun prepareForRecording(language: InputLanguage): Boolean = initialize(language)
    fun start(): Boolean
    /** Legacy online plugins have no language contract; preserve Chinese and reject unverified routing. */
    fun start(language: InputLanguage): Boolean {
        require(language == InputLanguage.CHINESE) { "当前语音后端尚未声明${language.displayName}支持，请使用本地多语言模型" }
        return start()
    }
    /** Only true if the current recognizer and its configured pipeline support this language. */
    fun canContinueIn(language: InputLanguage): Boolean = false
    fun processAudioChunk(buffer: ByteArray)
    fun stop()
    fun cancel()
    fun release()
    fun isAvailable(): Boolean
}
