package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** GitHub #63: sliders set below the legibility floor now say what the keyboard really uses. */
class GlassActualOpacityTest {
    @Test fun raisedValueIsShown() {
        assertEquals(" · 实际 62%", actualSuffix(0.07f, 0.62f))
        assertEquals("", actualSuffix(0.80f, 0.80f))
        assertEquals("", actualSuffix(0.80f, 0.804f))
        assertEquals("", actualSuffix(0.30f, null))
    }
}
