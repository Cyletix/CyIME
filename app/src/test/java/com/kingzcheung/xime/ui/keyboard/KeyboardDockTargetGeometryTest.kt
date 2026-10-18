package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Component geometry only; the service restoring saved state needs separate IME coverage. */
class KeyboardDockTargetGeometryTest {
    @Test fun tabletSavedLeftAlignedWidthAndHeightHaveIndependentlyCalculatedEdges() {
        // The user's saved tablet geometry is 1005 x 433, offset -244 in a 1493dp host.
        // 1493 - 1005 = 488, so the centered half-margin 244 is entirely removed.
        for (density in listOf(1f, 1.875f)) {
            val plain = fixedKeyboardRect(1493f * density, 934f * density,
                433f * density, 0f, 0f, 1005f * density, -244f * density)
            assertRect(plain, 0f, 501f, 1005f, 934f, density)
            val padded = fixedKeyboardRect(1493f * density, 934f * density,
                433f * density, 20f * density, 24f * density, 1005f * density, -244f * density)
            assertRect(padded, 0f, 457f, 1005f, 910f, density)
            assertEquals(453f * density, padded.height, .01f)
            val target = KeyboardDockTarget(1005, 433, -244, 20, 24)
                .rectInHost(1493f * density, 934f * density, density)
            assertRect(target, 0f, 457f, 1005f, 910f, density)
        }
    }

    @Test fun fixedTargetKeepsTheSameDimensionsInFullAndContentHeightHosts() {
        val full = fixedKeyboardRect(1493f, 934f, 433f, 20f, 24f, 1005f, -244f)
        val contentHost = fixedKeyboardRect(1493f, 477f, 433f, 20f, 24f, 1005f, -244f)
        assertRect(full, 0f, 457f, 1005f, 910f, 1f)
        assertRect(contentHost, 0f, 0f, 1005f, 453f, 1f)
        assertEquals(full.width, contentHost.width, .01f)
        assertEquals(full.height, contentHost.height, .01f)
    }

    private fun assertRect(actual: ResizeRect, left: Float, top: Float, right: Float, bottom: Float, density: Float) {
        assertEquals("left", left * density, actual.left, .01f)
        assertEquals("top", top * density, actual.top, .01f)
        assertEquals("right", right * density, actual.right, .01f)
        assertEquals("bottom", bottom * density, actual.bottom, .01f)
    }
}
