package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「柔化顶部边缘」的模糊细条：首尾相接盖满上沿那一段、宽度和卡片一样，透明度从上到下一级级升，不到 1。 */
class GlassTopFeatherTest {
    @Test fun bandsTileTheFeatherWithRisingAlpha() {
        // 2.625x 手机：16dp = 42px，切 6 条每条 7px。
        val bands = glassTopFeatherBands(0, 1000, 1080, 1700, 42)
        assertEquals(GLASS_TOP_FEATHER_STEPS, bands.size)
        assertEquals(1000, bands.first().top)
        assertEquals(1042, bands.last().bottom)
        bands.zipWithNext { a, b ->
            assertEquals("细条之间不能有缝或重叠", a.bottom, b.top)
            assertTrue("透明度要一路升", b.alpha > a.alpha)
        }
        assertTrue(bands.all { it.left == 0 && it.right == 1080 })
        assertTrue("最上一条要很淡", bands.first().alpha < 0.2f)
        assertTrue("最下一条也不能满，满的是下面那整块", bands.last().alpha < 1f)
    }

    @Test fun oddPixelHeightsStillTile() {
        // mdpi 平板：16px 切 6 条，有的 3px 有的 2px，仍首尾相接。
        val bands = glassTopFeatherBands(100, 500, 900, 900, 16)
        assertEquals(500, bands.first().top)
        assertEquals(516, bands.last().bottom)
        bands.zipWithNext { a, b -> assertEquals(a.bottom, b.top) }
    }

    @Test fun tooShortOrEmptyCardGetsNoBands() {
        assertTrue(glassTopFeatherBands(0, 0, 1080, 60, 42).isEmpty())
        assertTrue(glassTopFeatherBands(0, 0, 0, 600, 42).isEmpty())
        assertTrue(glassTopFeatherBands(0, 0, 1080, 600, 0).isEmpty())
    }
}
