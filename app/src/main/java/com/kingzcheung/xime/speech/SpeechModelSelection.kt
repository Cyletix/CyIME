package com.kingzcheung.xime.speech

/** One primary recognizer, optionally followed by SenseVoice. Existing stored IDs remain valid. */
internal object SpeechModelSelection {
    val primaryIds = listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA)

    fun supportsCorrection(mode: String) = primary(mode) in setOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.ZIPFORMER)

    fun primary(mode: String): String = when (mode) {
        SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.ZIPFORMER
        SpeechModelCatalog.TWO_PASS -> SpeechModelCatalog.PARAFORMER
        else -> mode
    }

    /**
     * Dolphin / TeleSpeech dialect correction (1.14.25, stored as "primary+model") was removed after
     * Henan dialect tests: SenseVoice was better on both videos. Such a choice falls back to SenseVoice.
     */
    val retiredCorrectors = setOf("dolphin-base-ctc-int8", "telespeech-ctc-int8")
    fun withoutRetiredCorrector(mode: String): String {
        val at = mode.lastIndexOf('+')
        return if (at > 0 && mode.substring(at + 1) in retiredCorrectors) withCorrection(mode.substring(0, at), true) else mode
    }

    fun hasCorrection(mode: String): Boolean = mode == SpeechModelCatalog.ZIPFORMER_TWO_PASS || mode == SpeechModelCatalog.TWO_PASS

    fun selectPrimary(current: String, model: String): String {
        require(model in primaryIds)
        return withCorrection(model, hasCorrection(current))
    }

    fun withCorrection(mode: String, enabled: Boolean): String = when (val model = primary(mode)) {
        SpeechModelCatalog.ZIPFORMER -> if (enabled) SpeechModelCatalog.ZIPFORMER_TWO_PASS else model
        SpeechModelCatalog.PARAFORMER -> if (enabled) SpeechModelCatalog.TWO_PASS else model
        // SenseVoice is a complete standalone choice and never corrects itself.
        SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.ZIPFORMER_JA, SpeechModelCatalog.ZIPFORMER_JA_INT8 -> model
        else -> error("不支持的语音模型：$model")
    }

    /**
     * 整句定稿 really changes one language's dictation: Chinese only, with a streaming preview + SenseVoice
     * correction, or SenseVoice alone. Paraformer / Zipformer alone and other languages ignore the switch.
     */
    fun wholeSentenceApplies(mode: String, chinese: Boolean): Boolean =
        chinese && (hasCorrection(mode) || primary(mode) == SpeechModelCatalog.SENSEVOICE)

    /** X-ASR (when downloaded) finalizes instead of SenseVoice only for Chinese preview + correction with 整句定稿 on. */
    fun xasrFinalizes(mode: String, chinese: Boolean, wholeSentenceOn: Boolean): Boolean =
        wholeSentenceOn && chinese && hasCorrection(mode)

    /** What decodes one language's dictation; the :asr service and every line of the voice settings page use this. */
    data class Pipeline(
        /** Shows words while speaking (Paraformer / Zipformer); null = text appears at each pause. */
        val preview: String?,
        /** Writes the final text: [SpeechModelCatalog.XASR], SenseVoice, or the primary model itself. */
        val finalizer: String,
        val wholeSentence: Boolean,
    )

    fun pipeline(mode: String, chinese: Boolean, wholeSentenceOn: Boolean, xasrReady: Boolean): Pipeline {
        val primary = primary(mode)
        val finalizer = when {
            !hasCorrection(mode) -> primary
            xasrReady && xasrFinalizes(mode, chinese, wholeSentenceOn) -> SpeechModelCatalog.XASR
            else -> SpeechModelCatalog.SENSEVOICE
        }
        val streaming = primary == SpeechModelCatalog.PARAFORMER || primary == SpeechModelCatalog.ZIPFORMER
        return Pipeline(primary.takeIf { streaming }, finalizer, wholeSentenceOn && wholeSentenceApplies(mode, chinese))
    }

    fun displayName(model: String): String? = when (model) {
        SpeechModelCatalog.ZIPFORMER -> "Zipformer · 中文"
        SpeechModelCatalog.ZIPFORMER_JA -> "Zipformer · 日语原版"
        SpeechModelCatalog.ZIPFORMER_JA_INT8 -> "Zipformer · 日语轻量"
        SpeechModelCatalog.PARAFORMER -> "Paraformer · 中英"
        SpeechModelCatalog.SENSEVOICE -> "SenseVoice · 多语言"
        else -> null
    }
}
