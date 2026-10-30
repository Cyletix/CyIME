package com.kingzcheung.xime.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

private fun encode(pcm: FloatArray): String {
    val b = ByteBuffer.allocate(pcm.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    pcm.forEach(b::putFloat)
    return Base64.getEncoder().encodeToString(b.array())
}
private fun exchange(line: String): String {
    println(line); System.out.flush()
    return checkNotNull(readlnOrNull()) { "Replay bridge closed" }
}
private fun decode(s: String) = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

/** Reuses production segmentation and transcript assembly; no Android or microphone involved. */
fun main(args: Array<String>) {
    for (file in File(args[0]).listFiles().orEmpty().filter { it.name.endsWith(".raw.f32") }.sortedBy { it.name }) {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val pcm = FloatArray(buffer.remaining()).also { buffer.get(it) }
        if (args.getOrNull(1) == "ja") {
            for (mode in listOf("int8", "fp32")) {
                exchange("CASE\t${file.name.removeSuffix(".raw.f32")}\t$mode")
                val text = JapaneseZipformerDecoding.decode(pcm, recognize = { audio ->
                    val result = exchange("DECODE\t${encode(audio)}").split('|')
                    JapaneseZipformerDecoding.Output(decode(result[0]), emptyList(),
                        result[1].split(',').filter { it.isNotEmpty() }.map { it.toFloat() })
                }, speech = { audio ->
                    exchange("SPANS\t${encode(audio)}").split(';').filter { it.isNotEmpty() }.map {
                        val span = it.split(','); span[0].toDouble() to span[1].toDouble()
                    }
                })
                exchange("DONE_JA\t${Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))}")
            }
            continue
        }
        // Optional variants: single-pause<ms> keeps one Paraformer with a longer minimum pause.
        val modes = System.getenv("CYIME_REPLAY_MODES")?.split(',')?.filter { it.isNotBlank() } ?: listOf("single", "refinement")
        for (mode in modes) {
            exchange("CASE\t${file.name.removeSuffix(".raw.f32")}\t$mode")
            val level = SpeechDetectionLevel()
            val first = SpeechTranscript()
            val second = SpeechTranscript()
            var lastId = -1L
            var count = 0
            val detector = object : SpeechDetector {
                override fun speech(frame: FloatArray) = exchange("VAD\t${encode(level.process(frame))}") == "1"
                override fun reset() { exchange("RESET"); level.reset() }
            }
            lateinit var segmenter: SpeechSegmenter
            val single = mode.startsWith("single")
            // "refinement-whole": the 整句定稿 setting (production cap, 1 s streaming pause).
            val whole = mode == "refinement-whole"
            segmenter = SpeechSegmenter(detector, if (single) 12000 else if (whole) WHOLE_SENTENCE_MAX_SEGMENT_MS else 6000,
                { id, audio ->
                    if (id != lastId) { lastId = id; count = 0 }
                    count += audio.size
                    val partial = decode(exchange("AUDIO\t${encode(audio)}"))
                    segmenter.charactersPerSecond = cleanSpeechText(partial).length / (count / 16000f).coerceAtLeast(.5f)
                },
                { id, audio, boundary ->
                    val parts = exchange("END\t$boundary\t${encode(audio)}").split('|')
                    first.update(id, decode(parts[0]), boundary)
                    second.update(id, cleanSenseVoiceText(decode(parts[1])), boundary)
                }, minimumPauseMs = if (mode == "single") 0 else if (whole) 1000 else if (mode.contains("-pause")) mode.substringAfter("-pause").toInt() else 600,
                useEnergyPause = false, resetDetectorOnPause = true)
            for (offset in pcm.indices step 1600) segmenter.accept(pcm.copyOfRange(offset, minOf(offset + 1600, pcm.size)))
            segmenter.finishInput()
            fun text(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            exchange("DONE\t${text(first.text())}\t${text(second.text())}")
        }
    }
}
