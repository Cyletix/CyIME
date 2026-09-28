package com.kingzcheung.xime.speech

/** One primary recognizer, optionally followed by SenseVoice. Existing stored IDs remain valid. */
internal object SpeechModelSelection {
    val primaryIds = listOf(SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE)

    fun primary(mode: String): String = when (mode) {
        SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.ZIPFORMER
        SpeechModelCatalog.TWO_PASS -> SpeechModelCatalog.PARAFORMER
        else -> mode
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
        SpeechModelCatalog.SENSEVOICE -> model
        else -> error("不支持的语音模型：$model")
    }

    fun displayName(model: String): String? = when (model) {
        SpeechModelCatalog.ZIPFORMER -> "Zipformer · 中文"
        SpeechModelCatalog.PARAFORMER -> "Paraformer · 中英"
        SpeechModelCatalog.SENSEVOICE -> "SenseVoice · 多语言"
        else -> null
    }
}
