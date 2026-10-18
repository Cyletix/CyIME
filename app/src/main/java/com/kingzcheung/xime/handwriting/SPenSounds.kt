package com.kingzcheung.xime.handwriting

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.provider.Settings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 三星平板/手机自带的 S Pen 书写与橡皮声：系统里 /system/media/audio/pensounds 下的循环音源
 * （44.1 kHz、16 位、双声道原始 PCM）。只在本机读取播放，不随安装包分发；
 * 跟随系统「S Pen 声音」开关（spen_feedback_sound），没有这些文件的设备不发声。
 * 音量随笔速变化：停住就静音，抬笔即停。
 */
class SPenSounds(private val context: Context) {
    private var write: AudioTrack? = null
    private var erase: AudioTrack? = null
    private var playing: AudioTrack? = null
    private var volume = 0f

    /** 设备上有三星笔声文件；在后台线程调用 [load] 之后才会真正发声。 */
    val available: Boolean get() = File(DIR, WRITE_FILE).canRead() && File(DIR, ERASE_FILE).canRead()

    fun load() {
        if (write != null || !available) return
        write = track(File(DIR, WRITE_FILE), WRITE_GAIN)
        erase = track(File(DIR, ERASE_FILE), ERASE_GAIN)
    }

    private fun enabled(): Boolean = try {
        Settings.System.getInt(context.contentResolver, "spen_feedback_sound", 0) == 1
    } catch (_: Exception) { false }

    val active: Boolean get() = write != null && enabled()

    /** 笔每一帧移动时调用；速度单位 px/ms，[fullSpeed] 为满音量的速度。 */
    fun onMotion(erasing: Boolean, speed: Float, fullSpeed: Float) {
        val track = (if (erasing) erase else write) ?: return
        if (!enabled()) return stop()
        if (playing !== track) {
            stop()
            playing = track
            runCatching { track.play() }
        }
        // 平滑，避免逐帧速度抖动造成颤音。
        volume = volume * .6f + targetVolume(speed, fullSpeed) * .4f
        runCatching { track.setVolume(volume) }
    }

    fun stop() {
        playing?.let { runCatching { it.setVolume(0f); it.pause() } }
        playing = null
        volume = 0f
    }

    fun release() {
        stop()
        write?.release(); erase?.release()
        write = null; erase = null
    }

    private fun track(file: File, gain: Float): AudioTrack? = runCatching {
        val bytes = file.readBytes()
        val samples = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        for (i in samples.indices) samples[i] = (samples[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
        val frames = samples.size / 2
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44_100)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 4)
            .build().apply {
                write(samples, 0, frames * 2)
                setLoopPoints(0, frames, -1)
                setVolume(0f)
            }
    }.getOrNull()

    companion object {
        private const val DIR = "/system/media/audio/pensounds"
        private const val WRITE_FILE = "smpsdata1.dat"
        private const val ERASE_FILE = "smpsdatae1.dat"
        // 原始音源很轻（书写峰值约 -38 dBFS），放大到与按键音相近。
        private const val WRITE_GAIN = 12f
        private const val ERASE_GAIN = 3f

        internal fun targetVolume(speed: Float, fullSpeed: Float): Float =
            if (speed <= 0f || fullSpeed <= 0f) 0f else (speed / fullSpeed).coerceIn(0f, 1f)
    }
}
