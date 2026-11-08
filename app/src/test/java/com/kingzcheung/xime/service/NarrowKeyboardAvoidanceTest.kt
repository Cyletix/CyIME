package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrowKeyboardAvoidanceTest {
    // 横屏 3200 宽，卡片靠右（截图里的位置）。
    private fun beside(editorLeft: Float, editorRight: Float, cardLeft: Int = 1660, cardRight: Int = 2960,
        width: Int = 3200, filled: Boolean = false) =
        narrowKeyboardLeavesEditorUncovered(cardLeft, cardRight, width, filled, editorLeft, editorRight)

    @Test
    fun smallWindowLeftOfCardIsNotPushed() {
        assertTrue(beside(250f, 800f))
        assertTrue(beside(250f, 1660f))
    }

    @Test
    fun editorRightOfCardIsNotPushed() {
        assertTrue(beside(2960f, 3150f))
    }

    @Test
    fun editorUnderTheCardStillAvoids() {
        assertFalse(beside(250f, 1661f))
        assertFalse(beside(1800f, 2400f))
        assertFalse(beside(2900f, 3150f))
    }

    @Test
    fun fullWidthEditorStillAvoids() {
        assertFalse(beside(0f, 3200f))
    }

    @Test
    fun fullWidthKeyboardKeepsTodaysBehaviour() {
        assertFalse(beside(250f, 800f, cardLeft = 0, cardRight = 3200))
    }

    @Test
    fun filledSidesKeepTodaysBehaviour() {
        assertFalse(beside(250f, 800f, filled = true))
    }

    @Test
    fun unusableEditorBoundsKeepTodaysBehaviour() {
        assertFalse(beside(800f, 800f))
        assertFalse(beside(Float.NaN, 800f))
        assertFalse(beside(250f, Float.POSITIVE_INFINITY))
    }
}
