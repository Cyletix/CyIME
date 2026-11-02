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

    @Test
    fun `each dictionary shows its own share`() {
        // 三角洲行动 323 词、我的世界 1576 词：单开时各自的份额。
        assertEquals("0.1%", CellDictionaryCost.format(CellDictionaryCost.share(0, 323)))
        assertEquals("0.4%", CellDictionaryCost.format(CellDictionaryCost.share(0, 1576)))
        // 已开 10 万词时再开 5 万词，按曲线只多 4.7%，不是单开时的 14%。
        assertEquals("4.7%", CellDictionaryCost.format(CellDictionaryCost.share(100_000, 50_000)))
        assertEquals("<0.1%", CellDictionaryCost.format(CellDictionaryCost.share(0, 100)))
        assertEquals("14%", CellDictionaryCost.format(CellDictionaryCost.slowdown(50_000)))
    }

    @Test
    fun `levels for the colour`() {
        assertEquals(CellDictionaryCost.Level.NORMAL, CellDictionaryCost.level(CellDictionaryCost.slowdown(1_899)))
        assertEquals(CellDictionaryCost.Level.NOTICEABLE, CellDictionaryCost.level(CellDictionaryCost.slowdown(20_000)))
        assertEquals(CellDictionaryCost.Level.NOTICEABLE, CellDictionaryCost.level(CellDictionaryCost.slowdown(50_000)))
        assertEquals(CellDictionaryCost.Level.HEAVY, CellDictionaryCost.level(CellDictionaryCost.slowdown(50_001)))
    }
}
