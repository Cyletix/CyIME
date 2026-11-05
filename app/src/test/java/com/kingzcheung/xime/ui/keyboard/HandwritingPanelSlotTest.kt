package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 11-05：全屏手写时表情、编辑(方向)等面板回到平时键盘大小，不铺满整个屏幕。 */
class HandwritingPanelSlotTest {
    @Test fun notFullscreenKeepsThePanelFillingTheKeyboard() {
        assertNull(handwritingPanelSlot(false, 280, 400, 0, 400, 280))
        assertNull(handwritingPanelSlot(true, 0, 400, 0, 400, 800))
    }

    @Test fun fullscreenPanelTakesTheNormalKeyboardSizeAtTheBottom() {
        // 手机竖屏：平时 280dp 高、整宽；全屏手写 800dp 高。面板只要 280dp，上面 520dp 仍是手写区。
        assertEquals(HandwritingPanelSlot(400, 280, 0), handwritingPanelSlot(true, 280, 400, 0, 400, 800))
    }

    @Test fun narrowFixedKeyboardKeepsItsWidthAndSidePosition() {
        // 平板：平时 700dp 宽、往左挪 100dp；全屏宽 1200dp。
        assertEquals(HandwritingPanelSlot(700, 320, -100), handwritingPanelSlot(true, 320, 700, -100, 1200, 900))
        // 挪得超出屏幕时夹回边上。
        assertEquals(HandwritingPanelSlot(700, 320, -250), handwritingPanelSlot(true, 320, 700, -900, 1200, 900))
    }

    @Test fun neverTallerOrWiderThanTheHost() {
        // 展开动画刚开始，宿主还没长高：面板不超过宿主。
        assertEquals(HandwritingPanelSlot(400, 260, 0), handwritingPanelSlot(true, 280, 500, 30, 400, 260))
    }
}
