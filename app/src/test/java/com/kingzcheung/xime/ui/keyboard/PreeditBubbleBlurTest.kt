package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 编码清空时气泡背后的模糊和拼音同一帧消失，不再晚一拍（用户 11-08）。 */
class PreeditBubbleBlurTest {
    @Test fun blurTurnsFullyTransparentTheFrameTheBubbleHides() {
        assertEquals(0f, preeditBubbleBlurAlpha(hidden = true), 0f)
    }

    @Test fun visibleBubbleKeepsFullBlur() {
        assertEquals(1f, preeditBubbleBlurAlpha(hidden = false), 0f)
    }
}
