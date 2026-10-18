package com.kingzcheung.xime.speech

import com.k2fsa.sherpa.onnx.OfflineDolphinModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.kingzcheung.xime.model.*
import java.io.File

/**
 * Optional dialect-robust second pass for Chinese, chosen instead of SenseVoice at pauses.
 * Download-only (never bundled); the streaming first pass is unchanged. Both are Chinese-only
 * in use: no language/dialect detection ever switches the recognizer.
 *
 * 2026-10-18 PC replay (FLEURS zh, 32 clips, 2 threads; see docs/development/dialect-correction-20261018.md):
 * SenseVoice CER 7.4 %, Dolphin base 16.1 %, TeleSpeech 22.9 % (neither adds punctuation or
 * Arabic numerals); median decode 0.74 / 0.37 / 0.61 s; peak process RSS 389 / 472 / 841 MiB.
 */
internal object DialectCorrection {
    const val DOLPHIN = "dolphin-base-ctc-int8"
    const val TELESPEECH = "telespeech-ctc-int8"
    /** Offered second passes for Chinese; SenseVoice stays first and default. */
    val correctorIds = listOf(SpeechModelCatalog.SENSEVOICE, DOLPHIN, TELESPEECH)
    private val dialectIds = setOf(DOLPHIN, TELESPEECH)
    fun isDialect(id: String) = id in dialectIds

    private const val SEPARATOR = "+"
    /** Stored mode for a primary + dialect corrector, e.g. "paraformer-zh-en-int8+dolphin-base-ctc-int8". */
    fun mode(primary: String, corrector: String): String {
        require(isDialect(corrector)) { "不支持的校正模型：$corrector" }
        return primary + SEPARATOR + corrector
    }
    /** Primary of a dialect mode, or null for any other mode. */
    fun primary(mode: String): String? = split(mode)?.first
    /** Dialect corrector of a mode, or null for any other mode (SenseVoice modes keep their old IDs). */
    fun corrector(mode: String): String? = split(mode)?.second
    private fun split(mode: String): Pair<String, String>? {
        val at = mode.lastIndexOf(SEPARATOR)
        if (at <= 0) return null
        val corrector = mode.substring(at + 1)
        return if (isDialect(corrector)) mode.substring(0, at) to corrector else null
    }

    fun displayName(id: String): String? = when (id) {
        DOLPHIN -> "Dolphin · 方言"
        TELESPEECH -> "TeleSpeech · 方言"
        else -> null
    }

    private const val dolphinBase = "https://huggingface.co/csukuangfj/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02/resolve/1f3a53d0ecf658f8b0974e2cfde368eee40732fa"
    private const val teleBase = "https://huggingface.co/csukuangfj/sherpa-onnx-telespeech-ctc-int8-zh-2024-06-04/resolve/d6ae7e614f84da611d9895867fdf4274fe07a00a"
    /** Official sherpa-onnx int8 exports, pinned by commit and hash. Same file names as SenseVoice. */
    val models = listOf(
        ModelInfo(DOLPHIN, "Dolphin · 方言", "中文二次校正，适合口音和多种方言；不加标点，数字写成汉字。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "1f3a53d0ecf6", size = "104.2 MB", files = listOf(
                ModelFile("model.int8.onnx", "$dolphinBase/model.int8.onnx", "a3aa46c97f3f60f135ff949793cb05fabe7a0b3c484dc2e3cc699d354ee11b76", 103729802L),
                ModelFile("tokens.txt", "$dolphinBase/tokens.txt", "c3788261a51df1899ea4b210b552cd42139204de72c0ad60f6cebb199078872e", 504662L)
            )))),
        ModelInfo(TELESPEECH, "TeleSpeech · 方言", "中文二次校正，覆盖较多方言；不加标点，数字写成汉字，运行内存较大。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "d6ae7e614f84", size = "341.1 MB", files = listOf(
                ModelFile("model.int8.onnx", "$teleBase/model.int8.onnx", "2bf52b42c971696dc02a2db94c52c8fc75fab321ac4cf10b5a2d6863a73a33b3", 340990949L),
                ModelFile("tokens.txt", "$teleBase/tokens.txt", "f1dea25fe751bc3eebd27b2ff3b86298f9598686a090701990dabddde0eba508", 66642L)
            )))),
    )

    /** Same thread count as the SenseVoice pass: runs only at a pause, beside the streaming thread. */
    fun createRecognizer(id: String, dir: File): OfflineRecognizer {
        fun path(name: String) = File(dir, name).also {
            require(it.isFile && it.length() > 0) { "语音模型文件缺失：$name" }
        }.absolutePath
        val model = when (id) {
            DOLPHIN -> OfflineModelConfig(dolphin = OfflineDolphinModelConfig(path("model.int8.onnx")),
                tokens = path("tokens.txt"), numThreads = 2)
            TELESPEECH -> OfflineModelConfig(teleSpeech = path("model.int8.onnx"), modelType = "telespeech_ctc",
                tokens = path("tokens.txt"), numThreads = 2)
            else -> error("不支持的校正模型：$id")
        }
        return OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model))
    }

    /**
     * Dolphin decodes 40 languages and emits word pieces with spaces; sherpa-onnx 1.13.8 does not
     * return its language/region tag (result.lang is empty), so there is nothing to act on.
     */
    fun clean(text: String): String = cleanSenseVoiceText(text)

    // Kana, Hangul, Thai, Lao, Myanmar, Khmer, Devanagari..Malayalam, Arabic, Cyrillic, Hebrew.
    private val foreign = Regex("[\\u0400-\\u04FF\\u0590-\\u06FF\\u0900-\\u0D7F\\u0E00-\\u0EFF\\u1000-\\u109F\\u1780-\\u17FF\\u3040-\\u30FF\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]")
    /** Chinese-only output: a pass that answers in another script is rejected and the preview kept. */
    fun hasForeignScript(text: String) = foreign.containsMatchIn(text)
}
