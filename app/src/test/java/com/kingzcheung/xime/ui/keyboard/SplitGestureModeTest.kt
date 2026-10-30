package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 拆词页：竖着拖是滚动，不选词（用户 10-30）；横着拖连选；滚不动时竖着拖也连选。 */
class SplitGestureModeTest {
    @Test fun verticalDragScrollsAndSelectsNothing() {
        assertEquals(SplitGesture.SCROLL, splitGestureMode(onChip = true, dx = 4f, dy = 40f, canScroll = true))
        assertEquals(SplitGesture.SCROLL, splitGestureMode(onChip = true, dx = 3f, dy = -40f, canScroll = true))
        assertEquals(SplitGesture.SCROLL, splitGestureMode(onChip = true, dx = 20f, dy = 20f, canScroll = true))
    }

    @Test fun sidewaysDragFromAWordSelectsARun() {
        assertEquals(SplitGesture.SELECT, splitGestureMode(onChip = true, dx = 40f, dy = 6f, canScroll = true))
        assertEquals(SplitGesture.SELECT, splitGestureMode(onChip = true, dx = -40f, dy = 30f, canScroll = true))
    }

    @Test fun aListThatCannotScrollStillSelectsUpAndDown() {
        assertEquals(SplitGesture.SELECT, splitGestureMode(onChip = true, dx = 0f, dy = 40f, canScroll = false))
    }

    @Test fun aDragStartedBetweenWordsOnlyScrolls() {
        assertEquals(SplitGesture.SCROLL, splitGestureMode(onChip = false, dx = 40f, dy = 0f, canScroll = true))
    }
}
