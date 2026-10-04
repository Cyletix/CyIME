package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardBackdropFrameTest {
    @Test fun `keyboard bottom and navigation top sample adjacent rows from one image`() {
        for (navHeight in listOf(24, 48, 96)) {
            val frame = keyboardBackdropFrame(Offset(0f, 1000f), IntSize(1200, 800), 400 + navHeight)
            val keyOrigin = frame.origin
            val navOrigin = keyOrigin + Offset(0f, 400f)
            assertEquals(IntSize(1200, 400 + navHeight), frame.size)
            assertEquals(Offset.Zero, frame.offsetIn(keyOrigin))
            // Last row of keys and first row of navigation are image rows 399 and 400.
            assertEquals(399f, 399f - frame.offsetIn(keyOrigin).y, 0f)
            assertEquals(400f, -frame.offsetIn(navOrigin).y, 0f)
        }
    }

    @Test fun `narrow offset keyboards use the corresponding horizontal image slice`() {
        val frame = keyboardBackdropFrame(Offset(30f, 900f), IntSize(1400, 500), 500)
        for (keyLeft in listOf(0f, 200f, 650f)) {
            val keyOrigin = frame.origin + Offset(keyLeft, 0f)
            val navOrigin = frame.origin + Offset(0f, 450f)
            val keySampleX = 70f - frame.offsetIn(keyOrigin).x
            val navSampleX = keyLeft + 70f - frame.offsetIn(navOrigin).x
            assertEquals(keySampleX, navSampleX, 0f)
        }
    }

    @Test fun `fullscreen resize host does not stretch backdrop across empty space above keyboard`() {
        val frame = keyboardBackdropFrame(Offset(0f, 40f), IntSize(1080, 2000), 540)
        assertEquals(Offset(0f, 1500f), frame.origin)
        assertEquals(IntSize(1080, 540), frame.size)
        val movedHost = keyboardBackdropFrame(Offset(0f, 65f), IntSize(1080, 2000), 540)
        assertEquals(frame.offsetIn(Offset(120f, 1700f)), movedHost.offsetIn(Offset(120f, 1725f)))
    }

    @Test fun `rotation and insets replace the shared frame rather than rescaling just navigation`() {
        val portrait = keyboardBackdropFrame(Offset.Zero, IntSize(1080, 800), 600)
        val landscape = keyboardBackdropFrame(Offset(0f, 24f), IntSize(1800, 420), 380)
        assertEquals(IntSize(1080, 600), portrait.size)
        assertEquals(IntSize(1800, 380), landscape.size)
        assertEquals(Offset(0f, 64f), landscape.origin)
    }

    @Test fun `unmeasured and oversized frames stay inside actual host bounds`() {
        assertEquals(IntSize.Zero, keyboardBackdropFrame(Offset.Zero, IntSize.Zero, 500).size)
        assertEquals(IntSize(800, 200), keyboardBackdropFrame(Offset.Zero, IntSize(800, 200), 500).size)
        assertEquals(IntSize(800, 0), keyboardBackdropFrame(Offset.Zero, IntSize(800, 200), -1).size)
    }
}
