package com.kingzcheung.xime.settings

import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class KeyboardPaletteUpgradeTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(base.cacheDir, "palette-upgrade-test")
    private val context = object : ContextWrapper(base) { override fun getFilesDir(): File = folder }
    @Before fun before() { File(folder, "rime").mkdirs() }
    @After fun after() {
        File(folder, "rime/xime.yaml").delete()
        File(folder, "rime/xime.custom.yaml").delete()
        KeysConfigHelper.loadConfig(base)
        KeyboardThemes.reload(base)
    }

    @Test fun packagedLavenderReplacesOldDeployedDefaultWithoutMigration() {
        File(folder, "rime/xime.yaml").writeText("""
            color_schemes:
              lavender_purple:
                keyboard_background: {type: solid, color_dark: 0xFF1E1838}
        """.trimIndent())
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        val theme = KeyboardThemes.getThemeById("lavender_purple")
        assertEquals(Color(0xFF211E28), theme.keyboardBgDark)
        assertEquals(Color(0xFF38333F), theme.keyBgDark)
        assertEquals(1f, theme.keyBgDark.alpha, 0f)
    }

    @Test fun explicitCustomPaletteStillWinsOverUpdatedPackagedDefaults() {
        File(folder, "rime/xime.custom.yaml").writeText("""
            color_schemes:
              lavender_purple:
                keyboard_background: {type: solid, color_dark: 0xFF102030}
                key_background: {type: solid, color_dark: 0xFF304050}
        """.trimIndent())
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        val theme = KeyboardThemes.getThemeById("lavender_purple")
        assertEquals(Color(0xFF102030), theme.keyboardBgDark)
        assertEquals(Color(0xFF304050), theme.keyBgDark)
    }
    @Test fun advance858AssetRolesMatchFallbackAndRemainAvailableWithoutChangingDefault() {
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        for (dark in listOf(false, true)) {
            assertEquals(Color(0xFF6D717C), KeyboardThemes.getSpecialKeyColor("858AdvanceColor", dark))
            assertEquals(Color(0xFF3F4E68), KeyboardThemes.getEnterKeyColor("858AdvanceColor", dark))
            assertEquals(Color(0xFF525252), KeyboardThemes.getKeyBgColorOverride("858AdvanceColor", dark))
            assertEquals(Color.White, KeyboardThemes.getSpecialKeyTextColor("858AdvanceColor", dark))
            assertEquals(Color.White, KeyboardThemes.getKeyTextColorOverride("858AdvanceColor", dark))
        }
        assertEquals("soft_blue", KeyboardThemes.themes.first().id)
    }

    @Test fun customEnterColorOverridesOnlyEnterRole() {
        File(folder, "rime/xime.custom.yaml").writeText("""
            color_schemes:
              858AdvanceColor:
                enter_key_bg_color_dark: 0xFF112233
        """.trimIndent())
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        assertEquals(Color(0xFF112233), KeyboardThemes.getEnterKeyColor("858AdvanceColor", true))
        assertEquals(Color(0xFF3F4E68), KeyboardThemes.getEnterKeyColor("858AdvanceColor", false))
        assertEquals(Color(0xFF6D717C), KeyboardThemes.getSpecialKeyColor("858AdvanceColor", true))
    }

}
