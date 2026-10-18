package com.kingzcheung.xime.service

import androidx.compose.runtime.mutableStateOf

/**
 * 屏幕键盘时宿主光标的屏幕位置，只给编码悬浮气泡用：气泡放在光标处，不再固定在键盘左边盖住已上屏的字。
 * 单独放一个状态，光标每动一次只重画气泡，不牵动整块键盘的 uiState。
 */
internal object SoftCaretAnchor {
    val anchor = mutableStateOf<HardwareCursorAnchor?>(null)
}
