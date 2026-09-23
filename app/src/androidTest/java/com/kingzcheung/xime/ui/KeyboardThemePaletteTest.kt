package com.kingzcheung.xime.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.theme.DynamicThemes
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.SoftBlueTheme
import com.kingzcheung.xime.ui.theme.softDarkKeyContainer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KeyboardThemePaletteTest {
    @Before fun reload() {
        KeyboardThemes.reload(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun fixedThemeUsesItsCompletePaletteInsteadOfOldGlobalDefaults() {
        val theme = KeyboardThemes.getThemeById(SoftBlueTheme.ID)
        assertEquals(SoftBlueTheme.ID, theme.id)
        assertFalse(theme.isDynamic)
        for (dark in listOf(false, true)) {
            assertEquals(KeyboardThemes.getKeyBackgroundColor(theme.id, dark), KeyboardThemes.getKeyBgColorOverride(theme.id, dark))
            assertEquals(KeyboardThemes.getKeyTextColor(theme.id, dark), KeyboardThemes.getKeyTextColorOverride(theme.id, dark))
            assertEquals(KeyboardThemes.getCandidateTextColor(theme.id, dark), KeyboardThemes.getCandidateTextColorOverride(theme.id, dark))
        }
    }

    @Test fun lavenderAndOtherStaticThemesShareOneFunctionContainerRule() {
        listOf("lavender_purple", "ocean_blue", "teal_cyan").forEach { id ->
            val theme = KeyboardThemes.getThemeById(id)
            assertEquals(id, theme.id)
            assertEquals(softDarkKeyContainer(theme.accentDark), theme.specialKeyDark)
            assertEquals(theme.specialKeyDark, theme.primaryContainerDark)
        }
    }

    @Test fun dynamicThemeKeepsAnExplicitSystemFollowingEntryAndSoftDarkKeys() {
        val theme = KeyboardThemes.getThemeById(DynamicThemes.THEME_ID)
        assertEquals("跟随系统动态配色", theme.name)
        if (DynamicThemes.isSupported()) {
            assertTrue(theme.isDynamic)
            assertEquals(softDarkKeyContainer(theme.accentDark), theme.specialKeyDark)
        }
    }
}
