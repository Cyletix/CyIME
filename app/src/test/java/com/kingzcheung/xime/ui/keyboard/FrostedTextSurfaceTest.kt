package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.PureBlackTheme
import com.kingzcheung.xime.ui.theme.SoftBlueTheme
import com.kingzcheung.xime.ui.theme.SoftLavenderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedTextSurfaceTest {
    @Test fun readableThemeTextStaysOpaqueAndKeepsItsColorInBothModes() {
        listOf(PureBlackTheme.create(), SoftBlueTheme.create(), SoftLavenderTheme.create()).forEach { theme ->
            listOf(false, true).forEach { dark ->
                val background = if (dark) theme.keyboardBgDark else theme.keyboardBgLight
                val normal = if (dark) theme.candidateTextColorDark else theme.candidateTextColorLight
                val selected = if (dark) theme.candidateSelectedTextColorDark else theme.candidateSelectedTextColorLight
                val accent = if (dark) theme.accentDark else theme.accentLight
                val surface = resolveFrostedTextSurface(background, normal, selected, accent)
                assertEquals(normal.copy(alpha = 1f), surface.text)
                assertEquals(1f, surface.selectedText.alpha, 0f)
                assertTrue((surface.shadow.color.luminance() > 0.5f) != (surface.text.luminance() > 0.5f))
            }
        }
    }

    @Test fun paleImageThemeTextGetsOnlyTheLightnessCorrectionNeededForItsNominalSurface() {
        val background = Color(0xFFF2F2F2)
        val paleText = Color(0xFFA4828C)
        val accent = Color(0xFFDCA79C)
        val surface = resolveFrostedTextSurface(background, paleText, accent, accent)
        assertTrue(surface.text.red < paleText.red)
        assertTrue(surface.text.red > surface.text.blue && surface.text.blue > surface.text.green)
        assertTrue(textContrast(surface.text, background) >= 4.5f)
    }

    @Test fun existingRoundControlsFollowKeyOpacityIncludingTransparentEndpoint() {
        listOf(0f, 0.2f, 0.8f, 1f).forEach { opacity ->
            val surface = resolveFrostedTextSurface(Color(0xFFF4F6FA), Color(0xFF42639C),
                config = FrostedGlassConfig(enabled = true, keyOpacity = opacity), keyBackground = Color.White)
            assertEquals(opacity, surface.controlBackground.alpha, 1f / 255f)
            assertEquals(Color.White, surface.controlBackground.copy(alpha = 1f))
        }
    }

    @Test fun textTreatmentNeverChangesTheInheritedRailBackgroundOrThemeHighlight() {
        val original = CandidateBarVisuals(Color.Transparent, Color(0xFF42639C), Color.Gray,
            Color.Blue, Color(0xFF2C4D85), preeditBackgroundColor = Color.White)
        assertEquals(original, original.withTextSurface(null))
        val surface = resolveFrostedTextSurface(Color(0xFFF4F6FA), original.textColor,
            original.selectedTextColor, original.accentColor)
        val result = original.withTextSurface(surface)
        assertTrue(result.opaqueComments)
        assertEquals(original.backgroundColor, result.backgroundColor)
        assertEquals(original.accentColor, result.accentColor)
        assertEquals(original.preeditBackgroundColor, result.preeditBackgroundColor)
        assertEquals(surface.shadow, result.textShadow)
    }
}
