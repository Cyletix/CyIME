package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.KeyboardColorScheme
import com.kingzcheung.xime.ui.theme.PureBlackTheme
import com.kingzcheung.xime.ui.theme.SoftBlueTheme
import com.kingzcheung.xime.ui.theme.SoftLavenderTheme
import com.kingzcheung.xime.ui.theme.TonalThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTextHaloTest {
    private val themes: List<KeyboardColorScheme> =
        listOf(PureBlackTheme.create(), SoftBlueTheme.create(), SoftLavenderTheme.create()) + TonalThemes.create()

    private fun worstWith(halo: GlassTextHalo?, texts: List<Color>, panel: Color, opacity: Float): Float =
        listOf(Color.White, Color.Black).minOf { host ->
            var behind = panel.copy(alpha = opacity).compositeOver(host)
            if (halo != null) behind = halo.color.copy(alpha = halo.alpha).compositeOver(behind)
            texts.minOf { textContrast(it.copy(alpha = 1f), behind) }
        }

    /** 用户 10-31 截图：黑白浅色、玻璃默认（底 0.2），黑色宿主上候选字几乎看不见。 */
    @Test fun lightGlassDefaultOverBlackHostGetsAWhiteHaloThatReachesLabelContrast() {
        val theme = PureBlackTheme.create()
        val texts = listOf(theme.candidateTextColorLight, theme.candidateSelectedTextColorLight)
        val opacity = FrostedGlassConfig.defaults(isDark = false, enabled = true).backgroundOpacity
        assertTrue(worstWith(null, texts, theme.surfaceLight, opacity) < 2f)
        val halo = glassTextHalo(texts, theme.surfaceLight, opacity)
        assertNotNull(halo)
        halo!!
        assertEquals(Color.White, halo.color)
        assertTrue(halo.alpha < 1f)
        assertTrue(worstWith(halo, texts, theme.surfaceLight, opacity) >= GLASS_LABEL_CONTRAST)
    }

    @Test fun alreadyReadableGlassGetsNoHaloSoTheNormalLookIsUnchanged() {
        val theme = PureBlackTheme.create()
        val opacity = FrostedGlassConfig.defaults(isDark = true, enabled = true).backgroundOpacity
        assertNull(glassTextHalo(listOf(theme.candidateTextColorDark, theme.candidateSelectedTextColorDark),
            theme.surfaceDark, opacity))
        assertNull(glassTextHalo(listOf(Color(0xFF202020)), Color(0xFFF2F2F2), 1f))
    }

    /** 偏亮的灰字亮度不到 0.5，也要配黑晕；按 0.5 切会配白晕，白宿主上等于没有。 */
    @Test fun haloIsTheEndpointWithMoreContrastNotAHalfLuminanceCut() {
        assertEquals(Color.Black, glassHaloColor(Color(0xFFC4B2B4)))
        assertEquals(Color.Black, glassHaloColor(Color(0xFFE0E0E0)))
        assertEquals(Color.White, glassHaloColor(Color(0xFF454545)))
        assertEquals(Color.White, glassHaloColor(Color(0xFF42639C)))
    }

    @Test fun everyBuiltInThemeReachesLabelContrastOverWhiteAndBlackAtEveryOpacity() {
        themes.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val panel = if (dark) theme.surfaceDark else theme.surfaceLight
                val texts = if (dark) listOf(theme.candidateTextColorDark, theme.candidateSelectedTextColorDark)
                    else listOf(theme.candidateTextColorLight, theme.candidateSelectedTextColorLight)
                listOf(0f, 0.2f, 0.5f, 0.8f, 1f).forEach { opacity ->
                    val halo = glassTextHalo(texts, panel, opacity)
                    val worst = worstWith(halo, texts, panel, opacity)
                    // 全不透明的光晕是能给的最大反差；文字本身对黑白都不够时不再要求 4.5。
                    if (halo == null || halo.alpha < 1f) assertTrue("${theme.id} dark=$dark $opacity: $worst",
                        worst >= GLASS_LABEL_CONTRAST)
                }
            }
        }
    }

    @Test fun haloIsTheWeakestStepThatPasses() {
        val texts = listOf(Color(0xFF454545))
        val halo = glassTextHalo(texts, Color(0xFFE8E8EA), 0.2f)!!
        val weaker = halo.copy(alpha = halo.alpha - 0.05f)
        assertTrue(worstWith(weaker, texts, Color(0xFFE8E8EA), 0.2f) < GLASS_LABEL_CONTRAST)
    }

    private fun maskAlpha(matrix: FloatArray, color: Color): Float {
        val r = color.red * 255f; val g = color.green * 255f; val b = color.blue * 255f; val a = color.alpha * 255f
        return (matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]).coerceIn(0f, 255f)
    }

    /** 字形取满；选中高亮（主题色 20%）和键帽色按钮底不生晕。 */
    @Test fun glyphMaskKeepsTextAndDropsHighlightsAndKeycapFills() {
        val white = glyphMaskMatrix(darkGlyphs = true)
        assertTrue(maskAlpha(white, Color(0xFF454545)) > 0.7f * 255f)
        assertTrue(maskAlpha(white, Color(0xFF42639C)) > 0.7f * 255f)
        assertEquals(0f, maskAlpha(white, Color(0xFF454545).copy(alpha = 0.2f)), 0f)
        assertEquals(0f, maskAlpha(white, Color.White.copy(alpha = 0.8f)), 0f)
        val black = glyphMaskMatrix(darkGlyphs = false)
        assertTrue(maskAlpha(black, Color(0xFFE0E0E0)) > 0.7f * 255f)
        assertTrue(maskAlpha(black, Color(0xFFC4B2B4)) > 0.7f * 255f)
        assertEquals(0f, maskAlpha(black, Color(0xFFE0E0E0).copy(alpha = 0.2f)), 0f)
        assertEquals(0f, maskAlpha(black, Color(0xFF1C1C1E).copy(alpha = 0.2f)), 0f)
    }

    @Test fun textSurfaceCarriesTheHaloOnlyWhenTheRailGlassIsTooThin() {
        val thin = resolveFrostedTextSurface(Color(0xFFE8E8EA), Color(0xFF454545), Color.Black, Color(0xFF454545),
            FrostedGlassConfig.defaults(isDark = false, enabled = true), Color.White)
        assertEquals(Color.White, thin.halo?.color)
        val opaque = resolveFrostedTextSurface(Color(0xFFE8E8EA), Color(0xFF454545), Color.Black, Color(0xFF454545),
            FrostedGlassConfig(enabled = true, backgroundOpacity = 1f), Color.White)
        assertNull(opaque.halo)
    }
}
