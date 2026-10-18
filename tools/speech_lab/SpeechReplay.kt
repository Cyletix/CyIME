package com.kingzcheung.xime.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

private fun floats(bytes: ByteArray): FloatArray {
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(b.remaining()).also { b.get(it) }
}
private fun encode(pcm: FloatArray): String {
    val b = ByteBuffer.allocate(pcm.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    pcm.forEach(b::putFloat)
    return Base64.getEncoder().encodeToString(b.array())
}
private fun exchange(line: String): String {
    println(line)
    System.out.flush()
    return checkNotNull(readlnOrNull()) { "Native replay bridge closed" }
}
private fun text(response: String) = String(Base64.getDecoder().decode(response), Charsets.UTF_8)

/** Actual app gain/segmentation; native VAD and decoder are supplied synchronously by sherpa Python. */
fun main(args: Array<String>) {
    val dir = File(args[0])
    for (source in dir.listFiles().orEmpty().filter { it.name.endsWith(".raw.f32") }.sortedBy { it.name }) {
        val name = source.name.removeSuffix(".raw.f32")
        val pcm = floats(source.readBytes())
        for (mode in args.drop(1).ifEmpty { listOf("before", "after") }) {
            require(mode in listOf("before", "after")) { "Unknown replay mode: $mode" }
            exchange("CASE\t$name\t$mode")
            val level = SpeechDetectionLevel()
            var position = 0
            var samples = 0
            var lastId = -1L
            val detector = object : SpeechDetector {
                override fun speech(frame: FloatArray): Boolean {
                    val input = if (mode == "after") level.process(frame) else frame
                    return exchange("VAD\t${encode(input)}") == "1"
                }
                override fun reset() { exchange("RESET"); level.reset() }
            }
            lateinit var segmenter: SpeechSegmenter
            segmenter = SpeechSegmenter(detector, 12000, { id, audio ->
                if (id != lastId) { lastId = id; samples = 0 }
                samples += audio.size
                val result = text(exchange("AUDIO\t$position\t${encode(audio)}"))
                segmenter.charactersPerSecond = cleanSpeechText(result).length / (samples / 16000f).coerceAtLeast(.5f)
            }, { _, audio, boundary ->
                exchange("END\t${audio.size}\t$boundary")
            }, useEnergyPause = mode == "before", resetDetectorOnPause = mode == "after")
            for (offset in pcm.indices step 1600) {
                position = minOf(offset + 1600, pcm.size)
                segmenter.accept(pcm.copyOfRange(offset, position))
            }
            segmenter.finishInput()
            exchange("DONE")
        }
    }
}
