package com.kingzcheung.xime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * 开车连续语音（用户 2026-10-31 设计，设置里默认关）：语音听写时点收起变成悬浮条，
 * 之后发出消息（输入框被清空）或离开输入框，听写照常停下、释放麦克风；
 * 回到同一个应用的输入框、键盘弹出来时，直接是悬浮条并自动接着听。
 *
 * 安全边界：
 * - 只在输入法窗口显示、并且有输入框焦点时开麦；没有焦点时一定不录。
 * - 只续用户自己收起的悬浮条；App 收起键盘自动出的悬浮条、手动点麦克风停下、展开键盘、返回键都不续。
 * - 只续同一个应用，[VOICE_PILL_RESUME_WINDOW_MS] 内有效；熄屏、密码框、通话中都不续。
 * - 没有麦克风权限时不自动弹授权页。
 */
internal class VoicePillResume(
    private val context: Context,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private var mark: VoicePillResumeMark? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    val armed: Boolean get() = mark != null

    /** 记下：这个应用里的悬浮条听写被发送或离开焦点打断了。 */
    fun arm(packageName: String?) {
        if (packageName.isNullOrEmpty()) { disarm(); return }
        mark = VoicePillResumeMark(packageName, nowMs())
        if (screenOffReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { disarm() }
            }
            screenOffReceiver = receiver
            runCatching {
                ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED)
            }.onFailure { screenOffReceiver = null }
        }
    }

    /** 用户自己停下（点麦克风、展开键盘、返回键、熄屏、关设置）：不再自动续听。 */
    fun disarm() {
        mark = null
        screenOffReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        screenOffReceiver = null
    }

    fun appliesTo(packageName: String?): Boolean = voicePillResumeApplies(mark, packageName, nowMs())
}
