package com.kingzcheung.xime.ui.keyboard
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
class LanguageMenuGeometryTest {
    @Test fun fractionallyScaledMenuFitsAboveTheKeyAtLargeWindowOffsets() {
        val density = 432 / 160f
        for (offset in listOf(2107f, 2110f)) {
            val panel = Rect(0f, offset, 1080f, 2400f)
            val key = Rect(840f, 2300f, 960f, 2400f)
            val menu = languageMenuGeometry(panel, key, density, 2)
            val inset = 8f * density
            assertEquals(panel.top + inset, menu.bounds.top, .001f)
            assertEquals(panel.bottom - inset, menu.bounds.bottom, .001f)
            assertEquals(key.right, menu.bounds.right, .001f)
        }
    }

    @Test fun staysInsideActualPanelAtExtremeSizesAndPositions() {
        for (d in listOf(1f, 2.7f, 3f)) for (w in listOf(100f, 200f, 360f, 400f, 1400f)) for (h in listOf(160f, 228f, 700f)) {
            val panel = Rect(170f*d, 80f*d, (170+w)*d, (80+h)*d)
            for (x in listOf(panel.left, panel.center.x, panel.right-32*d)) {
                val key = Rect(x, panel.bottom-44*d, x+32*d, panel.bottom)
                val menu = languageMenuGeometry(panel, key, d, 10)
                assertTrue(menu.bounds.left >= panel.left && menu.bounds.right <= panel.right)
                assertTrue(menu.bounds.top >= panel.top && menu.bounds.bottom <= panel.bottom)
                assertTrue(menu.bounds.width <= 280*d + 0.1f)
                assertTrue(menu.rowHeightPx >= 44*d)
            }
        }
    }
    @Test fun rightEdgeAnchorsToLanguageKeyWhenItFits() {
        val panel = Rect(0f, 0f, 400f, 300f)
        val key = Rect(300f, 240f, 340f, 300f)
        assertEquals(key.right, languageMenuGeometry(panel, key, 1f, 3).bounds.right, 0.01f)
    }

    @Test fun manyLanguagesStayAboveTheKeyAndScrollInside() {
        // 用户 11-01 截图：九键、所有语言都加上后，菜单铺满整块键盘盖住了语言键。
        val d = 2.8f
        val panel = Rect(0f, 1360f, 1080f, 2060f)
        val key = Rect(190f, 1930f, 340f, 2060f)
        val menu = languageMenuGeometry(panel, key, d, 12)
        assertTrue(menu.anchoredAbove)
        assertEquals(key.top, menu.bounds.bottom, .001f)
        assertEquals(panel.top + 8f * d, menu.bounds.top, .001f)
        assertTrue(menu.bounds.height < LANGUAGE_MENU_CHROME_DP * d + 12 * menu.rowHeightPx)
    }

    @Test fun fewLanguagesSitDirectlyOnTheKey() {
        val panel = Rect(0f, 0f, 400f, 300f)
        val key = Rect(300f, 240f, 340f, 300f)
        val menu = languageMenuGeometry(panel, key, 1f, 2)
        assertTrue(menu.anchoredAbove)
        assertEquals(key.top, menu.bounds.bottom, .001f)
        assertEquals(LANGUAGE_MENU_CHROME_DP + 2 * menu.rowHeightPx, menu.bounds.height, .001f)
    }

    @Test fun currentLanguageStartsInTheRowNextToTheFinger() {
        val d = 2.8f
        val panel = Rect(0f, 1360f, 1080f, 2060f)
        val key = Rect(190f, 1930f, 340f, 2060f)
        val menu = languageMenuGeometry(panel, key, d, 12)
        val list = menu.bounds.height - LANGUAGE_MENU_CHROME_DP * d
        for (index in 0 until 12) {
            val scroll = languageMenuInitialScroll(menu, index, d)
            val rowBottom = (index + 1) * menu.rowHeightPx - scroll
            assertTrue(rowBottom <= list + 1f)
            // 列表本身够长时，当前语言就是最下面一行。
            if ((index + 1) * menu.rowHeightPx >= list) assertEquals(list, rowBottom, 1f)
        }
        // 当前语言不在列表里时按第一项算。
        assertEquals(0, languageMenuInitialScroll(menu, -1, d))
    }

    @Test fun fallbackKeepsTheOldTopAlignedScroll() {
        val density = 432 / 160f
        val panel = Rect(0f, 2107f, 1080f, 2400f)
        val key = Rect(840f, 2300f, 960f, 2400f)
        val menu = languageMenuGeometry(panel, key, density, 5)
        assertFalse(menu.anchoredAbove)
        assertEquals((3 * menu.rowHeightPx).toInt(), languageMenuInitialScroll(menu, 3, density))
    }
}
