package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.viewmodel.ShiftMode

/**
 * 空格长按移光标时的 Shift 扩选，同电脑 Shift+方向键：从锚点扩选，松开 Shift 后的下一步折叠选区、回到普通移动。
 * 按住 Shift（HELD）逐步实时判断；单次大写（SINGLE）在本次拖动第一步锁定整段扩选并被用掉；大写锁定（CAPS）不扩选。
 * 只在空格移光标期间生效，键盘区滑动移光标等其他来源不受影响。
 */
internal object SpaceCursorShift {
    private var active = false
    private var latched: Boolean? = null

    fun begin() { active = true; latched = null }

    fun end() { active = false; latched = null }

    /** 返回这一步是否扩选；首次扩选的那一步之后调用方应把 Shift 记为已使用。 */
    fun selecting(mode: ShiftMode): Boolean {
        if (!active) return false
        val oneShot = latched ?: (mode == ShiftMode.SINGLE).also { latched = it }
        return oneShot || mode == ShiftMode.HELD
    }
}
