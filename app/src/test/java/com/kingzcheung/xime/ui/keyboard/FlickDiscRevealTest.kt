package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 九键拂动的方向圆盘只在拂出去或按住后出现（用户 11-03：只点不拂时轮盘让打字发卡）。 */
class FlickDiscRevealTest {
    @Test fun onlyAMoveOffThePressPointRevealsTheDisc() {
        // 刚按下（手势发出的第一帧）：还没动。
        assertFalse(SwipeState(isPressed = true, pressedText = "ABC").movedOffPress())
        // 手指离开按下点：手势把 isPressed 置为 false，仍带着本键文字。
        assertTrue(SwipeState(isPressed = false, pressedText = "ABC").movedOffPress())
        assertTrue(SwipeState(isPressed = false, pressedText = "ABC",
            keyFlick = KeyFlickPreview("A", 0.3f, KeyFlickOrigin.LEFT)).movedOffPress())
        // 长按菜单和抬手后的空状态都不算拂动。
        assertFalse(SwipeState(isPressed = true, isLongPress = true, longPressItems = listOf("2")).movedOffPress())
        assertFalse(SwipeState().movedOffPress())
    }

    @Test fun holdDelayEndsBeforeTheLongPress() {
        // 按住的人在长按出数字（300 ms）之前就能看到圆盘；快速点按（一般不到 150 ms）看不到。
        assertTrue(FLICK_DISC_HOLD_MS in 150L until 300L)
    }

    @Test fun withTheDiscOnlyCrossingTheThresholdIsKept() {
        val near = KeyFlickPreview("A", 0.4f, KeyFlickOrigin.LEFT)
        val nearer = KeyFlickPreview("A", 0.7f, KeyFlickOrigin.LEFT)
        // 圆盘开着：未过阈值的两帧相等，手指移动不引起重组；过了阈值才变。
        assertEquals(discFlickPreview(near, disc = true), discFlickPreview(nearer, disc = true))
        assertEquals(1f, discFlickPreview(KeyFlickPreview("A", 1f, KeyFlickOrigin.LEFT), disc = true)!!.progress, 0f)
        assertEquals("A", discFlickPreview(near, disc = true)!!.text)
        assertEquals(KeyFlickOrigin.LEFT, discFlickPreview(near, disc = true)!!.origin)
        // 圆盘关着：键内渐变要跟手，进度原样保留。
        assertSame(near, discFlickPreview(near, disc = false))
        assertNull(discFlickPreview(null, disc = true))
    }
}
