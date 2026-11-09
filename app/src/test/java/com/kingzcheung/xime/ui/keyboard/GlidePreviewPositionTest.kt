package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户 11-09：滑行实时显示的词在键盘中间被手指挡住；改成固定在键盘上方正中。 */
class GlidePreviewPositionTest {
    private val window = IntSize(1080, 2400)
    private val keyboard = IntRect(0, 1500, 1080, 2300)
    private val popup = IntSize(200, 80)

    @Test fun sitsAboveTheKeyboardCentred() {
        val at = glidePreviewPosition(keyboard, window, popup, gap = 16)
        assertEquals(IntOffset(440, 1500 - 16 - 80), at)
        // 整个在键盘顶边以上：手指在任何键上都挡不到。
        assertTrue(at.y + popup.height <= keyboard.top)
    }

    @Test fun floatingKeyboardKeepsItsOwnCentreAndStaysOnScreen() {
        assertEquals(IntOffset(150 + (500 - 200) / 2, 900 - 16 - 80),
            glidePreviewPosition(IntRect(150, 900, 650, 1300), window, popup, gap = 16))
        // 贴着屏幕顶、左边时不出屏幕。
        assertEquals(IntOffset(0, 0), glidePreviewPosition(IntRect(-300, 20, 100, 400), window, popup, gap = 16))
    }
}
