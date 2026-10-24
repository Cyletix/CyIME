package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Actual laid-out letter cells, including custom and split QWERTY rows. */
internal class ShiftSlideTargets(private val candidates: CandidateDropTargets = CandidateDropTargets.shared) {
    data class Target(val key: String, val bounds: Rect, val commit: () -> Unit)
    data class Drag(val origin: Offset, val pointer: Offset)
    private val targets = mutableMapOf<Any, List<Target>>()
    var keyboardBounds: Rect = Rect.Zero
    var hovered by mutableStateOf<Target?>(null)
        private set
    var drag by mutableStateOf<Drag?>(null)
        private set
    /** 这次按住 Shift 已经拖出过 Shift 键：14 键据此把双字母键拆成单字母键，直到松开 Shift（用户 10-21）。 */
    var dragging by mutableStateOf(false)
        private set
    /** 按住符号键当 Ctrl：字母键改显示快捷键，拖到字母上松手发 Ctrl+字母，不选候选（用户 10-24）。 */
    var ctrl by mutableStateOf(false)
        private set
    private var ctrlShortcut: ((Char) -> Unit)? = null
    /** 这次按住符号键已经用另一根手指点过字母：松手不再当点按打开符号。 */
    var ctrlUsed = false
        private set
    fun beginCtrl(shortcut: (Char) -> Unit) {
        ctrl = true
        ctrlShortcut = shortcut
        ctrlUsed = false
    }
    /** 按住符号键时另一根手指点字母：和按住 Shift 再点一样，发 Ctrl+这个字母（用户 10-24）。 */
    fun ctrlTap(letters: String): Boolean {
        val letter = letters.singleOrNull()?.takeIf { ctrl && it.lowercaseChar() in 'a'..'z' } ?: return false
        val send = ctrlShortcut ?: return false
        ctrlUsed = true
        send(letter)
        return true
    }

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
    fun at(point: Offset): Target? = if (!keyboardBounds.contains(point)) candidateAt(point)?.takeUnless { ctrl }
        else targets.values.asSequence().flatten().firstOrNull { it.bounds.contains(point) }

    /** 拖出键盘、落在候选上：松手等于按住 Shift 点这个候选（释义模式上屏英文）。 */
    private fun candidateAt(point: Offset): Target? =
        candidates.at(point)?.let { (bounds, select) -> Target(CANDIDATE_KEY, bounds, select) }

    companion object {
        /** 候选目标的 key；不是字母，键帽高亮不会匹配到它。 */
        const val CANDIDATE_KEY = "candidate"
    }

    fun move(shiftBounds: Rect, pointer: Offset): Target? {
        if (shiftBounds.contains(pointer)) {
            clearDrag()
            return null
        }
        drag = Drag(shiftBounds.center, pointer)
        dragging = true
        val target = at(pointer)
        // Pointer motion redraws the line; keys recompose only when the target changes.
        if (hovered?.key != target?.key || hovered?.bounds != target?.bounds) hovered = target
        if (!ctrl) {
            candidates.dragLine = drag?.let { it.origin to it.pointer }
            candidates.hoveredBounds = target?.takeIf { it.key == CANDIDATE_KEY }?.bounds
        }
        return target
    }
    /** 松开 Shift：清掉拖线，键盘回到原来的布局。 */
    fun finish() {
        clearDrag()
        dragging = false
        ctrl = false
        ctrlShortcut = null
    }
    fun clearDrag() {
        drag = null
        hovered = null
        candidates.dragLine = null
        candidates.hoveredBounds = null
    }
}
internal val LocalShiftSlideTargets = staticCompositionLocalOf<ShiftSlideTargets?> { null }
