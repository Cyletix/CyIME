package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * 候选栏里每个候选在根布局里的位置，供 Shift 拖动用：从 Shift 拖到某个候选上松手，等于按住 Shift
 * 点这个候选（释义模式下有英文就上屏英文）。只在主线程读写。
 */
internal class CandidateDropTargets {
    private val targets = LinkedHashMap<Any, Pair<Rect, () -> Unit>>()

    fun put(id: Any, bounds: Rect, select: () -> Unit) {
        if (bounds.isEmpty) targets.remove(id) else targets[id] = bounds to select
    }

    fun remove(id: Any) {
        targets.remove(id)
    }

    /** 落点所在的候选；返回它的选择动作。 */
    fun at(point: Offset): Pair<Rect, () -> Unit>? = targets.values.firstOrNull { it.first.contains(point) }

    companion object {
        /** 键盘界面只有一份候选栏，Shift 键和候选栏不在同一个组件树分支里，用同一个实例对接。 */
        val shared = CandidateDropTargets()
    }
}
