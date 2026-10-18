package com.kingzcheung.xime.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/** Official sherpa-onnx 1.13.8, matching ASRInput. Each recognizer has one owning worker. */
internal class SherpaSpeechEngine(context: Context, val selection: AsrModelManager.Selection) : LocalSpeechEngine {
    private var online: OnlineRecognizer? = null
    private var offline: OfflineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null
    private var japaneseVad: Vad? = null // Refinement worker only; capture owns the other VAD.
    private val japanese get() = SpeechModelCatalog.isJapaneseZipformer(selection.mode)
    private val paraformer get() = selection.first?.modelType == "paraformer"
    override val hasRefinement get() = selection.first != null && selection.secondDir != null
    override val isStreaming get() = online != null
    override val maxSegmentMs get() = if (japanese) 20000 else super.maxSegmentMs
    override val minimumPauseMs get() = if (japanese) 1000 else super.minimumPauseMs
    override val hasRollingPreview get() = japanese
    override val lockedChinese get() = selection.recognitionLanguage == "zh"
    // Quiet speech validated by VAD must not be vetoed by a recent louder phrase's RMS.
    override val useEnergyPause get() = !japanese && !paraformer
    override val resetDetectorOnPause get() = paraformer
    override val decodeTimeoutMs get() = if (japanese) JapaneseZipformerDecoding.decodeTimeoutMs else super.decodeTimeoutMs
    init {
        try {
            val first = selection.first
            if (japanese) {
                val dir = checkNotNull(selection.firstDir)
                val info = checkNotNull(first)
                offline = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(requireFile(dir, info.encoderFile),
                        requireFile(dir, info.decoderFile), requireFile(dir, info.joinerFile)),
                    tokens = requireFile(dir, "tokens.txt"), numThreads = 2, provider = "cpu"),
                    decodingMethod = "greedy_search"))
                japaneseVad = Vad(context.assets, japaneseVadConfig())
            } else if (first != null) {
                val dir = selection.firstDir!!
                fun path(name: String) = requireFile(dir, name)
                val model = if (first.modelType == "paraformer") OnlineModelConfig(
                    paraformer = OnlineParaformerModelConfig(path(first.encoderFile), path(first.decoderFile)),
                    tokens = path("tokens.txt"), numThreads = 1,
                ) else OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(path(first.encoderFile), path(first.decoderFile), path(first.joinerFile)),
                    tokens = path("tokens.txt"), numThreads = 1,
                )
                online = OnlineRecognizer(config = OnlineRecognizerConfig(modelConfig = model, enableEndpoint = false))
            }
            if (selection.secondDir != null) offline = createRefiner(selection.secondDir)
            vad = Vad(context.assets, if (japanese) japaneseVadConfig() else VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
                model = "speech/silero_vad.onnx", threshold = .6f, minSilenceDuration = .064f,
                minSpeechDuration = .15f, windowSize = 512, maxSpeechDuration = 30f)))
        } catch (error: Throwable) { close(); throw error }
    }

    override fun detector(): SpeechDetector = object : SpeechDetector {
        private val level = SpeechDetectionLevel()
        override fun speech(frame: FloatArray): Boolean {
            val v = checkNotNull(vad)
            v.acceptWaveform(if (paraformer) level.process(frame) else frame)
            val active = v.isSpeechDetected()
            while (!v.empty()) v.pop()
            return active
        }
        override fun reset() { vad?.reset(); level.reset() }
    }
    override fun captionDetector(): SpeechDetector = object : SpeechDetector {
        override fun speech(frame: FloatArray): Boolean {
            val v = checkNotNull(vad)
            v.acceptWaveform(frame)
            val active = v.isSpeechDetected()
            while (!v.empty()) v.pop()
            return active
        }
        override fun reset() { vad?.reset() }
    }
    override fun accept(samples: FloatArray): String {
        val recognizer = online ?: return ""
        val s = stream ?: recognizer.createStream().also { stream = it }
        s.acceptWaveform(samples, 16000)
        while (recognizer.isReady(s)) recognizer.decode(s)
        return recognizer.getResult(s).text
    }
    override fun finishStream(): String {
        val recognizer = online ?: return ""
        val s = stream ?: return ""
        try {
            if (selection.first?.modelType == "paraformer") s.setOption("is_final", "1")
            else s.acceptWaveform(FloatArray(4800), 16000) // Zipformer right context / final short chunk
            s.inputFinished()
            while (recognizer.isReady(s)) recognizer.decode(s)
            return recognizer.getResult(s).text
        } finally { s.release(); stream = null }
    }
    override fun refine(samples: FloatArray): String {
        val recognizer = refiner()
        if (japanese) return JapaneseZipformerDecoding.decode(samples, recognize = { padded ->
            val s = recognizer.createStream()
            try {
                s.acceptWaveform(padded, 16000)
                recognizer.decode(s)
                val r = recognizer.getResult(s)
                JapaneseZipformerDecoding.Output(r.text, r.tokens.toList(), r.timestamps.toList())
            } finally { s.release() }
        }, speech = { pcm ->
            val v = checkNotNull(japaneseVad)
            v.reset()
            val spans = mutableListOf<Pair<Double, Double>>()
            fun drain() { while (!v.empty()) {
                val segment = v.front()
                spans.add(segment.start / 16000.0 to (segment.start + segment.samples.size) / 16000.0)
                v.pop()
            } }
            for (offset in pcm.indices step 512) {
                v.acceptWaveform(pcm.copyOfRange(offset, minOf(offset + 512, pcm.size)).copyOf(512))
                drain()
            }
            v.flush(); drain()
            spans
        })
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(samples, 16000)
            recognizer.decode(s)
            val result = recognizer.getResult(s)
            if (dialect) return DialectCorrection.clean(result.text)
            return cleanSenseVoiceText(result.text, result.lang)
        } finally { s.release() }
    }
    override fun refineRich(samples: FloatArray): RichTranscript {
        if (japanese || dialect) return RichTranscript(refine(samples))
        val recognizer = refiner()
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(samples, 16000)
            recognizer.decode(s)
            val result = recognizer.getResult(s)
            return RichTranscript(result.text, result.lang, result.event, result.tokens, result.timestamps)
        } finally { s.release() }
    }
    override fun preview(samples: FloatArray): String {
        check(japanese)
        val recognizer = checkNotNull(offline)
        val s = recognizer.createStream()
        try {
            val padded = FloatArray(samples.size + JapaneseZipformerDecoding.paddingSamples * 2)
            samples.copyInto(padded, JapaneseZipformerDecoding.paddingSamples)
            s.acceptWaveform(padded, 16000)
            recognizer.decode(s)
            val r = recognizer.getResult(s)
            return JapaneseZipformerDecoding.spaced(JapaneseZipformerDecoding.Output(r.text, r.tokens.toList(), r.timestamps.toList()))
        } finally { s.release() }
    }
    override fun reset() { stream?.release(); stream = null; vad?.reset() }
    /** 阶梯释放后在第一次定稿时重新加载 SenseVoice；日语模型本身就是离线识别器，不在此释放。 */
    private fun refiner(): OfflineRecognizer = offline ?: run {
        check(!japanese)
        createRefiner(checkNotNull(selection.secondDir)).also { offline = it }
    }
    override fun releaseRefinement(): Boolean {
        if (japanese || selection.first == null || selection.secondDir == null) return false
        val recognizer = offline ?: return false
        recognizer.release()
        offline = null
        return true
    }
    private val dialect get() = DialectCorrection.isDialect(selection.second)
    private fun createRefiner(dir: File) =
        if (dialect) DialectCorrection.createRecognizer(selection.second, dir) else createSenseVoice(dir)
    private fun createSenseVoice(dir: File) = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
        senseVoice = OfflineSenseVoiceModelConfig(
            model = requireFile(dir, "model.int8.onnx"), language = selection.recognitionLanguage, useInverseTextNormalization = true),
        // Runs only at a pause/stop, beside the one streaming thread; two threads shorten the final wait.
        tokens = requireFile(dir, "tokens.txt"), numThreads = 2,
    )))
    override fun close() { reset(); online?.release(); online = null; offline?.release(); offline = null; vad?.release(); vad = null; japaneseVad?.release(); japaneseVad = null }
    private fun japaneseVadConfig() = VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
        model = "speech/silero_vad.onnx", threshold = .5f, minSilenceDuration = .2f,
        minSpeechDuration = .15f, windowSize = 512, maxSpeechDuration = 30f))
    private fun requireFile(dir: File, name: String): String = File(dir, name).also {
        require(it.isFile && it.length() > 0) { "语音模型文件缺失：$name" }
    }.absolutePath
}
