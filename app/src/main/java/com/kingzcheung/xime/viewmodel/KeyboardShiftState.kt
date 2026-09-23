package com.kingzcheung.xime.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShiftMode {
    OFF, SINGLE, CAPS;

    val isShifted: Boolean get() = this != OFF

    /** 快速连按时键面可能尚未重组，提交字母以当前状态为准；动作名不参与转换。 */
    fun applyToKey(key: String): String =
        if (key.length == 1 && (key[0] in 'a'..'z' || key[0] in 'A'..'Z')) {
            if (isShifted) key.uppercase() else key.lowercase()
        } else key
}

/** 图标、键帽与输入共用一个状态，避免大小写开关和锁定状态脱节。 */
internal class KeyboardShiftState {
    private val _mode = MutableStateFlow(ShiftMode.OFF)
    val mode = _mode.asStateFlow()

    fun singleTap() {
        _mode.value = if (_mode.value == ShiftMode.OFF) ShiftMode.SINGLE else ShiftMode.OFF
    }

    fun doubleTap() {
        _mode.value = if (_mode.value == ShiftMode.CAPS) ShiftMode.OFF else ShiftMode.CAPS
    }

    fun setShifted(shifted: Boolean) {
        if (!shifted) reset()
        else if (_mode.value == ShiftMode.OFF) _mode.value = ShiftMode.SINGLE
    }

    fun onCharacterTyped() {
        if (_mode.value == ShiftMode.SINGLE) reset()
    }

    fun reset() {
        _mode.value = ShiftMode.OFF
    }
}
