package com.kingzcheung.xime.rime

import org.junit.Assert.*
import org.junit.Test

class MergedLetterInputTest {
    @Test fun tapsAndExplicitLettersHaveDistinctCodesWithoutChangingCustomActions() {
        for (group in MergedLetterInput.groups) {
            assertEquals(group, MergedLetterInput.tapValue(group, group.take(1), true, false))
            assertTrue(MergedLetterInput.isGroup(group, true))
            group.forEach { assertFalse(MergedLetterInput.isGroup(it.toString(), true)) }
            assertEquals(group.take(1), MergedLetterInput.tapValue(group, group.take(1), false, false))
            assertEquals(group.take(1).uppercase(), MergedLetterInput.tapValue(group, group.take(1), true, true))
            assertEquals("custom", MergedLetterInput.tapValue(group, "custom", true, false))
        }
        assertEquals("ni'hao", merged14Preedit("BUGaO", "bu'gao", "ni hao"))
    }
}
