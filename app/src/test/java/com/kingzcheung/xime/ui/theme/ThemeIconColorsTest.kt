package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeIconColorsTest {
    @Test fun everyOfferedThemeHasAnIconColour() {
        val offered = listOf(TransparentGlassTheme.ID, PureBlackTheme.ID, "lavender_purple", SoftBlueTheme.ID) +
            TonalThemes.create().map { it.id }
        offered.forEach { assertTrue(it, ThemeIconColors.forTheme(it) in ThemeIconColors.ids) }
        // Each colourful theme gets its own colour; only the two neutral themes share one.
        assertEquals(offered.size - 1, offered.map(ThemeIconColors::forTheme).toSet().size)
    }

    @Test fun neutralAndUnknownThemes() {
        assertEquals("mono", ThemeIconColors.forTheme(PureBlackTheme.ID))
        assertEquals("mono", ThemeIconColors.forTheme(TransparentGlassTheme.ID))
        assertEquals("lavender", ThemeIconColors.forTheme("no_such_theme"))
    }
}
