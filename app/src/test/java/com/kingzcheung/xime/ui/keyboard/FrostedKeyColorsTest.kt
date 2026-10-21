package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostedKeyColorsTest {
    private val darkKey = Color(0xFF525252)
    private val enabled = FrostedGlassConfig(enabled = true, keyOpacity = 0.22f)
    // sRGB colors use 8-bit alpha channels.
    private val tolerance = 1f / 255f

    @Test fun disabledEffectPreservesExistingNormalAndHighlightedColors() {
        val legacyColors = listOf(darkKey, darkKey.copy(alpha = 0.7f), Color(0xFF414141))
        legacyColors.forEach { legacy ->
            assertEquals(legacy, frostedKeyColor(darkKey, Color.White,
                enabled.copy(enabled = false), legacyStateColor = legacy, highlighted = true))
        }
    }

    @Test fun disabledEffectUsesOnePressTintForEveryKeyType() {
        // 所有键型按下都一样：向本键文字色靠 PRESSED_KEY_TINT，深色主题变亮、浅色主题变暗。
        val pressedDark = frostedKeyColor(darkKey, Color.White, enabled.copy(enabled = false),
            legacyStateColor = darkKey.copy(alpha = 0.7f), pressed = true)
        assertEquals(androidx.compose.ui.graphics.lerp(darkKey, Color.White, PRESSED_KEY_TINT), pressedDark)
        val lightKey = Color(0xFFF2F2F2)
        val pressedLight = frostedKeyColor(lightKey, Color.Black, enabled.copy(enabled = false), pressed = true)
        assertTrue(pressedLight.red < lightKey.red)
    }

    @Test fun darkNeutralKeycapsKeepTheirChannelRatiosAndConfiguredOpacity() {
        val result = frostedKeyColor(darkKey, Color.White, enabled)
        assertTrue(result.red > darkKey.red)
        assertEquals(result.red, result.green, tolerance)
        assertEquals(result.green, result.blue, tolerance)
        assertEquals(0.22f, result.alpha, tolerance)
    }

    @Test fun coloredActionKeysKeepTheirThemeHue() {
        val blue = Color(0xFF3079F6)
        val result = frostedKeyColor(blue, Color.White, enabled)
        assertEquals(blue.red / blue.blue, result.red / result.blue, tolerance)
        assertEquals(blue.green / blue.blue, result.green / result.blue, tolerance)
    }

    @Test fun lightThemeFillIsNotInverted() {
        val lightKey = Color(0xFFF4F4F4)
        val result = frostedKeyColor(lightKey, Color.Black, enabled)
        assertTrue(result.red > 0.75f)
        assertEquals(result.red, result.green, tolerance)
        assertEquals(0.22f, result.alpha, tolerance)
    }

    @Test fun opaqueDarkKeycapsRetainTheirOriginalContrast() {
        assertEquals(darkKey, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = 1f)))
    }

    @Test fun interactionFeedbackPreservesConfiguredOpacityAndChangesOnlyTint() {
        val feedbackConfig = enabled.copy(keyOpacity = 0.6f)
        val normal = frostedKeyColor(darkKey, Color.White, feedbackConfig)
        val pressed = frostedKeyColor(darkKey, Color.White, feedbackConfig,
            legacyStateColor = darkKey.copy(alpha = 0.7f), pressed = true)
        val highlighted = frostedKeyColor(darkKey, Color.White, feedbackConfig,
            legacyStateColor = darkKey.copy(alpha = 0.8f), highlighted = true)
        assertEquals(0.6f, pressed.alpha, tolerance)
        assertEquals(0.6f, highlighted.alpha, tolerance)
        assertTrue(pressed.red > highlighted.red)
        assertTrue(highlighted.red > normal.red)
    }

    @Test fun nearBlackKeysStayDarkUnderGlass() {
        // 2026-10-22 用户：黑白主题深色的近黑键帽在玻璃下只稍微提亮——比背景亮一档，不发灰；字仍要清楚。
        val normalKey = Color(0xFF1C1C1E)
        val functionKey = Color(0xFF29292B)
        val glass = FrostedGlassConfig.defaults(true, true)
        for (host in listOf(Color.Black, Color.White)) {
            val panel = Color.Black.copy(alpha = glass.backgroundOpacity).compositeOver(host)
            for (key in listOf(normalKey, functionKey)) {
                val fill = frostedKeyColor(key, Color.White, glass).compositeOver(panel)
                assertTrue("near-black key stays dark on $host", fill.red - panel.red < 0.1f)
                assertTrue("but is still told apart from the panel on $host", fill.red - panel.red > 0.01f)
                assertTrue(textContrast(Color.White, fill) >= 4.5f)
            }
        }
        assertEquals(normalKey.red * glassNearBlackLift, frostedKeyColor(normalKey, Color.White, glass).red, tolerance)
        // 其他主题的深色键帽仍按原规则提亮，保持层次。
        val softBlueKey = Color(0xFF2E323A)
        assertTrue(frostedKeyColor(softBlueKey, Color.White, glass).red > softBlueKey.red + 0.1f)
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

    @Test fun liftedFillStillShowsPressFeedbackWhenTintReachesWhite() {
        val cap = Color(0xFF353535)
        val config = enabled.copy(keyOpacity = 0.2f)
        val normal = frostedKeyColor(cap, Color.White, config)
        val pressed = frostedKeyColor(cap, Color.White, config, pressed = true)
        assertTrue(normal != pressed)
        assertEquals(normal.alpha, pressed.alpha, 0f)
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

    @Test fun glassHintsKeepThemeRgbAndFullOpacityAcrossAppearanceAndGestureStates() {
        val texts = listOf(Color(0xFF20242C), Color(0xFFF0EAF5), Color(0xFF685191))
        texts.forEach { text ->
            listOf(0.5f, 0.6f, 0.7f, 0.85f, 1f).forEach { legacyAlpha ->
                assertEquals(text, keyHintColor(text.copy(alpha = 0.5f), enabled, legacyAlpha))
                assertEquals(text.copy(alpha = legacyAlpha),
                    keyHintColor(text, enabled.copy(enabled = false), legacyAlpha))
            }
        }
    }

    @Test fun defaultLightHintsRemainReadableOverBlackAndWhiteHostContent() {
        val text = Color(0xFF20242C)
        val glass = enabled.copy(backgroundOpacity = 0.2f, keyOpacity = 0.8f)
        listOf(Color.Black, Color.White).forEach { host ->
            val panel = Color(0xFFF4F6FA).copy(alpha = glass.backgroundOpacity).compositeOver(host)
            listOf(false, true).forEach { pressed ->
                val key = frostedKeyColor(Color.White, text, glass, pressed = pressed).compositeOver(panel)
                val hint = keyHintColor(text, glass, 0.5f).compositeOver(key)
                assertTrue("small hints must remain readable over $host, pressed=$pressed",
                    textContrast(hint, key) >= 4.5f)
            }
        }
    }
    @Test fun invalidOpacityIsBounded() {
        assertEquals(1f, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = 2f)).alpha, 0f)
        assertEquals(0f, frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = -1f)).alpha, 0f)
        assertEquals(0.22f,
            frostedKeyColor(darkKey, Color.White, enabled.copy(keyOpacity = Float.NaN)).alpha, tolerance)
    }
}
