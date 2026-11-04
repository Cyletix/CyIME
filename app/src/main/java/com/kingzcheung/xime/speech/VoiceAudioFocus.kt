package com.kingzcheung.xime.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.kingzcheung.xime.settings.SettingsPreferences

/** 屏蔽外放音的三种做法（用户 11-04 截图的选项和叫法）；默认不屏蔽，和以前一样（用户「默认不变」）。 */
enum class VoiceOtherAudio(val id: String, val label: String) {
    DUCK("duck", "压低音量"),
    PAUSE("pause", "暂停播放"),
    OFF("off", "不屏蔽");

    companion object {
        val DEFAULT = OFF
        fun fromId(id: String?): VoiceOtherAudio = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 屏蔽外放音（用户 2026-11-04 要的）：语音输入开麦时把正在放的音乐、视频压低或暂停，关麦就恢复。
 *
 * 先申请临时音频焦点：压低音量用可压低的焦点（系统自动把别的播放器压小），暂停播放用独占焦点（播放器自己暂停、
 * 录完自己接着放）。Android 15 起不在前台的应用申请焦点会被系统拒绝，输入法可能也算在内；这时退回为自己动媒体音量：
 * 压低就临时调到原来的五分之一，暂停就临时静音，关麦时只还原自己动过的那次。动音量之前先落盘记一笔，
 * 进程中途被杀没来得及还原，下次创建时补上，不会让媒体一直很小声或没声。
 *
 * 只在开麦那一刻有媒体在放时动作；同一时刻可能有新旧两段录音交接，按持有者计数，最后一个放手才恢复。
 */
internal class VoiceAudioFocus(private val context: Context) {
    companion object {
        private const val TAG = "VoiceAudioFocus"
        /** 退回手动压低时调到原音量的几分之一。 */
        private const val DUCK_DIVISOR = 5
    }

    private val lock = Any()
    private val owners = HashSet<Any>()
    private var focusRequest: AudioFocusRequest? = null
    private var mutedMusic = false
    /** 手动压低前的媒体音量和压到的音量；-1 表示没压。 */
    private var volumeBefore = -1
    private var volumeLowered = -1

    private val audio: AudioManager? by lazy {
        try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (_: Exception) { null }
    }

    init {
        // 上次动了媒体音量后进程没走到恢复：先把声音还回去。
        try {
            if (SettingsPreferences.isSttMusicMutedByVoice(context)) {
                unmuteMusic()
                SettingsPreferences.setSttMusicMutedByVoice(context, false)
            }
            val before = SettingsPreferences.getSttMusicVolumeBeforeVoice(context)
            if (before >= 0) {
                audio?.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
                SettingsPreferences.setSttMusicVolumeBeforeVoice(context, -1)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Restoring media volume failed", error)
        }
    }

    fun acquire(owner: Any) { synchronized(lock) {
        if (!owners.add(owner) || owners.size > 1) return
        try {
            val mode = SettingsPreferences.getSttOtherAudio(context)
            if (mode == VoiceOtherAudio.OFF) return
            val manager = audio ?: return
            if (!manager.isMusicActive) return
            val gain = if (mode == VoiceOtherAudio.DUCK) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            val request = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
                return
            }
            // 系统不给焦点：自己动媒体音量（不弹音量条），关麦时还原。
            if (manager.isVolumeFixed || manager.isStreamMute(AudioManager.STREAM_MUSIC)) return
            if (mode == VoiceOtherAudio.DUCK) {
                val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val lowered = current / DUCK_DIVISOR
                if (current <= 0 || lowered >= current) return
                SettingsPreferences.setSttMusicVolumeBeforeVoice(context, current)
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, lowered, 0)
                volumeBefore = current
                volumeLowered = lowered
                Log.i(TAG, "Audio focus refused; media lowered while recording")
            } else {
                SettingsPreferences.setSttMusicMutedByVoice(context, true)
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                mutedMusic = true
                Log.i(TAG, "Audio focus refused; media muted while recording")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Quieting other audio failed", error)
        }
    } }

    fun release(owner: Any) { synchronized(lock) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        restore()
    } }

    private fun restore() {
        val request = focusRequest
        focusRequest = null
        if (request != null) try { audio?.abandonAudioFocusRequest(request) } catch (error: Exception) {
            Log.w(TAG, "Abandoning audio focus failed", error)
        }
        if (mutedMusic) {
            mutedMusic = false
            try {
                unmuteMusic()
                SettingsPreferences.setSttMusicMutedByVoice(context, false)
            } catch (error: Exception) {
                Log.w(TAG, "Restoring media volume failed", error)
            }
        }
        if (volumeBefore >= 0) {
            val before = volumeBefore
            val lowered = volumeLowered
            volumeBefore = -1
            volumeLowered = -1
            try {
                // 说话时用户自己调过音量就听用户的，不再改回去。
                val manager = audio
                if (manager != null && manager.getStreamVolume(AudioManager.STREAM_MUSIC) == lowered) {
                    manager.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
                }
                SettingsPreferences.setSttMusicVolumeBeforeVoice(context, -1)
            } catch (error: Exception) {
                Log.w(TAG, "Restoring media volume failed", error)
            }
        }
    }

    private fun unmuteMusic() {
        audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    }
}
