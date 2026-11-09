package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 换了语言后，空格先写出语言名，停一会儿再淡成空格图标（像 iOS）。
 * 给「功能键不显示提示字」开着、26 键中文和英文看起来一样时认出当前语言（用户 11-09）。
 *
 * 只记本进程里上一次的语言名：第一次弹出键盘、同一语言重开键盘、去数字/符号键盘再回来都不闪；
 * 整个键盘换了布局（空格键重建）也按语言名比，不会误闪。
 */
internal object SpaceLanguageFlash {
    const val HOLD_MS = 900L
    const val FADE_MS = 600L

    data class Flash(val name: String, val startMs: Long)

    private var lastName: String? = null

    /** 当前这次闪示；各块空格（包括刚重建的）按它和自己的语言名决定画不画。 */
    var current by mutableStateOf<Flash?>(null)
        private set

    /** 数字/符号键盘的「空格」和分体右半边的空名不算语言。 */
    fun isLanguageName(name: String): Boolean = name.isNotBlank() && name != "空格"

    /** 空格键拿到语言名时调。和上一次不同才闪。 */
    fun observe(name: String, nowMs: Long) {
        if (!isLanguageName(name)) return
        val previous = lastName
        lastName = name
        if (previous != null && previous != name) current = Flash(name, nowMs)
    }

    /** 这块空格此刻语言名该画多实：1 全实，0 不画。 */
    fun alpha(flash: Flash?, name: String, nowMs: Long): Float {
        if (flash == null || flash.name != name) return 0f
        val elapsed = (nowMs - flash.startMs).coerceAtLeast(0L)
        return when {
            elapsed < HOLD_MS -> 1f
            elapsed < HOLD_MS + FADE_MS -> 1f - (elapsed - HOLD_MS).toFloat() / FADE_MS
            else -> 0f
        }
    }

    internal fun resetForTest() {
        lastName = null
        current = null
    }
}
