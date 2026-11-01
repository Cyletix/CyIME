package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.kingzcheung.xime.keyboard.LetterKeyRect

/**
 * 九键的数字键布局：只收 2–9，滑行识别按这几个矩形判断起手键、收手键和轨迹。
 *
 * 和 26 键的 [LetterGeometry] 分开，是因为那边会顺带发布「相邻字母」给按键角标用，
 * 把数字混进去会改到 26 键的角标逻辑。
 */
class T9Geometry {
    @Volatile private var keys: Map<Char, LetterKeyRect> = emptyMap()

    fun place(digit: String, rect: Rect) {
        val key = digit.singleOrNull()?.takeIf { it in '2'..'9' } ?: return
        if (rect.width <= 0f || rect.height <= 0f) return
        val measured = LetterKeyRect(rect.left, rect.top, rect.width, rect.height)
        if (keys[key] != measured) keys = keys + (key to measured)
    }

    /** 当前 2–9 各键的根坐标矩形。 */
    fun snapshot(): Map<Char, LetterKeyRect> = keys
}

val LocalT9Geometry = staticCompositionLocalOf<T9Geometry?> { null }
