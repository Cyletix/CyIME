package com.kingzcheung.xime.speech

/** One primary recognizer, optionally followed by SenseVoice or a Chinese dialect pass. Existing stored IDs remain valid. */
internal object SpeechModelSelection {
    val primaryIds = listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA)

    fun supportsCorrection(mode: String) = primary(mode) in setOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.ZIPFORMER)

    fun primary(mode: String): String = DialectCorrection.primary(mode) ?: when (mode) {
        SpeechModelCatalog.ZIPFORMER_TWO_PASS -> SpeechModelCatalog.ZIPFORMER
        SpeechModelCatalog.TWO_PASS -> SpeechModelCatalog.PARAFORMER
        else -> mode
    }

    fun hasCorrection(mode: String): Boolean = mode == SpeechModelCatalog.ZIPFORMER_TWO_PASS || mode == SpeechModelCatalog.TWO_PASS ||
        DialectCorrection.corrector(mode) != null

    /** The second-pass model of a two-pass mode, or null without correction. */
    fun corrector(mode: String): String? =
        if (hasCorrection(mode)) DialectCorrection.corrector(mode) ?: SpeechModelCatalog.SENSEVOICE else null

    /** Turn correction on with [corrector] (SenseVoice keeps its original mode IDs). */
    fun withCorrector(mode: String, corrector: String): String {
        require(supportsCorrection(mode)) { "当前主模型不支持二次校正" }
        return if (corrector == SpeechModelCatalog.SENSEVOICE) withCorrection(primary(mode), true)
        else DialectCorrection.mode(primary(mode), corrector)
    }

    fun selectPrimary(current: String, model: String): String {
        require(model in primaryIds)
        // A chosen dialect pass follows the new primary when that primary can be corrected.
        val dialect = DialectCorrection.corrector(current)
        return if (dialect != null && supportsCorrection(model)) withCorrector(model, dialect)
        else withCorrection(model, hasCorrection(current))
    }

    fun withCorrection(mode: String, enabled: Boolean): String = if (enabled && DialectCorrection.corrector(mode) != null) mode else when (val model = primary(mode)) {
        SpeechModelCatalog.ZIPFORMER -> if (enabled) SpeechModelCatalog.ZIPFORMER_TWO_PASS else model
        SpeechModelCatalog.PARAFORMER -> if (enabled) SpeechModelCatalog.TWO_PASS else model
        // SenseVoice is a complete standalone choice and never corrects itself.
        SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.ZIPFORMER_JA, SpeechModelCatalog.ZIPFORMER_JA_INT8 -> model
        else -> error("不支持的语音模型：$model")
    }

    fun displayName(model: String): String? = when (model) {
        SpeechModelCatalog.ZIPFORMER -> "Zipformer · 中文"
        SpeechModelCatalog.ZIPFORMER_JA -> "Zipformer · 日语原版"
        SpeechModelCatalog.ZIPFORMER_JA_INT8 -> "Zipformer · 日语轻量"
        SpeechModelCatalog.PARAFORMER -> "Paraformer · 中英"
        SpeechModelCatalog.SENSEVOICE -> "SenseVoice · 多语言"
        else -> DialectCorrection.displayName(model)
    }
}
