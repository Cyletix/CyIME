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
    override val hasRefinement get() = selection.first != null && selection.secondDir != null
    override val isStreaming get() = online != null
    init {
        try {
            val first = selection.first
            if (first != null) {
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
            if (selection.secondDir != null) {
                offline = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = requireFile(selection.secondDir, "model.int8.onnx"), language = "auto", useInverseTextNormalization = true),
                    tokens = requireFile(selection.secondDir, "tokens.txt"), numThreads = 1,
                )))
            }
            vad = Vad(context.assets, VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(
                model = "speech/silero_vad.onnx", threshold = .6f, minSilenceDuration = .064f,
                minSpeechDuration = .15f, windowSize = 512, maxSpeechDuration = 30f)))
        } catch (error: Throwable) { close(); throw error }
    }

    override fun detector(): SpeechDetector = object : SpeechDetector {
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
        val recognizer = checkNotNull(offline)
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(samples, 16000)
            recognizer.decode(s)
            return cleanSenseVoiceText(recognizer.getResult(s).text)
        } finally { s.release() }
    }
    override fun reset() { stream?.release(); stream = null; vad?.reset() }
    override fun close() { reset(); online?.release(); online = null; offline?.release(); offline = null; vad?.release(); vad = null }
    private fun requireFile(dir: File, name: String): String = File(dir, name).also {
        require(it.isFile && it.length() > 0) { "语音模型文件缺失：$name" }
    }.absolutePath
}
