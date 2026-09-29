package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.kingzcheung.xime.keyboard.LetterKeyRect
import com.kingzcheung.xime.keyboard.encodeLetterNeighbors

/** Main-thread layout collector. Only immutable encoded maps cross into the input queue. */
class LetterGeometry(private val publish: (String) -> Unit) {
    private val keys = mutableMapOf<Char, LetterKeyRect>()
    fun place(text: String, rect: Rect) {
        val key = text.lowercase().singleOrNull()?.takeIf { it in 'a'..'z' } ?: return
        val measured = LetterKeyRect(rect.left, rect.top, rect.width, rect.height)
        if (keys.put(key, measured) != measured) publish(encodeLetterNeighbors(keys))
    }
}

val LocalLetterGeometry = staticCompositionLocalOf<LetterGeometry?> { null }
