package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class SwipeBubblePlacementTest {
    @Test fun topRowMayExtendAboveKeyboardWithoutMovingItsHorizontalAnchor() {
        val p = swipeBubbleWindowPosition(IntRect(100, 400, 900, 800), IntSize(1000, 1000),
            IntSize(250, 150), 4f, -52f, 4f)
        assertEquals(100, p.x)
        assertEquals(344, p.y)
        assertTrue(p.y < 400)
    }

    @Test fun popupStaysInsidePhysicalScreenAtEveryCorner() {
        for (window in listOf(IntSize(360, 800), IntSize(1493, 934)))
            for (x in listOf(0, window.width - 200)) for (y in listOf(0, window.height - 200)) {
                val p = swipeBubbleWindowPosition(IntRect(x, y, x + 200, y + 200), window,
                    IntSize(180, 130), 4f, -52f, 4f)
                assertTrue(p.x >= 0 && p.y >= 0)
                assertTrue(p.x + 180 <= window.width && p.y + 130 <= window.height)
            }
    }
}
