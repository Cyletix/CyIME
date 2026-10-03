package com.kingzcheung.xime.clipboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class ClipboardNavigation { LEFT, RIGHT, UP, DOWN, CONFIRM, CANCEL }

/** IME windows do not take the editor's focus. Route physical keys to the visible board. */
internal class ClipboardKeyboardNavigation(private val onClose: () -> Unit) {
    var active by mutableStateOf(false)
        private set
    private val pending = ArrayDeque<ClipboardNavigation>()
    private var handler: ((ClipboardNavigation) -> Unit)? = null
    fun open() { pending.clear(); active = true }
    fun reset() { active = false; pending.clear(); handler = null }
    fun close() { reset(); onClose() }
    fun dispatch(action: ClipboardNavigation) {
        if (!active) return
        if (action == ClipboardNavigation.CANCEL) { close(); return }
        val receiver = handler
        if (receiver == null) { if (pending.size < 32) pending.addLast(action) } else receiver(action)
    }
    fun attach(receiver: ((ClipboardNavigation) -> Unit)?) {
        handler = receiver
        while (active && handler != null && pending.isNotEmpty()) handler?.invoke(pending.removeFirst())
    }
}

internal val LocalClipboardKeyboardNavigation = staticCompositionLocalOf<ClipboardKeyboardNavigation?> { null }

internal data class ClipboardCell(val key: String, val x: Int, val y: Int, val width: Int, val height: Int)

/** Prefer the actual staggered-grid geometry; use the adjacent logical row outside the viewport. */
internal fun nextClipboardKey(keys: List<String>, current: String?, direction: ClipboardNavigation,
    columns: Int, visible: List<ClipboardCell>): String? {
    if (keys.isEmpty()) return null
    val index = keys.indexOf(current).coerceAtLeast(0)
    val source = visible.firstOrNull { it.key == keys[index] }
    if (source != null) {
        val sx = source.x + source.width / 2f; val sy = source.y + source.height / 2f
        val horizontal = direction == ClipboardNavigation.LEFT || direction == ClipboardNavigation.RIGHT
        val positive = direction == ClipboardNavigation.RIGHT || direction == ClipboardNavigation.DOWN
        val target = visible.filter { it.key in keys && it.key != source.key }.mapNotNull { cell ->
            val dx = cell.x + cell.width / 2f - sx; val dy = cell.y + cell.height / 2f - sy
            val primary = if (horizontal) dx else dy
            val cross = if (horizontal) dy else dx
            if ((if (positive) primary else -primary) <= 1f) null
            else cell to (kotlin.math.abs(primary) + kotlin.math.abs(cross) * 2f)
        }.minByOrNull { it.second }?.first
        if (target != null) return target.key
    }
    val delta = when (direction) {
        ClipboardNavigation.LEFT -> -1
        ClipboardNavigation.RIGHT -> 1
        ClipboardNavigation.UP -> -columns.coerceAtLeast(1)
        ClipboardNavigation.DOWN -> columns.coerceAtLeast(1)
        else -> 0
    }
    return keys[(index + delta).coerceIn(0, keys.lastIndex)]
}
