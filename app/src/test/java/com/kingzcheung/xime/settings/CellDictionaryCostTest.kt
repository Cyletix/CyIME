package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CellDictionaryCostTest {
    @Test
    fun `measured points and interpolation`() {
        assertEquals(0, CellDictionaryCost.slowdownPercent(0))
        assertEquals(14, CellDictionaryCost.slowdownPercent(50_000))
        assertEquals(21, CellDictionaryCost.slowdownPercent(100_000))
        assertEquals(35, CellDictionaryCost.slowdownPercent(250_000))
        assertEquals(1, CellDictionaryCost.slowdownPercent(5_000))
        assertEquals(28, CellDictionaryCost.slowdownPercent(175_000))
    }
}
