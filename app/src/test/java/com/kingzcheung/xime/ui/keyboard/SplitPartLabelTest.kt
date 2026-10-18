package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitPartLabelTest {
    private val nbsp = Char(0x00A0).toString()
    private val ideographicSpace = Char(0x3000).toString()

    @Test fun spacesKeepTheirOwnWidthInsteadOfTheWordSpace() {
        assertEquals(nbsp, splitPartLabel(" "))
        assertEquals(nbsp + nbsp, splitPartLabel("  "))
        assertEquals(nbsp.repeat(4), splitPartLabel("\t"))
        assertEquals(ideographicSpace, splitPartLabel(ideographicSpace))
    }

    @Test fun wordsAndLineBreaksAreUnchanged() {
        assertEquals("剪贴板", splitPartLabel("剪贴板"))
        assertEquals("a b", splitPartLabel("a b"))
        assertEquals("换行", splitPartLabel(" \n"))
    }
}
