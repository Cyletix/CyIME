package com.kingzcheung.xime.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.viewmodel.OFFERED_THEMES
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 莫奈取色加回配色方案（用户 11-03）：Android 12+ 才有，排在黑白后面、纯色前面；
 * 默认主题不变；能做「我的图片」的底，换壁纸后图片还在。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MonetThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = SettingsPreferences.getPrefsPublic(context)

    @After fun clear() {
        prefs.edit().remove("keyboard_theme").remove("custom_theme_image_src")
            .remove("custom_theme_image_base").remove("custom_theme_image_placement").commit()
        File(SchemaManager.getRimeDir(context), "themes").listFiles()
            ?.filter { it.name.startsWith("custom-keyboard-bg-") }?.forEach { it.delete() }
        KeyboardThemes.reload(context)
    }

    @Test fun offeredRightAfterPureBlackAndBeforeSolidThemes() {
        KeyboardThemes.reload(context)
        val offered = KeyboardThemes.themes.filter { it.id in OFFERED_THEMES }.map { it.id }
        assertEquals(listOf(TransparentGlassTheme.ID, PureBlackTheme.ID, DynamicThemes.THEME_ID, "lavender_purple", SoftBlueTheme.ID),
            offered.take(5))
        assertEquals(1, offered.count { it == DynamicThemes.THEME_ID })
    }

    @Test fun usesTheWallpaperPaletteUnderTheUsersName() {
        KeyboardThemes.reload(context)
        val theme = KeyboardThemes.getThemeById(DynamicThemes.THEME_ID)
        assertEquals(DynamicThemes.THEME_ID, theme.id)
        assertEquals("莫奈取色", theme.name)
        assertTrue(theme.isDynamic)
        // 设置页与键盘同一套颜色（完整主题规则），不再另用系统 Material 动态色。
        assertTrue(theme.useThemeColors)
        assertEquals(softDarkKeyContainer(theme.accentDark), theme.specialKeyDark)
    }

    @Test fun defaultThemeIsUnchangedAndAChosenMonetIsKept() {
        prefs.edit().remove("keyboard_theme").commit()
        assertEquals(SettingsPreferences.defaultKeyboardTheme, SettingsPreferences.getKeyboardTheme(context))
        assertFalse(SettingsPreferences.defaultKeyboardTheme == DynamicThemes.THEME_ID)
        prefs.edit().putString("keyboard_theme", DynamicThemes.THEME_ID).commit()
        assertEquals(DynamicThemes.THEME_ID, SettingsPreferences.getKeyboardTheme(context))
        assertEquals(DynamicThemes.THEME_ID, prefs.getString("keyboard_theme", null))
    }

    @Test fun customImageCanUseMonetAsItsBaseAndIsNotItselfRebuiltFromTheWallpaper() {
        KeyboardThemes.reload(context)
        val file = File(SchemaManager.getRimeDir(context), "themes/custom-keyboard-bg-1.jpg")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1))
        prefs.edit().putString("custom_theme_image_src", "themes/custom-keyboard-bg-1.jpg")
            .putString("custom_theme_image_base", DynamicThemes.THEME_ID).commit()
        val themes = KeyboardThemes.themes.filter { it.id != CustomImageTheme.ID }
        val monet = themes.first { it.id == DynamicThemes.THEME_ID }
        val custom = CustomImageTheme.build(context, themes)!!
        assertEquals(CustomImageTheme.ID, custom.id)
        assertEquals(DynamicThemes.THEME_ID, CustomImageTheme.baseThemeId)
        assertEquals(monet.keyBgLight, custom.keyBgLight)
        assertEquals(monet.keyTextColorDark, custom.keyTextColorDark)
        assertEquals((monet.keyboardBgLight.toArgb() and 0xFFFFFF).toLong(), custom.keyboardBackground!!.overlayColor)
        // 快照：不能被 refreshDynamicSchemes 当成动态主题整体重建（会丢图片），取色仍按完整主题。
        assertFalse(custom.isDynamic)
        assertTrue(custom.useThemeColors)
    }

    @Test fun importedThemesKeepTheirOldColourRuleUnderACustomImage() {
        KeyboardThemes.reload(context)
        val file = File(SchemaManager.getRimeDir(context), "themes/custom-keyboard-bg-2.jpg")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(2))
        prefs.edit().putString("custom_theme_image_src", "themes/custom-keyboard-bg-2.jpg")
            .putString("custom_theme_image_base", "mint_green").commit()
        val themes = KeyboardThemes.themes.filter { it.id != CustomImageTheme.ID }
        val base = themes.first { it.id == "mint_green" }
        assertEquals(base.useThemeColors, CustomImageTheme.build(context, themes)!!.useThemeColors)
    }

    @Test fun customYamlEntryNamedDynamicIsNotDuplicated() {
        val custom = DynamicThemes.create(context, DynamicThemes.THEME_ID, "我的动态")!!
        val list = KeyboardThemes.withDynamicTheme(context, listOf(PureBlackTheme.create(), custom))
        assertEquals(1, list.count { it.id == DynamicThemes.THEME_ID })
    }
}

/** Android 12 以下没有系统取色：列表里不出现，以前选过的回到柔和蓝。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], application = Application::class)
class MonetThemeOldAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun clear() {
        SettingsPreferences.getPrefsPublic(context).edit().remove("keyboard_theme").commit()
    }

    @Test fun hiddenBeforeAndroid12() {
        KeyboardThemes.reload(context)
        assertFalse(KeyboardThemes.themes.any { it.id == DynamicThemes.THEME_ID })
        assertEquals(SoftBlueTheme.ID, KeyboardThemes.getThemeById(DynamicThemes.THEME_ID).id)
    }

    @Test fun storedMonetFallsBackToSoftBlue() {
        SettingsPreferences.getPrefsPublic(context).edit().putString("keyboard_theme", DynamicThemes.THEME_ID).commit()
        assertEquals(SoftBlueTheme.ID, SettingsPreferences.getKeyboardTheme(context))
    }
}
