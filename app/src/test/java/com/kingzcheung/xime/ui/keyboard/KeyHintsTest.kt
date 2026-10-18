package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHintsTest {
    @Test fun hintsHideOnlyOnPhysicallyTinyKeys() {
        // 6.3 dp/mm ≈ 160dpi 的名义值；普通手机键帽约 32dp，仍显示提示。
        assertTrue(showKeyHints(32f, 48f, 6.3f))
        // 缩小的悬浮键盘，短边约 3.6mm：只留主字符。
        assertFalse(showKeyHints(23f, 40f, 6.3f))
        // 读不到物理尺寸时不隐藏。
        assertTrue(showKeyHints(10f, 10f, null))
    }

    @Test fun hintTextIsSmallerAndGrowsLessOnLargeScreens() {
        assertEquals(9f, swipeHintFontSp(10f, 1f), 0.001f)
        // 平板最大约 13sp（旧版到 17sp）。
        assertEquals(13.05f, swipeHintFontSp(10f, 3f), 0.01f)
    }
}
