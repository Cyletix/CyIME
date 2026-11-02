package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 键盘上方的拼音气泡要是实底：应用画面在后面时拼音照样看得清（用户 10-31）。 */
class PreeditBubbleColorTest {
    private val darkSurface = Color(0xFF26232F)
    private val lightSurface = Color(0xFFF2F2F2)

    @Test fun bubbleIsOpaqueKeyColorOverTheKeyboardSurface() {
        val translucentKey = Color(0xFF4A4658).copy(alpha = 0.6f)
        val bubble = preeditBubbleColor(darkSurface, translucentKey, blurBehind = false)
        assertEquals(1f, bubble.alpha, 0f)
        assertEquals(translucentKey.compositeOver(darkSurface).copy(alpha = 1f), bubble)
    }

    @Test fun missingKeyColorFallsBackToTheOpaqueSurface() {
        assertEquals(darkSurface, preeditBubbleColor(darkSurface.copy(alpha = 0.3f), Color.Unspecified, blurBehind = false))
        assertEquals(lightSurface, preeditBubbleColor(lightSurface, Color.Transparent, blurBehind = false))
    }

    @Test fun glassWithLiveBlurKeepsOnlyASliverOfSeeThrough() {
        val bubble = preeditBubbleColor(darkSurface, Color(0xFF4A4658), blurBehind = true)
        // Color 按 8 位存透明度，0.9 存成 230/255。
        assertEquals(PREEDIT_BUBBLE_GLASS_ALPHA, bubble.alpha, 1f / 255f)
        assertTrue(bubble.alpha >= 0.9f)
    }

    @Test fun userOpacityMakesTheBubbleSeeThroughAndGlassStillCapsIt() {
        val key = Color(0xFF4A4658)
        assertEquals(0.5f, preeditBubbleColor(darkSurface, key, blurBehind = false, opacity = 0.5f).alpha, 1f / 255f)
        assertEquals(0.5f, preeditBubbleColor(darkSurface, key, blurBehind = true, opacity = 0.5f).alpha, 1f / 255f)
        assertEquals(PREEDIT_BUBBLE_GLASS_ALPHA, preeditBubbleColor(darkSurface, key, blurBehind = true, opacity = 1f).alpha, 1f / 255f)
        assertEquals(1f, preeditBubbleColor(darkSurface, key, blurBehind = false).alpha, 0f)
    }

    @Test fun opacitySettingSnapsToFivePercentWithinTwentyToHundred() {
        assertEquals(0.2f, com.kingzcheung.xime.settings.SettingsPreferences.normalizePreeditBubbleOpacity(0.05f), 0.0001f)
        assertEquals(0.65f, com.kingzcheung.xime.settings.SettingsPreferences.normalizePreeditBubbleOpacity(0.66f), 0.0001f)
        assertEquals(1f, com.kingzcheung.xime.settings.SettingsPreferences.normalizePreeditBubbleOpacity(Float.NaN), 0f)
    }

    @Test fun bubbleTextReachesKeycapContrastOnBothDarkAndLightThemes() {
        listOf(
            Triple(darkSurface, Color(0xFF4A4658), Color(0xFFB9A8E8)),
            Triple(darkSurface, Color(0xFF4A4658), Color(0xFF5A5470)),
            Triple(lightSurface, Color.White, Color(0xFF9A9A9A)),
        ).forEach { (surface, key, text) ->
            val bubble = preeditBubbleColor(surface, key, blurBehind = false)
            assertTrue(textContrast(readableThemeText(text, bubble), bubble) >= 4.5f)
        }
    }
}
