package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class CandidateNumberLabelTest {
    @Test fun numbersMatchOneThroughNineAndZeroWithoutWrapping() {
        assertEquals("1 你好", candidateNumberedText("你好", 0, true))
        assertEquals("9 世界", candidateNumberedText("世界", 8, true))
        assertEquals("0 世界", candidateNumberedText("世界", 9, true))
        assertEquals("后续", candidateNumberedText("后续", 10, true))
        assertEquals("hello", candidateNumberedText("hello", 0, false))
        assertEquals("剪贴板", candidateNumberedText("剪贴板", -1, true))
    }
}
