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

    /** 角标不透明度（GitHub #7 #60）：默认 1 和原来一模一样；调低时开不开玻璃都按比例变淡。 */
    @Test fun cornerHintOpacityDefaultsToTodayAndFadesWithOrWithoutGlass() {
        val text = Color(0xFF20242C)
        listOf(0.5f, 0.6f, 0.7f).forEach { legacyAlpha ->
            assertEquals(keyHintColor(text, enabled, legacyAlpha), keyHintColor(text, enabled, legacyAlpha, 1f))
            val off = enabled.copy(enabled = false)
            assertEquals(keyHintColor(text, off, legacyAlpha), keyHintColor(text, off, legacyAlpha, 1f))
            assertEquals(0.4f, keyHintColor(text, enabled, legacyAlpha, 0.4f).alpha, tolerance)
            assertEquals(legacyAlpha * 0.4f, keyHintColor(text, off, legacyAlpha, 0.4f).alpha, tolerance)
            assertEquals(text.red, keyHintColor(text, enabled, legacyAlpha, 0.4f).red, 0f)
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

    @Test fun iosGradientGlassUsesTheEarlyNearBlackKeys() {
        // 玻璃下 iOS 渐变深色键按早期近黑键提亮：字母键 #1C1C1C → #8C8C8C，功能键更亮（2026-10-30 用户反馈）。
        val glass = FrostedGlassConfig.defaults(isDark = true, enabled = true)
        val letter = frostedKeyColor(Color(0xFF55555A), Color.White, glass)
        val function = frostedKeyColor(Color(0xFF3A3A3C), Color.White, glass)
        assertEquals(0x8C / 255f, letter.red, tolerance)
        assertEquals(0.2f, letter.alpha, tolerance)
        assertTrue(function.red > letter.red + 0.3f)
        // 不开玻璃或按键不透明时仍用灰键，保留与渐变底的对比。
        assertEquals(Color(0xFF55555A), frostedKeyColor(Color(0xFF55555A), Color.White, glass.copy(enabled = false)))
        assertEquals(Color(0xFF55555A), frostedKeyColor(Color(0xFF55555A), Color.White, glass.copy(keyOpacity = 1f)))
    }

    @Test fun whiteGlassPanelIsThickOnlyForWhiteGlass() {
        // 白色玻璃：底色最少 WHITE_GLASS_PANEL_OPACITY，后面是深色应用也不透成深灰；更厚的设置、深色玻璃、关着玻璃都原样。
        val light = FrostedGlassConfig.defaults(false, enabled = true)
        assertEquals(WHITE_GLASS_PANEL_OPACITY, light.whiteGlassPanel(true).backgroundOpacity, 0f)
        assertEquals(light.keyOpacity, light.whiteGlassPanel(true).keyOpacity, 0f)
        val thick = light.copy(backgroundOpacity = .95f)
        assertEquals(thick, thick.whiteGlassPanel(true))
        val dark = FrostedGlassConfig.defaults(true, enabled = true).copy(backgroundOpacity = .3f)
        assertEquals(dark, dark.whiteGlassPanel(false))
        assertEquals(light.copy(enabled = false), light.copy(enabled = false).whiteGlassPanel(true))
        // 浅色页亲手拖过背景不透明度：按滑条来，不补（用户反馈 11-03：工具栏和底色要跟着透明度走）。
        val custom = light.copy(lightPanelCustom = true)
        assertEquals(custom, custom.whiteGlassPanel(true))
    }

    @Test fun whiteGlassFunctionKeysStayLighterThanTextButDarkerThanPanelOverDarkApps() {
        // 用户 11-03 截图：白色玻璃功能键减半透明后，深色应用上成了深灰块。同样厚、深一档后：
        // 字母键最亮，功能键比底色深一档，且都远亮于深字。
        val config = FrostedGlassConfig.defaults(false, enabled = true).whiteGlassPanel(true)
        val text = Color(0xFF202020)
        val panelColor = Color(0xFFE8E8EA)
        listOf(Color(0xFF141414), Color.White).forEach { app ->
            val panel = panelColor.copy(alpha = config.backgroundOpacity).compositeOver(app)
            val letter = frostedKeyColor(Color.White, text, config).compositeOver(panel)
            val function = frostedKeyColor(Color(0xFFD4D4D8), text, config,
                functionDepth = WHITE_GLASS_FUNCTION_DEPTH).compositeOver(panel)
            assertTrue(letter.red > panel.red && function.red < panel.red - .02f)
            assertTrue(function.red > .6f)
        }
    }
}
