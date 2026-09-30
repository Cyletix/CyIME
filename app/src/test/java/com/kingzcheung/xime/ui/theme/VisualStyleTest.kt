package com.kingzcheung.xime.ui.theme

import org.junit.Assert.*
import org.junit.Test

class VisualStyleTest {
    @Test fun materialsNeverOverrideThemeOrBrightness() {
        val original = VisualStyles.current
        try {
            val expected = KeyboardThemes.getThemeById("soft_blue")
            VisualStyle.entries.forEach { style ->
                VisualStyles.current = style
                assertNull(style.dark)
                assertEquals(expected, KeyboardThemes.getRenderingScheme("soft_blue"))
            }
        } finally { VisualStyles.current = original }
    }
    @Test fun storedStyleIdsRemainCompatible() {
        assertEquals(listOf("original", "neon", "glass", "facet", "frost"), VisualStyle.entries.map { it.id })
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId("unknown"))
        assertEquals(VisualStyle.FROST, VisualStyle.fromId("frost"))
    }
}
