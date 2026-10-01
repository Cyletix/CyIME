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
    @Test fun removedThemeCannotReturnFromOldDeployedConfig() {
        File(folder, "rime/xime.custom.yaml").writeText("""
            color_schemes:
              858AdvanceColor:
                name: "858AdvanceColor"
                primary_color: 0xC3CDDF
        """.trimIndent())
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        assertFalse(KeyboardThemes.themes.any { it.id == "858AdvanceColor" })
        assertEquals("soft_blue", KeyboardThemes.getThemeById("858AdvanceColor").id)
    }

    @Test fun customEnterColorOverridesOnlyEnterRole() {
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        val before = KeyboardThemes.getSpecialKeyColor("soft_blue", true)
        File(folder, "rime/xime.custom.yaml").writeText("""
            color_schemes:
              soft_blue:
                enter_key_bg_color_dark: 0xFF112233
        """.trimIndent())
        KeysConfigHelper.loadConfig(context)
        KeyboardThemes.reload(context)
        assertEquals(Color(0xFF112233), KeyboardThemes.getEnterKeyColor("soft_blue", true))
        assertEquals(before, KeyboardThemes.getSpecialKeyColor("soft_blue", true))
    }
}
