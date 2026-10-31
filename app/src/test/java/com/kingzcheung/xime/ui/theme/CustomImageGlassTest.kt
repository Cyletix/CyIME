package com.kingzcheung.xime.ui.theme

import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「我的图片」开着玻璃时背景图不模糊、不变淡；内置图片主题和别的背景照旧走玻璃。 */
class CustomImageGlassTest {
    private val glass = FrostedGlassConfig(enabled = true, blurRadiusDp = 10f, backgroundOpacity = 0.2f, keyOpacity = 0.3f)

    @Test fun customImageKeepsItsPictureUnderGlass() {
        val custom = BackgroundConfig(type = "image", src = "themes/custom-keyboard-bg-1793440000000.jpg")
        assertTrue(CustomImageTheme.isCustomImage(custom))
        val used = glass.forBackground(custom)
        assertEquals(0f, used.blurRadiusDp)
        assertEquals(1f, used.backgroundOpacity)
        assertEquals("按键照旧按玻璃透明", glass.keyOpacity, used.keyOpacity)
        assertTrue(used.enabled)
    }

    @Test fun builtInImagesAndOtherBackgroundsStayGlass() {
        val builtIn = BackgroundConfig(type = "image", src = "themes/desert.jpg")
        assertFalse(CustomImageTheme.isCustomImage(builtIn))
        assertEquals(glass, glass.forBackground(builtIn))
        assertEquals(glass, glass.forBackground(BackgroundConfig(type = "solid")))
        assertEquals(glass, glass.forBackground(null))
    }
}
