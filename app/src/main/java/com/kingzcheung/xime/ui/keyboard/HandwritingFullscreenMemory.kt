package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 手写记住上次是全屏还是半屏（11-05 他的要求「手写加一个记忆全屏半屏状态」）。
 * 只记用户自己点全屏/收起全屏、按返回收起的结果；键盘收起、切走手写、收成工具栏或语音悬浮条
 * 这些临时退出不改记忆。从没点过时是原来的默认半屏。
 */
internal object HandwritingFullscreenMemory {
    const val KEY = "handwriting_fullscreen"

    fun read(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(KEY, false)

    fun write(context: Context, fullscreen: Boolean) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        if (prefs.getBoolean(KEY, false) != fullscreen) prefs.edit().putBoolean(KEY, fullscreen).apply()
    }

    /** 回到手写时要不要直接全屏：只在手写页、键盘完整显示（非工具栏、非语音悬浮条、非调尺寸、非设置页预览）时恢复。 */
    fun shouldRestore(
        remembered: Boolean,
        handwritingPage: Boolean,
        alreadyExpanded: Boolean,
        compact: Boolean,
        voiceCollapsed: Boolean,
        resizing: Boolean,
        settingsPreview: Boolean,
    ): Boolean = remembered && handwritingPage && !alreadyExpanded && !compact && !voiceCollapsed &&
        !resizing && !settingsPreview
}
