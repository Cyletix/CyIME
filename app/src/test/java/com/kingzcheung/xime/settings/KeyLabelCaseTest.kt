package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyLabelCaseTest {
    @Test fun `letter caps follow shift, other labels stay as written`() {
        assertEquals("Q", keyLabelWithCase("q", isShifted = true))
        assertEquals("q", keyLabelWithCase("Q", isShifted = false))
        // 西班牙语的 ñ 键按 Shift 上屏 Ñ，键帽也要显示 Ñ。
        assertEquals("Ñ", keyLabelWithCase("ñ", isShifted = true))
        assertEquals("ñ", keyLabelWithCase("ñ", isShifted = false))
        for (label in listOf("手", "ㄅ", "，", "@language", "q1")) assertEquals(label, keyLabelWithCase(label, isShifted = true))
    }
}
