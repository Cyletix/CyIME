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

    @Test fun loweredResolutionDoesNotHideLetterHints() {
        // #172 荣耀 V20：面板 1080 宽、约 403dpi，「智能分辨率」调到 720p 后 density 2.0，xdpi 仍报 403。
        val raw = 403f / 25.4f / 2.0f
        // 字母键帽短边约 30dp：直接按 xdpi 算只有 3.8mm，角标被藏
        assertFalse(showKeyHints(30f, 48f, raw))
        // 按 720/1080 折回后约 5.7mm，角标照常显示
        assertTrue(showKeyHints(30f, 48f, raw * resolutionOverrideScale(720, 1080)))
        // 分辨率没调低、或只差一点（取整误差）时不改
        assertEquals(1f, resolutionOverrideScale(1080, 1080), 0f)
        assertEquals(1f, resolutionOverrideScale(1070, 1080), 0f)
        assertEquals(1f, resolutionOverrideScale(0, 1080), 0f)
    }

    @Test fun hintTextIsSmallerAndGrowsLessOnLargeScreens() {
        assertEquals(9f, swipeHintFontSp(10f, 1f), 0.001f)
        // 平板最大约 13sp（旧版到 17sp）。
        assertEquals(13.05f, swipeHintFontSp(10f, 3f), 0.01f)
    }
}
