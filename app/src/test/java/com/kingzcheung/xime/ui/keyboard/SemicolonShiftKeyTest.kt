package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemicolonShiftKeyTest {
    @Test
    fun shiftBecomesIngOnlyWhileAFinalIsAwaited() {
        // 空闲、打完一个音节时 Shift 照旧；打了声母（或零声母的 o）等韵母时是 ing。
        assertFalse(awaitsDoublePinyinFinal(""))
        assertTrue(awaitsDoublePinyinFinal("b"))
        assertTrue(awaitsDoublePinyinFinal("o"))
        assertFalse(awaitsDoublePinyinFinal("b;"))
        assertTrue(awaitsDoublePinyinFinal("b;g"))
        assertFalse(awaitsDoublePinyinFinal("b;gr"))
        // 手动分隔符不算键。
        assertTrue(awaitsDoublePinyinFinal("b;'g"))
        assertFalse(awaitsDoublePinyinFinal("b;'gr"))
    }
}
