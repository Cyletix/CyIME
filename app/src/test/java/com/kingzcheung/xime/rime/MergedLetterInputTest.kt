package com.kingzcheung.xime.rime

import org.junit.Assert.*
import org.junit.Test

class MergedLetterInputTest {
    @Test fun aSinglePairOffersBothLettersAndOnlyExplicitLettersAreSelected() {
        for (group in MergedLetterInput.groups) {
            val inputs = listOf(group.first().uppercase(), group[0].toString(), group[1].toString())
            for ((selection, input) in inputs.withIndex()) {
                val pair = MergedLetterInput.singleKeyGroup(input, 1, 0)
                assertEquals(group, pair)
                val snapshot = PinyinSyllableChoices(1, "pinyin_14jian", 1, input, 0, "", emptyList(), pair)
                assertEquals(group.map { it.toString() }, snapshot.labels)
                assertEquals(selection - 1, snapshot.selectedIndex)
            }
        }
        for (input in listOf("", "m", "l", "S", "'", "AA", "niA"))
            assertNull(MergedLetterInput.singleKeyGroup(input, input.length, 0))
        assertNull(MergedLetterInput.singleKeyGroup("A", 0, 0))
        assertNull(MergedLetterInput.singleKeyGroup("A", 1, 1))
    }

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
