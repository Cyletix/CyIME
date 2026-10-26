package com.kingzcheung.xime.ui.caption

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSavingTest {
    @Test fun `a line calling any of the holder's names buzzes, other lines do not`() {
        val names = "老王 王叔，爷爷"
        assertTrue(callsName("老王，过来吃饭了", names))
        assertTrue(callsName("爷爷你看这个", names))
        assertTrue(callsName("王叔在家吗？", names))
        assertFalse(callsName("今天天气不错", names))
        assertFalse(callsName("今天天气不错", " ,，"))
        assertTrue(callsName("Hi Tom, are you there", "tom"))
    }

    @Test fun `a transcript line is speaker, clock and words`() {
        val line = transcriptLine(null, 0L, "你好")
        assertTrue(line.startsWith("说话的人  ") && line.endsWith("  你好"))
    }
}
