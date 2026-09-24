package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Actual laid-out letter cells, including custom and split QWERTY rows. */
internal class ShiftSlideTargets {
    data class Target(val key: String, val bounds: Rect, val commit: () -> Unit)
    private val targets = mutableMapOf<Any, Target>()
    var hovered by mutableStateOf<String?>(null)
    fun put(id: Any, key: String, bounds: Rect, commit: () -> Unit) { targets[id] = Target(key, bounds, commit) }
    fun remove(id: Any) { targets.remove(id) }
    fun at(point: Offset): Target? = targets.values.firstOrNull { it.bounds.contains(point) }
}
internal val LocalShiftSlideTargets = staticCompositionLocalOf<ShiftSlideTargets?> { null }
