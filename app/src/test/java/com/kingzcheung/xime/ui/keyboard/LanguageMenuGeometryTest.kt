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
                assertTrue(menu.rowHeightPx >= LANGUAGE_MENU_MIN_ROW_DP*d - 0.001f)
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

    @Test fun lowestKeyboardHeightShowsThreeWholeLanguagesAboveTheKey() {
        // 用户 11-02：键盘调到最低（244dp，含 44dp 工具栏 + 四行 48dp），语言菜单至少整行放下三种语言。
        for (d in listOf(2f, 2.625f, 2.8f, 3f)) for (count in listOf(3, 9)) {
            val panel = Rect(0f, 1000f, 412f * d, 1000f + 244f * d)
            val key = Rect(60f * d, panel.bottom - 52f * d, 110f * d, panel.bottom - 4f * d)
            val menu = languageMenuGeometry(panel, key, d, count)
            assertTrue(menu.anchoredAbove)
            assertEquals(key.top, menu.bounds.bottom, .001f)
            val list = menu.bounds.height - LANGUAGE_MENU_CHROME_DP * d
            assertTrue("d=$d count=$count", list + .5f >= 3 * menu.rowHeightPx)
            assertTrue(menu.rowHeightPx >= LANGUAGE_MENU_MIN_ROW_DP * d - .001f)
        }
    }

    @Test fun manyLanguagesGrowAboveTheKeyboardUntilAllFit() {
        // 用户 11-05：三种以上语言时列表滚不动，改成按高度往上长，伸出键盘把所有语言摆出来。
        val d = 2.8f
        val panel = Rect(0f, 1360f, 1080f, 2060f)
        val key = Rect(190f, 1930f, 340f, 2060f)
        val statusBar = 24f * d
        for (count in listOf(3, 4, 6, 9)) {
            val inside = languageMenuGeometry(panel, key, d, count)
            val menu = languageMenuGeometry(panel, key, d, count, ceiling = statusBar)
            assertTrue(menu.anchoredAbove)
            assertEquals(key.top, menu.bounds.bottom, .001f)
            // 行高、宽度和左右位置跟只在键盘里时一样，只是往上长。
            assertEquals(inside.rowHeightPx, menu.rowHeightPx, .001f)
            assertEquals(inside.bounds.left, menu.bounds.left, .001f)
            assertEquals(inside.bounds.right, menu.bounds.right, .001f)
            assertEquals("count=$count", LANGUAGE_MENU_CHROME_DP * d + count * menu.rowHeightPx, menu.bounds.height, .001f)
            assertTrue(menu.bounds.top >= statusBar + 8f * d - .001f)
            // 列表整个放得下，不用滚。
            assertEquals(0, languageMenuInitialScroll(menu, count - 1, d))
        }
    }

    @Test fun languagesThatFitInsideTheKeyboardDoNotGrowAboveIt() {
        val d = 2.8f
        val panel = Rect(0f, 1360f, 1080f, 2060f)
        val key = Rect(190f, 1930f, 340f, 2060f)
        for (count in 1..3) {
            val inside = languageMenuGeometry(panel, key, d, count)
            val menu = languageMenuGeometry(panel, key, d, count, ceiling = 0f)
            assertEquals(inside.bounds.top, menu.bounds.top, .01f)
            assertEquals(inside.bounds.bottom, menu.bounds.bottom, .01f)
            assertEquals(inside.rowHeightPx, menu.rowHeightPx, .001f)
            assertTrue(menu.bounds.top >= panel.top)
        }
    }

    @Test fun shortWindowStopsBelowTheStatusBarAndScrolls() {
        // 横屏手机：键盘上方也放不下全部语言时，菜单顶停在状态栏下沿留边距处，剩下的在菜单里滚动。
        val d = 2.75f
        val panel = Rect(0f, 500f, 2400f, 1080f)
        val key = Rect(300f, 960f, 500f, 1080f)
        val statusBar = 24f * d
        val menu = languageMenuGeometry(panel, key, d, 30, ceiling = statusBar)
        assertTrue(menu.anchoredAbove)
        assertEquals(statusBar + 8f * d, menu.bounds.top, .001f)
        assertEquals(key.top, menu.bounds.bottom, .001f)
        assertTrue(menu.bounds.height < LANGUAGE_MENU_CHROME_DP * d + 30 * menu.rowHeightPx)
    }

    @Test fun ceilingBelowThePanelTopKeepsTheMenuInsideTheKeyboard() {
        // 键盘窗口只有键盘那么高（拿到的窗口顶就是面板顶）时照旧只在键盘里。
        val d = 2.8f
        val panel = Rect(0f, 0f, 1080f, 700f)
        val key = Rect(190f, 570f, 340f, 700f)
        assertEquals(languageMenuGeometry(panel, key, d, 12),
            languageMenuGeometry(panel, key, d, 12, ceiling = 0f))
        assertEquals(languageMenuGeometry(panel, key, d, 12),
            languageMenuGeometry(panel, key, d, 12, ceiling = 200f))
    }

    @Test fun tallKeyboardKeepsTheNormalRowHeight() {
        val d = 2.8f
        val panel = Rect(0f, 1000f, 412f * d, 1000f + 360f * d)
        val key = Rect(60f * d, panel.bottom - 70f * d, 110f * d, panel.bottom - 4f * d)
        val menu = languageMenuGeometry(panel, key, d, 9)
        assertTrue(menu.anchoredAbove)
        assertEquals(64f * d, menu.rowHeightPx, .001f)
    }
}
