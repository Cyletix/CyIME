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
        // 10-18 旧主题每个色系恢复一套（如樱花粉黛）；未恢复的旧 id（海洋蔚蓝，蓝色由柔和蓝代表）仍回落到柔和蓝。
        assertEquals("sakura_pink", KeyboardThemes.getThemeById("sakura_pink").id)
        assertEquals(SoftBlueTheme.ID, KeyboardThemes.getThemeById("ocean_blue").id)
        listOf("lavender_purple").forEach { id ->
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
