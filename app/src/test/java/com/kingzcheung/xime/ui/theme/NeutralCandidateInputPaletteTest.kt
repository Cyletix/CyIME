package com.kingzcheung.xime.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.preeditBubbleColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class NeutralCandidateInputPaletteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        KeyboardThemes.reload(context)
    }

    @Test fun matchesIosThemeInLightAndDark() {
        val light = resolveNeutralCandidateInputPalette(dark = false)
        val iosLight = resolveKeyboardPalette(TransparentGlassTheme.ID, dark = false)
        assertEquals(iosLight, light)
        assertEquals(Color.White.toArgb(), light.key.toArgb())
        assertEquals(Color(0xFF202020).toArgb(), light.text.toArgb())
        assertEquals(Color(0xFF454545).toArgb(), light.candidateText.toArgb())
        assertEquals(Color.Black.toArgb(), light.selectedText.toArgb())
        assertEquals(Color(0xFF454545).toArgb(), light.accent.toArgb())

        val dark = resolveNeutralCandidateInputPalette(dark = true)
        val iosDark = resolveKeyboardPalette(TransparentGlassTheme.ID, dark = true)
        assertEquals(iosDark, dark)
        assertEquals(Color(0xFF55555A).toArgb(), dark.key.toArgb())
        assertEquals(Color.White.toArgb(), dark.text.toArgb())
        assertEquals(Color(0xFFE0E0E0).toArgb(), dark.candidateText.toArgb())
        assertEquals(Color.White.toArgb(), dark.selectedText.toArgb())
        assertEquals(Color(0xFFE0E0E0).toArgb(), dark.accent.toArgb())
    }

    @Test fun keepsNeutralColorsEvenWhenActiveSkinIsColored() {
        val neutralLight = resolveNeutralCandidateInputPalette(dark = false)
        val neutralDark = resolveNeutralCandidateInputPalette(dark = true)

        for (themeId in listOf("sakura_pink", "sunset_gradient", "olivcelso_desert", SoftBlueTheme.ID)) {
            val coloredLight = resolveKeyboardPalette(themeId, dark = false)
            assertNotEquals(themeId, coloredLight.candidateText.toArgb(), neutralLight.candidateText.toArgb())
            assertNotEquals(themeId, coloredLight.accent.toArgb(), neutralLight.accent.toArgb())

            val bubbleLight = preeditBubbleColor(coloredLight.background, neutralLight.key, blurBehind = false)
            assertEquals(themeId, neutralLight.key.toArgb(), bubbleLight.toArgb())

            val coloredDark = resolveKeyboardPalette(themeId, dark = true)
            assertNotEquals(themeId, coloredDark.accent.toArgb(), neutralDark.accent.toArgb())

            val bubbleDark = preeditBubbleColor(coloredDark.background, neutralDark.key, blurBehind = false)
            assertEquals(themeId, neutralDark.key.toArgb(), bubbleDark.toArgb())
        }
    }
}
