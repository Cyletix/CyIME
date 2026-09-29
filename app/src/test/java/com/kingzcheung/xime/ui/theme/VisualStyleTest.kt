package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class VisualStyleTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun allFourPresetsKeepKeysAndCandidatesReadable() {
        val styles = VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }
        assertEquals(4, styles.size)
        styles.forEach { style ->
            val palette = requireNotNull(VisualStyles.palette(style))
            assertTrue(style.id, contrast(palette.keyTextColorLight, palette.keyBgLight) >= 4.5f)
            assertTrue(style.id, contrast(palette.keyTextColorLight, palette.specialKeyLight) >= 4.5f)
            assertTrue(style.id, contrast(palette.candidateTextColorLight, palette.keyboardBgLight) >= 4.5f)
            assertEquals(1f, palette.keyBgLight.alpha, 0f)
        }
    }

    @Test fun originalAndUnknownIdsNeverOverrideImportedThemes() {
        assertNull(VisualStyles.palette(VisualStyle.ORIGINAL))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId("unknown"))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId(null))
        assertEquals(VisualStyle.entries.size, VisualStyle.entries.map { it.id }.distinct().size)
    }
}
