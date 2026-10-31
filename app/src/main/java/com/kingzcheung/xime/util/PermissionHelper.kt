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

    /** 系统不再弹授权框、改送到应用设置页的时刻；从设置页打开麦克风回来也接着开语音。 */
    @Volatile private var voiceSettingsAt = 0L
    private const val VOICE_AFTER_SETTINGS_MS = 120_000L

    /** 授权框没弹出来（拒绝过且不再询问）：送去应用设置页之前调用。 */
    fun onSentToAppSettings() {
        voiceSettingsAt = if (voiceRequested) android.os.SystemClock.uptimeMillis() else 0L
        voiceRequested = false
    }

    /** 键盘重新出现时取一次：刚刚（10 秒内，从设置页回来 2 分钟内）为语音授了权就返回 true，只返回一次。 */
    fun takeVoiceAfterGrant(context: Context): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        val granted = voiceGrantedAt.let { it != 0L && now - it < VOICE_AFTER_GRANT_MS }
        val fromSettings = voiceSettingsAt.let { it != 0L && now - it < VOICE_AFTER_SETTINGS_MS }
        voiceGrantedAt = 0L
        voiceSettingsAt = 0L
        return (granted || fromSettings) && hasRecordAudioPermission(context)
    }

    fun requestRecordAudioPermission(context: Context) {
        voiceRequested = true
        voiceGrantedAt = 0L
        voiceSettingsAt = 0L
        // Its own task every time: with a plain NEW_TASK an existing CyIME task whose root intent is the same
        // MainActivity (extras do not count) is only brought forward, onCreate never runs and no dialog appears.
        // Its own task also returns to the app being typed in, not to the settings pages, when it finishes.
        val intent = Intent(context, com.kingzcheung.xime.MainActivity::class.java).setAction(ACTION_REQUEST_RECORD_AUDIO)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        intent.putExtra("request_permission", PERMISSION_RECORD_AUDIO)
        context.startActivity(intent)
    }
    private const val ACTION_REQUEST_RECORD_AUDIO = "com.kingzcheung.xime.action.REQUEST_RECORD_AUDIO"
}