package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 浅色玻璃的候选栏补到按键的厚度，不再透出后面深色应用（GitHub #40/#55，平板 11-01 复现）。 */
class GlassStripBoostTest {
    @Test fun lightGlassStripReachesKeyOpacity() {
        val light = FrostedGlassConfig.defaults(isDark = false, enabled = true)
        val boost = glassStripBoostAlpha(light.backgroundOpacity, light.keyOpacity)
        assertEquals(0.75f, boost, 1e-4f)
        // 叠起来正好是按键的 80%。
        assertEquals(light.keyOpacity, 1f - (1f - light.backgroundOpacity) * (1f - boost), 1e-4f)
    }

    @Test fun darkGlassAndOpaquePanelsAreUnchanged() {
        val dark = FrostedGlassConfig.defaults(isDark = true, enabled = true)
        assertEquals(0f, glassStripBoostAlpha(dark.backgroundOpacity, dark.keyOpacity), 0f)
        // 「我的图片」底图不透明（forBackground 把底色提到 1）：不补。
        assertEquals(0f, glassStripBoostAlpha(1f, 0.8f), 0f)
        assertEquals(0f, glassStripBoostAlpha(0.5f, 0.5f), 0f)
        assertEquals(0f, glassStripBoostAlpha(Float.NaN, 0.8f), 0f)
        assertEquals(0f, glassStripBoostAlpha(0.2f, Float.NaN), 0f)
    }

    @Test fun boostedLightStripNeedsNoGlowBehindDarkText() {
        val surface = Color(0xFFF2F2F2)
        val text = Color(0xFF222222)
        val light = FrostedGlassConfig.defaults(isDark = false, enabled = true)
        // 原来按 20% 底色算：深色应用上深字不够看，只能垫白光晕。
        assertNotNull(resolveFrostedTextSurface(surface, text, text, text, light, surface).halo)
        // 补到 80% 后两种宿主上都够看，不再垫光晕。
        assertNull(resolveFrostedTextSurface(surface, text, text, text,
            light.copy(backgroundOpacity = light.keyOpacity), surface).halo)
    }
}
