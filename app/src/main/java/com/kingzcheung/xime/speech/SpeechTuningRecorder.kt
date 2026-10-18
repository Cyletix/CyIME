package com.kingzcheung.xime.speech

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in local tuning capture: the exact PCM received by the recognizer plus its text,
 * region and error timeline. Files stay in the app's own storage and are never uploaded;
 * the PC tool replays them to compare segmentation and model settings on real speech.
 */
internal class SpeechTuningRecorder(private val dir: File, private val meta: Map<String, Any?>) {
    companion object {
        private const val MAX_SESSION_BYTES = 16000 * 2 * 300 // 5 minutes of 16 kHz PCM16
        private const val MAX_SESSIONS = 300
        private const val MAX_TOTAL_BYTES = 500L * 1024 * 1024
    }

    private val startedAt = System.nanoTime()
    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
    private val pcm = ByteArrayOutputStream()
    private val events = JSONArray()
    private var closed = false

    private fun elapsedMs() = (System.nanoTime() - startedAt) / 1_000_000

    @Synchronized fun audio(bytes: ByteArray) {
        if (!closed && pcm.size() + bytes.size <= MAX_SESSION_BYTES) pcm.write(bytes)
    }

    @Synchronized private fun event(type: String, fill: JSONObject.() -> Unit) {
        if (closed) return
        events.put(JSONObject().put("type", type).put("t_ms", elapsedMs())
            .put("received_audio_ms", pcm.size() / 32).apply(fill))
    }

    fun text(value: String) = event("text") { put("text", value) }
    fun region(id: Long, samples: Int, boundary: SpeechBoundary) =
        event("region") { put("id", id).put("duration_ms", samples / 16).put("boundary", boundary.name) }
    fun error(message: String) = event("error") { put("message", message) }

    /** Writes once; storage failure never affects recognition. */
    fun close(outcome: String, finalText: String) {
        val (audio, timeline) = synchronized(this) {
            if (closed) return
            event("end") { put("outcome", outcome).put("text", finalText) }
            closed = true
            pcm.toByteArray() to events
        }
        if (audio.isEmpty()) return
        try {
            dir.mkdirs()
            writeWav(File(dir, "$stamp.wav"), audio)
            val info = JSONObject()
            meta.forEach { (key, value) -> info.put(key, value) }
            info.put("audio_ms", audio.size / 32).put("outcome", outcome).put("final_text", finalText)
                .put("events", timeline)
            File(dir, "$stamp.json").writeText(info.toString(1))
            prune()
        } catch (_: Exception) { }
    }

    private fun writeWav(file: File, data: ByteArray) {
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); le32(36 + data.size); out.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(16000); le32(32000); le16(2); le16(16)
            out.writeBytes("data"); le32(data.size); out.write(data)
        }
    }

    /** Keeps the newest sessions within count and size limits; a pair is removed together. */
    private fun prune() {
        val sessions = dir.listFiles { f -> f.name.endsWith(".wav") }.orEmpty().sortedByDescending { it.name }
        var total = 0L
        sessions.forEachIndexed { index, wav ->
            total += wav.length()
            if (index >= MAX_SESSIONS || total > MAX_TOTAL_BYTES) {
                wav.delete()
                File(dir, wav.name.removeSuffix(".wav") + ".json").delete()
            }
        }
    }
}
