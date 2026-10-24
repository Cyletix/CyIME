package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorLineCharsTest {
    @Test fun `chinese text fills a line with one character per text size`() {
        assertEquals(20f, estimateCharsPerLine(960f, 48f, "今天下午三点开会")!!, 0.01f)
    }

    @Test fun `latin text fits more characters on the same line`() {
        assertEquals(960f / (48f * 0.55f), estimateCharsPerLine(960f, 48f, "meeting")!!, 0.01f)
    }

    @Test fun `empty field assumes chinese width and ignores line breaks`() {
        assertEquals(20f, estimateCharsPerLine(960f, 48f, "")!!, 0.01f)
        assertEquals(20f, estimateCharsPerLine(960f, 48f, "你\n好")!!, 0.01f)
    }

    @Test fun `missing or degenerate layout gives no estimate`() {
        assertNull(estimateCharsPerLine(0f, 48f, "你好"))
        assertNull(estimateCharsPerLine(960f, Float.NaN, "你好"))
        assertNull(estimateCharsPerLine(60f, 48f, "你好"))
    }
}
