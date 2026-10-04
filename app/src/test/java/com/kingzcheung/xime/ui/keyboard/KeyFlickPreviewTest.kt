package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyFlickPreviewTest {
    @Test fun `paired letters move from the selected side and honor the configured distance`() {
        for (threshold in listOf(12f, 24f, 72f)) {
            val left = horizontalKeyFlickPreview(-(8f + threshold) / 2f, 0f, 8f, threshold, "q", "w")!!
            assertEquals(KeyFlickOrigin.LEFT, left.origin)
            assertEquals("q", left.text)
            assertEquals(0.5f, left.progress, 0.001f)
            assertEquals(KeyFlickPreview("w", 1f, KeyFlickOrigin.RIGHT),
                horizontalKeyFlickPreview(threshold + 1f, 0f, 8f, threshold, "q", "w"))
        }
        assertNull(horizontalKeyFlickPreview(0f, 0f, 8f, 24f, "q", "w"))
        assertNull(horizontalKeyFlickPreview(40f, 60f, 8f, 24f, "q", "w"))
        assertNull(horizontalKeyFlickPreview(-40f, 0f, 8f, 24f, null, "w"))
    }

    private fun preview(dy: Float, dx: Float = 0f) = keyFlickPreview(
        dx, dy, 8f, 50f, "!", "1", upFromTop = false, downFromTop = true)

    @Test fun `down pulls top symbol in and up pulls bottom symbol in`() {
        assertEquals(KeyFlickPreview("1", 1f, true), preview(75f))
        assertEquals(KeyFlickPreview("!", 1f, false), preview(-75f))
    }

    @Test fun `preview progresses before commit threshold and reverses with the finger`() {
        val near = preview(12f)!!
        val middle = preview(30f)!!
        assertTrue(near.progress > 0f)
        assertTrue(middle.progress > near.progress && middle.progress < 1f)
        assertEquals(near, preview(12f))
        assertNull(preview(0f))
        assertEquals(1f, preview(500f)!!.progress, 0f)
    }

    @Test fun `jitter horizontal drags and unavailable directions do not preview`() {
        assertNull(preview(8f))
        assertNull(preview(40f, 60f))
        assertNull(preview(-40f, -40f))
        assertNull(keyFlickPreview(0f, 80f, 8f, 50f, "1", null))
        assertNull(keyFlickPreview(0f, -80f, 8f, 50f, "", "!"))
    }

    @Test fun `reversed habit keeps original symbol origin independent of physical direction`() {
        assertEquals(KeyFlickPreview("1", 1f, true), keyFlickPreview(0f, -75f, 8f, 50f, "1", "!"))
        assertEquals(KeyFlickPreview("!", 1f, false), keyFlickPreview(0f, 75f, 8f, 50f, "1", "!"))
    }
}
