package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedKeyColorsTest {
    private val darkKey = Color(0xFF525252)
    private val enabled = FrostedGlassConfig(enabled = true, keyOpacity = 0.22f)
    // sRGB colors use 8-bit alpha channels.
    private val tolerance = 1f / 255f

    @Test fun disabledEffectPreservesExistingNormalPressedAndHighlightedColors() {
        val legacyColors = listOf(darkKey, darkKey.copy(alpha = 0.7f), Color(0xFF414141))
        legacyColors.forEach { legacy ->
            assertEquals(legacy, frostedKeyColor(darkKey, Color.White,
                enabled.copy(enabled = false), legacyStateColor = legacy, pressed = true, highlighted = true))
        }
    }

    @Test fun darkNeutralKeycapsBecomeVisibleFrostedTintWithoutChangingConfiguredOpacity() {
        val result = frostedKeyColor(darkKey, Color.White, enabled)
        assertTrue(result.red > 0.65f)
        assertEquals(result.red, result.green, tolerance)
        assertEquals(result.green, result.blue, tolerance)
        assertEquals(0.22f, result.alpha, tolerance)
    }

    @Test fun coloredActionKeysKeepTheirThemeHue() {
        val blue = Color(0xFF3079F6)
        val result = frostedKeyColor(blue, Color.White, enabled)
        assertEquals(blue.red, result.red, tolerance)
        assertEquals(blue.green, result.green, tolerance)
        assertEquals(blue.blue, result.blue, tolerance)
    }

    @Test fun lightThemeFillIsNotInverted() {
        val lightKey = Color(0xFFF4F4F4)
        val result = frostedKeyColor(lightKey, Color.Black, enabled)
        assertEquals(lightKey.red, result.red, tolerance)
        assertEquals(0.22f, result.alpha, tolerance)
    }

    @Test fun opaqueDarkKeycapsRetainTheirOriginalContrast() {
        assertEquals(darkKey, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = 1f)))
    }

    @Test fun interactionOpacityMultipliesTheSettingInsteadOfReplacingIt() {
        val pressed = frostedKeyColor(darkKey, Color.White, enabled,
            legacyStateColor = darkKey.copy(alpha = 0.7f), pressed = true)
        val highlighted = frostedKeyColor(darkKey, Color.White, enabled,
            legacyStateColor = darkKey.copy(alpha = 0.8f), highlighted = true)
        assertEquals(0.22f * 0.7f, pressed.alpha, tolerance)
        assertEquals(0.22f * 0.8f, highlighted.alpha, tolerance)
    }

    @Test fun zeroOpacityRemainsTransparentDuringAllInteractionStates() {
        listOf(false, true).forEach { pressed ->
            listOf(false, true).forEach { highlighted ->
                val result = frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = 0f),
                    pressed = pressed, highlighted = highlighted)
                assertEquals(0f, result.alpha, 0f)
            }
        }
    }

    @Test fun importedThemeTransparencyDoesNotOverrideTheIndependentOpacitySetting() {
        listOf(0f, 0.5f, 1f).forEach { themeOpacity ->
            val themeKey = darkKey.copy(alpha = themeOpacity)
            assertEquals(0.22f, frostedKeyColor(themeKey, Color.White, enabled).alpha, tolerance)
            assertEquals(1f, frostedKeyColor(themeKey, Color.White, enabled.copy(keyOpacity = 1f)).alpha, 0f)
        }
    }

    @Test fun explicitStateScaleKeepsTransparentHitRegionsAndDisabledControlsDistinct() {
        assertEquals(0f, frostedKeyColor(darkKey, Color.White, enabled, opacityScale = 0f).alpha, 0f)
        assertEquals(0.22f * 0.4f,
            frostedKeyColor(darkKey, Color.White, enabled, opacityScale = 0.4f).alpha, tolerance)
    }

    @Test fun invalidOpacityIsBounded() {
        assertEquals(1f, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = 2f)).alpha, 0f)
        assertEquals(0f, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = -1f)).alpha, 0f)
        assertEquals(0.22f,
            frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = Float.NaN)).alpha, tolerance)
    }
}
