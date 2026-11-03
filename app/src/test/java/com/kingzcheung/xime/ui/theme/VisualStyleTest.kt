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
    /** 11-03：流光玻璃、毛玻璃和真玻璃重复，列表只留三种；选过的人按原有外观画，存的值不动。 */
    @Test fun fakeGlassStylesAreNoLongerOfferedAndDrawAsOriginal() {
        assertEquals(listOf(VisualStyle.ORIGINAL, VisualStyle.NEON, VisualStyle.FACET), VisualStyle.offered)
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.forKeyboard(VisualStyle.GLASS))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.forKeyboard(VisualStyle.FROST))
        VisualStyle.offered.forEach { assertEquals(it, VisualStyle.forKeyboard(it)) }
    }
}
