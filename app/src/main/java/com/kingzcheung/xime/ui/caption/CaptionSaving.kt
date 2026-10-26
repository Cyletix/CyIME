package com.kingzcheung.xime.ui.caption

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「自动保存字幕」: each session's transcript kept as one file in 下载/CyIME字幕, updated as it goes. On by default. */
internal object CaptionAutoSave {
    private const val PREFS = "caption_mode"
    private const val KEY = "auto_save"
    fun isOn(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

/** One transcript line: speaker, clock time, words (the format of the saved file and of 复制全部). */
internal fun transcriptLine(speaker: String?, wallMs: Long, text: String): String =
    "${speaker ?: "说话的人"}  ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(wallMs))}  $text"
