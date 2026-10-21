package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig

/** 按下时键面向文字色靠拢的比例（所有键型共用）。 */
internal const val PRESSED_KEY_TINT = 0.14f

/** Only the keycap fill is translucent; labels and icons keep their theme colors. */
internal fun frostedKeyColor(
    background: Color,
    foreground: Color,
    config: FrostedGlassConfig,
    legacyStateColor: Color = background,
    pressed: Boolean = false,
    highlighted: Boolean = false,
    opacityScale: Float = 1f,
): Color {
    // 非玻璃主题的按下反馈也统一：键面向本键文字色靠一点（深色主题变亮、浅色主题变暗），
    // 不再有的键加深 10%、有的 20%、有的变透明 70%。高亮、禁用等其他状态仍用各自的 legacyStateColor。
    if (!config.enabled) return if (pressed) lerp(background, foreground.copy(alpha = background.alpha), PRESSED_KEY_TINT) else legacyStateColor

    val opacity = config.keyOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.22f
    // Dark keycaps must not lose their elevation twice: #1C1C1C at 20% would
    // become #060606 over black. Compensate the tint (not alpha), preserving
    // the theme's channel ratios and its original color at full opacity.
    // For light surfaces the same rule preserves their distance from white.
    val raisedTint = glassKeyTint(background, foreground, opacity)
    val feedback = when {
        pressed -> 0.08f
        highlighted -> 0.04f
        else -> 0f
    }
    val feedbackTarget = if (maxOf(kotlin.math.abs(raisedTint.red - foreground.red),
        kotlin.math.abs(raisedTint.green - foreground.green), kotlin.math.abs(raisedTint.blue - foreground.blue)) < 0.05f)
        background else foreground
    val tint = lerp(raisedTint, feedbackTarget, feedback)
    // Frosted material owns its opacity independently of an imported theme's alpha.
    // Disabled controls and transparent hit regions can explicitly scale the material.
    return tint.copy(alpha = opacity * opacityScale.coerceIn(0f, 1f))
}

/** 键帽最亮通道低于此值视为近黑（#29292B 以下）；其他主题的深色键帽都高于此值。 */
internal const val NEAR_BLACK_KEY_PEAK = .18f

/** 近黑键帽在玻璃下的提亮倍数（可调参数；渲染测试会临时改它出对比图）。 */
internal var glassNearBlackLift = 3f

private fun glassKeyTint(background: Color, foreground: Color, opacity: Float): Color {
    if (opacity <= 0f || opacity >= 1f) return background
    val darkSurface = foreground.luminance() > background.luminance()
    val r = if (darkSurface) background.red else 1f - background.red
    val g = if (darkSurface) background.green else 1f - background.green
    val b = if (darkSurface) background.blue else 1f - background.blue
    val peak = maxOf(r, g, b)
    if (peak <= 0f) return background
    // 近黑键帽（黑白主题深色）在玻璃下只提亮 GLASS_NEAR_BLACK_LIFT 倍：
    // 按 1/不透明度（默认 5 倍）会发灰，不提亮（1 倍）又和背景混在一起（2026-10-22 用户反馈）。
    val lift = if (darkSurface && peak < NEAR_BLACK_KEY_PEAK) glassNearBlackLift else 1f / opacity
    val scale = minOf(lift, 1f / opacity, 1f / peak)
    fun channel(value: Float) = if (darkSurface) value * scale else 1f - value * scale
    return Color(channel(r), channel(g), channel(b), background.alpha)
}

/**
 * 玻璃下键帽色按亮度统一提到同一层白（或黑），深色主题里功能键与字母键会变得一样。
 * 功能键（主题的功能键色）改用更透的材质，与 iOS 一样比字母键更暗/更淡，深浅色都看得出。
 */
internal const val GLASS_FUNCTION_KEY_SCALE = 0.5f

@androidx.compose.runtime.Composable
internal fun glassFunctionKeyScale(background: Color): Float {
    val palette = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current ?: return 1f
    fun Color.sameRgb(other: Color) = kotlin.math.abs(red - other.red) < 0.004f &&
        kotlin.math.abs(green - other.green) < 0.004f && kotlin.math.abs(blue - other.blue) < 0.004f
    return if (background.sameRgb(palette.function) && !palette.key.sameRgb(palette.function))
        GLASS_FUNCTION_KEY_SCALE else 1f
}

/** On glass, size and position distinguish hints; fading them would expose the host through text. */
internal fun keyHintColor(themeText: Color, config: FrostedGlassConfig, legacyAlpha: Float): Color =
    themeText.copy(alpha = if (config.enabled) 1f else legacyAlpha)

/** 玻璃材质上键面文字的最低对比度（按键字号大，取 WCAG AA 正文线 4.5:1）。 */
internal const val GLASS_LABEL_CONTRAST = 4.5f

/**
 * 玻璃材质的可读性下限：键盘后面可能是纯白页面，也可能是纯黑页面，两种最坏情况下键面文字都要清楚。
 * 用户设得太透时，背景与按键两个不透明度按同一比例向不透明靠拢，刚好达标为止；本来就清楚的设置原样保留。
 */
internal fun FrostedGlassConfig.legibleOver(
    surface: Color,
    keys: List<Pair<Color, Color>>,
    target: Float = GLASS_LABEL_CONTRAST,
): FrostedGlassConfig {
    if (!enabled || keys.isEmpty()) return this
    val base = normalized()
    fun worst(config: FrostedGlassConfig): Float = listOf(Color.White, Color.Black).minOf { backdrop ->
        val panel = surface.copy(alpha = config.backgroundOpacity).compositeOver(backdrop)
        keys.minOf { (key, text) -> contrastRatio(text, frostedKeyColor(key, text, config).compositeOver(panel)) }
    }
    if (worst(base) >= target) return base
    for (step in 1..20) {
        val t = step / 20f
        val raised = base.copy(
            backgroundOpacity = base.backgroundOpacity + (1f - base.backgroundOpacity) * t,
            keyOpacity = base.keyOpacity + (1f - base.keyOpacity) * t,
        )
        if (worst(raised) >= target) return raised
    }
    return base.copy(backgroundOpacity = 1f, keyOpacity = 1f)
}

/** 当前主题的字母键与功能键都参与检查。 */
internal fun FrostedGlassConfig.legibleForTheme(themeId: String, isDark: Boolean): FrostedGlassConfig {
    if (!enabled) return this
    val palette = com.kingzcheung.xime.ui.theme.resolveKeyboardPalette(themeId, isDark)
    return legibleOver(palette.background,
        listOf(palette.key to palette.text, palette.function to palette.functionText))
}

private fun contrastRatio(a: Color, b: Color): Float {
    val light = maxOf(a.luminance(), b.luminance())
    val dark = minOf(a.luminance(), b.luminance())
    return (light + 0.05f) / (dark + 0.05f)
}
