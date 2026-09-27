package com.kingzcheung.xime.ui.keyboard
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
class LanguageMenuGeometryTest {
    @Test fun staysInsideActualPanelAtExtremeSizesAndPositions() {
        for (d in listOf(1f, 3f)) for (w in listOf(200f, 360f, 400f, 1400f)) for (h in listOf(160f, 228f, 700f)) {
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
}
