package com.kingzcheung.xime.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val PERMISSION_RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
    const val REQUEST_CODE_RECORD_AUDIO = 1001
    
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /** 语音入口申请的授权：允许后键盘回来时接着开语音，不用再点一次麦克风。 */
    @Volatile private var voiceRequested = false
    @Volatile private var voiceGrantedAt = 0L
    private const val VOICE_AFTER_GRANT_MS = 10_000L

    /** 授权页的结果；只有语音入口申请、且允许了，才记下来。 */
    fun onRecordAudioResult(granted: Boolean) {
        voiceGrantedAt = if (granted && voiceRequested) android.os.SystemClock.uptimeMillis() else 0L
        voiceRequested = false
    }

    /** 键盘重新出现时取一次：刚刚（10 秒内）为语音授了权就返回 true，只返回一次。 */
    fun takeVoiceAfterGrant(context: Context): Boolean {
        val at = voiceGrantedAt
        if (at == 0L) return false
        voiceGrantedAt = 0L
        return android.os.SystemClock.uptimeMillis() - at < VOICE_AFTER_GRANT_MS && hasRecordAudioPermission(context)
    }

    fun requestRecordAudioPermission(context: Context) {
        voiceRequested = true
        voiceGrantedAt = 0L
        val intent = Intent(context, com.kingzcheung.xime.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("request_permission", PERMISSION_RECORD_AUDIO)
        context.startActivity(intent)
    }
}