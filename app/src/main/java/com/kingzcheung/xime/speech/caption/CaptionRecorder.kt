package com.kingzcheung.xime.speech.caption

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Caption-mode microphone: VOICE_RECOGNITION, 16 kHz mono PCM16 in 100 ms chunks, the same
 * format the :asr session expects. Independent of the keyboard's capture; the caption page is
 * only open while the keyboard is hidden, and the service refuses a second session anyway.
 */
internal class CaptionRecorder(
    private val onChunk: (ByteArray) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onChunkRead: (Float) -> Unit = {},
    /** Three spectra per 100 ms chunk, ~33 ms apart, for the sound ribbon. */
    private val onSpectrum: (List<FloatArray>) -> Unit = {},
) {
    private val spectrum = CaptionSpectrum()
    private val room = RoomFloor()
    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    // start() runs on the caption page's worker, stop() on the main thread.
    @SuppressLint("MissingPermission")
    @Synchronized fun start(): Boolean {
        if (running.get()) return true
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = maxOf(minimum, SAMPLE_RATE * 2 * 2) // two seconds
        val audio = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer)
        } catch (e: Exception) { onFailure("无法打开麦克风：${e.message}"); return false }
        if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); onFailure("麦克风初始化失败"); return false }
        record = audio
        running.set(true)
        try { audio.startRecording() } catch (e: Exception) { stop(); onFailure("无法开始录音：${e.message}"); return false }
        thread = Thread({
            val chunk = ByteArray(CHUNK_BYTES)
            while (running.get()) {
                val read = audio.read(chunk, 0, chunk.size)
                if (read < 0) { if (running.get()) onFailure("麦克风被系统收回"); break }
                if (read == 0) continue
                var peak = 0
                var i = 0
                while (i + 1 < read) {
                    val sample = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 255)).toShort().toInt()
                    if (abs(sample) > peak) peak = abs(sample)
                    i += 2
                }
                onLevel(peak / 32768f)
                onChunkRead(peak / 32768f)
                val samples = read / 2
                if (samples >= CaptionSpectrum.SIZE) {
                    val hop = (samples - CaptionSpectrum.SIZE) / 2
                    onSpectrum(List(3) { room.above(spectrum.bands(chunk, it * hop)) })
                }
                try { onChunk(if (read == chunk.size) chunk.copyOf() else chunk.copyOf(read)) }
                catch (e: Exception) { if (running.get()) onFailure(e.message ?: "字幕会话中断"); break }
            }
        }, "CyIME-CaptionMic").apply { isDaemon = true; start() }
        FileLogger.i(TAG, "recording started")
        return true
    }

    @Synchronized fun stop() {
        running.set(false)
        val audio = record ?: return
        record = null
        try { audio.stop() } catch (_: Exception) { }
        try { audio.release() } catch (_: Exception) { }
        thread?.let { if (it !== Thread.currentThread()) it.join(500) }
        thread = null
        FileLogger.i(TAG, "recording stopped")
    }

    val isRunning: Boolean get() = running.get()

    /** Audio session of the live recording (0 when stopped): matches it in AudioManager's recording list. */
    val sessionId: Int get() = record?.audioSessionId ?: 0

    companion object {
        private const val TAG = "CaptionRecorder"
        const val SAMPLE_RATE = 16000
        const val CHUNK_BYTES = SAMPLE_RATE / 10 * 2 // 100 ms
    }
}
