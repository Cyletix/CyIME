package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class FourteenKeyInputTest {
    @Test fun pairedLettersFollowPrintedOrderAndCaseOnlyWhenEnabled() {
        for (group in listOf("qw", "er", "ty", "ui", "op", "as", "df", "gh", "jk", "zx", "cv", "bn")) {
            assertEquals(group[0].toString() to group[1].toString(), fourteenKeyLetters(group, true))
            assertEquals(group[0].uppercase() to group[1].uppercase(), fourteenKeyLetters(group.uppercase(), true))
            assertNull(fourteenKeyLetters(group, false))
        }
        for (group in listOf(null, "", "l", "m", "space", "12", "?!", "a1", "qw ")) assertNull(fourteenKeyLetters(group, true))
    }

    @Test fun distanceBoundsAndStepAreStableForInvalidAndRestoredValues() {
        assertEquals(24f, normalizeLetterSwipeDistance(Float.NaN), 0f)
        assertEquals(24f, normalizeLetterSwipeDistance(Float.POSITIVE_INFINITY), 0f)
        assertEquals(12f, normalizeLetterSwipeDistance(-5f), 0f)
        assertEquals(72f, normalizeLetterSwipeDistance(100f), 0f)
        assertEquals(26f, normalizeLetterSwipeDistance(25f), 0f)
    }
}
