package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Actual laid-out letter cells, including custom and split QWERTY rows. */
internal class ShiftSlideTargets {
    data class Target(val key: String, val bounds: Rect, val commit: () -> Unit)
    data class Drag(val origin: Offset, val pointer: Offset)
    private val targets = mutableMapOf<Any, List<Target>>()
    var keyboardBounds: Rect = Rect.Zero
    var hovered by mutableStateOf<Target?>(null)
        private set
    var drag by mutableStateOf<Drag?>(null)
        private set

    /** 合并字母键按左右半区选单个字母，绝不调用它的长按或符号手势。 */
    fun put(id: Any, letters: String, bounds: Rect, commit: (String) -> Unit) {
        if (letters.length !in 1..2 || letters.any { it.lowercaseChar() !in 'a'..'z' }) {
            remove(id)
            return
        }
        targets[id] = letters.mapIndexed { index, char ->
            val letter = char.uppercaseChar().toString()
            val cell = Rect(bounds.left + bounds.width * index / letters.length, bounds.top,
                bounds.left + bounds.width * (index + 1) / letters.length, bounds.bottom)
            Target(letter, cell) { commit(letter) }
        }
    }
    fun remove(id: Any) { targets.remove(id) }
    fun at(point: Offset): Target? = if (!keyboardBounds.contains(point)) null
        else targets.values.asSequence().flatten().firstOrNull { it.bounds.contains(point) }

    fun move(shiftBounds: Rect, pointer: Offset): Target? {
        if (shiftBounds.contains(pointer)) {
            clearDrag()
            return null
        }
        drag = Drag(shiftBounds.center, pointer)
        val target = at(pointer)
        // Pointer motion redraws the line; keys recompose only when the target changes.
        if (hovered?.key != target?.key || hovered?.bounds != target?.bounds) hovered = target
        return target
    }
    fun clearDrag() {
        drag = null
        hovered = null
    }
}
internal val LocalShiftSlideTargets = staticCompositionLocalOf<ShiftSlideTargets?> { null }
