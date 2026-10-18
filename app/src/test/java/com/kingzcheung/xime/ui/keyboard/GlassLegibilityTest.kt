package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 玻璃材质在白底、黑底宿主上，任意透明度设置下键面文字都要清楚。 */
class GlassLegibilityTest {
    private data class Palette(val surface: Color, val keys: List<Pair<Color, Color>>)

    private val palettes = listOf(
        // 黑白深色、黑白浅色、薰衣草深色、iOS 渐变深色
        Palette(Color.Black, listOf(Color(0xFF2C2C2E) to Color.White, Color(0xFF48484A) to Color.White)),
        Palette(Color(0xFFE8E8EA), listOf(Color.White to Color(0xFF202020), Color(0xFFD4D4D8) to Color(0xFF202020))),
        Palette(Color(0xFF211E28), listOf(Color(0xFF38333F) to Color(0xFFF0EAF5))),
        Palette(Color(0xFF1F2229), listOf(Color(0xFF55555A) to Color.White, Color(0xFF3A3A3C) to Color.White)),
    )

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun worst(p: Palette, config: FrostedGlassConfig) = listOf(Color.White, Color.Black).minOf { backdrop ->
        val panel = p.surface.copy(alpha = config.backgroundOpacity).compositeOver(backdrop)
        p.keys.minOf { (key, text) -> contrast(text, frostedKeyColor(key, text, config).compositeOver(panel)) }
    }

    @Test fun everyOpacityPairStaysReadableOverWhiteAndBlack() {
        val levels = listOf(0f, 0.2f, 0.35f, 0.5f, 0.8f, 1f)
        for (p in palettes) for (bg in levels) for (key in levels) {
            val user = FrostedGlassConfig(enabled = true, backgroundOpacity = bg, keyOpacity = key)
            val shown = user.legibleOver(p.surface, p.keys)
            assertTrue("$p bg=$bg key=$key → ${worst(p, shown)}", worst(p, shown) >= GLASS_LABEL_CONTRAST)
            // 只会更不透明，不会反向改动用户设置。
            assertTrue(shown.backgroundOpacity >= bg - 0.0001f && shown.keyOpacity >= key - 0.0001f)
        }
    }

    @Test fun readableSettingsAreKeptExactly() {
        val dark = FrostedGlassConfig.defaults(isDark = true, enabled = true)
        val p = palettes.first()
        assertTrue(worst(p, dark) >= GLASS_LABEL_CONTRAST)
        assertEquals(dark, dark.legibleOver(p.surface, p.keys))
        val off = FrostedGlassConfig(enabled = false, backgroundOpacity = 0f, keyOpacity = 0f)
        assertEquals(off, off.legibleOver(p.surface, p.keys))
    }
}
