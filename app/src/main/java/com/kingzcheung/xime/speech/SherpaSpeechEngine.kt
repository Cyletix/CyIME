package com.kingzcheung.xime.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/** 0.25 s; X-ASR fails below 1440 samples (see [SherpaSpeechEngine.createXasr]). */
private const val XASR_MIN_SAMPLES = 4000

/** Official sherpa-onnx 1.13.8, matching ASRInput. Each recognizer has one owning worker. */
internal class SherpaSpeechEngine(context: Context, val selection: AsrModelManager.Selection) : LocalSpeechEngine {
    private var online: OnlineRecognizer? = null
    private var offline: OfflineRecognizer? = null
    /** Japanese captions: SenseVoice in Japanese, used only for its punctuation (see projectPunctuation). */
    private var punctuator: OfflineRecognizer? = null
    /** 整句定稿的 X-ASR（只在 [AsrModelManager.Selection.finalizerDir] 存在时加载，代替 SenseVoice）。 */
    private var finalizer: OfflineRecognizer? = null
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
            // With X-ASR finalizing whole sentences, SenseVoice loads only if something still asks for it.
            // Beside a streaming model it loads in the background (prepareRefinement), so the first words
            // do not wait for it: 2026-10-30, sherpa-onnx 1.13.8 on x86, SenseVoice int8 takes 1.2–2.2 s to load.
            if (selection.secondDir != null && selection.finalizerDir == null) {
                if (japanese) punctuator = createSenseVoice(selection.secondDir)
                else if (online == null) offline = createSenseVoice(selection.secondDir)
            }
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
    /**
     * Captions get the same VAD-only gain as the keyboard (target 0.2): without it a tablet at
     * arm's length heard nothing at all (2026-10-21, 1.14.77: the keyboard recognised, captions
     * never opened a segment; tablet recordings gave 0 speech frames raw vs 28–110 with the gain).
     * Room noise no longer merges turns as on 10-16: the 5 s cap and energy pauses split them, and
     * fan-only, hiss, hum and click replays still never start recognition with this gain.
     */
    override fun captionDetector(): SpeechDetector = object : SpeechDetector {
        private val level = SpeechDetectionLevel()
        override fun speech(frame: FloatArray): Boolean {
            val v = checkNotNull(vad)
            v.acceptWaveform(level.process(frame))
            val active = v.isSpeechDetected()
            while (!v.empty()) v.pop()
            return active
        }
        override fun reset() { vad?.reset(); level.reset() }
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
            return cleanSenseVoiceText(result.text, result.lang)
        } finally { s.release() }
    }
    override fun refinePunctuated(samples: FloatArray): String {
        if (japanese) return refine(samples)
        val xasr = selection.finalizerDir?.let { dir -> finalizer ?: createXasr(dir).also { finalizer = it } }
        val recognizer = xasr ?: refiner()
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(if (xasr != null && samples.size < XASR_MIN_SAMPLES) samples.copyOf(XASR_MIN_SAMPLES) else samples, 16000)
            recognizer.decode(s)
            val result = recognizer.getResult(s)
            return cleanSenseVoiceText(result.text, result.lang, keepMarks = true)
        } finally { s.release() }
    }
    override fun refineRich(samples: FloatArray): RichTranscript {
        if (japanese) {
            val words = refine(samples)
            val marks = punctuator ?: return RichTranscript(words, "ja")
            val s = marks.createStream()
            return try {
                s.acceptWaveform(samples, 16000)
                marks.decode(s)
                RichTranscript(com.kingzcheung.xime.speech.caption.projectPunctuation(words, marks.getResult(s).text), "ja")
            } finally { s.release() }
        }
        val recognizer = refiner()
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(samples, 16000)
            recognizer.decode(s)
            val result = recognizer.getResult(s)
            return RichTranscript(result.text, result.lang, result.event, result.tokens, result.timestamps)
        } finally { s.release() }
    }
    /** Captions without a streaming model (English SenseVoice, Japanese Zipformer) re-read the sentence so far. */
    override val hasCaptionPreview get() = online == null && offline != null

    override fun preview(samples: FloatArray): String {
        val recognizer = checkNotNull(offline)
        if (!japanese) {
            val s = recognizer.createStream()
            return try {
                s.acceptWaveform(samples, 16000)
                recognizer.decode(s)
                val r = recognizer.getResult(s)
                cleanSenseVoiceText(r.text, r.lang)
            } finally { s.release() }
        }
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
        createSenseVoice(checkNotNull(selection.secondDir)).also { offline = it }
    }
    override val refinementLoaded: Boolean get() = japanese || online == null ||
        (if (selection.finalizerDir != null) finalizer != null else selection.secondDir == null || offline != null)
    override fun prepareRefinement() {
        if (refinementLoaded) return
        val xasr = selection.finalizerDir
        if (xasr != null) finalizer = createXasr(xasr) else refiner()
    }
    override fun releaseRefinement(): Boolean {
        if (japanese || selection.first == null || selection.secondDir == null) return false
        if (offline == null && finalizer == null) return false
        offline?.release(); offline = null
        finalizer?.release(); finalizer = null
        return true
    }
    // The X-ASR int8 export aborts in its encoder (Reshape) on inputs of 40.2 s or more (2026-10-30 scan,
    // 25–48 s in 0.16 s steps; none below). Whole-sentence regions stop at 30 s and re-read blocks at 28 s.
    // It also fails (Conv input shape) below 1440 samples, so shorter input is padded with silence.
    private fun createXasr(dir: File) = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
        transducer = OfflineTransducerModelConfig(requireFile(dir, "encoder-epoch-99-avg-1.int8.onnx"),
            requireFile(dir, "decoder-epoch-99-avg-1.onnx"), requireFile(dir, "joiner-epoch-99-avg-1.int8.onnx")),
        tokens = requireFile(dir, "tokens.txt"), numThreads = 2, provider = "cpu"),
        decodingMethod = "greedy_search"))
    private fun createSenseVoice(dir: File) = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
        senseVoice = OfflineSenseVoiceModelConfig(
            model = requireFile(dir, "model.int8.onnx"), language = selection.recognitionLanguage, useInverseTextNormalization = true),
        // Runs only at a pause/stop, beside the one streaming thread; two threads shorten the final wait.
        tokens = requireFile(dir, "tokens.txt"), numThreads = 2,
    )))
    override fun close() { reset(); online?.release(); online = null; offline?.release(); offline = null; finalizer?.release(); finalizer = null; punctuator?.release(); punctuator = null; vad?.release(); vad = null; japaneseVad?.release(); japaneseVad = null }
    private fun japaneseVadConfig() = VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
        model = "speech/silero_vad.onnx", threshold = .5f, minSilenceDuration = .2f,
        minSpeechDuration = .15f, windowSize = 512, maxSpeechDuration = 30f))
    private fun requireFile(dir: File, name: String): String = File(dir, name).also {
        require(it.isFile && it.length() > 0) { "语音模型文件缺失：$name" }
    }.absolutePath
}
