package com.kingzcheung.xime.ui.caption

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.frostedKeyColor
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground

private fun readableOn(color: Color) = if (color.luminance() > .5f) Color(0xFF111111) else Color.White

/**
 * 字幕页跟随键盘主题：底图/渐变用键盘背景，按钮用键帽色，面板用功能键色，主操作用回车键色，
 * 文字用键帽文字色。开了玻璃效果时按钮和面板按键盘同一套透明度变成半透明玻璃。
 * 做法是把这些颜色填进 MaterialTheme，页面里的文字、输入框、标签栏都跟着走，不再各写一套。
 */
@Composable
internal fun CaptionThemed(dark: Boolean, content: @Composable () -> Unit) {
    val palette = LocalKeyboardPalette.current ?: return content()
    val glass = rememberKeyboardInputPreferences(dark).frostedGlass
    val base = MaterialTheme.colorScheme
    val scheme = remember(palette, glass, base) {
        val key = frostedKeyColor(palette.key, palette.text, glass)
        // 面板比按钮更透一档，按钮浮在面板上才分得出层次（同键盘里功能键比字母键更淡的做法）。
        val panel = frostedKeyColor(palette.function, palette.functionText, glass, opacityScale = .6f)
            .let { if (glass.enabled) it else it.copy(alpha = .92f) }
        val primary = palette.enter.copy(alpha = 1f)
        // 对话框等需要一个不透明底色的地方：玻璃时就是“主题背景 × 透明度”叠在中性底上的结果。
        val surface = if (glass.enabled) palette.background.copy(alpha = glass.backgroundOpacity).compositeOver(captionBackstop(dark))
            else palette.background.copy(alpha = 1f)
        base.copy(
            surface = surface,
            onSurface = palette.text.copy(alpha = 1f),
            onSurfaceVariant = palette.text.copy(alpha = .72f),
            primary = palette.accent.copy(alpha = 1f),
            onPrimary = readableOn(palette.accent),
            primaryContainer = primary,
            onPrimaryContainer = readableOn(primary),
            surfaceContainer = panel,
            surfaceContainerLow = panel,
            surfaceContainerHigh = key,
            surfaceContainerHighest = key,
            outline = palette.text.copy(alpha = .38f),
            outlineVariant = palette.text.copy(alpha = .16f),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

/** 玻璃键盘后面“透出来”的东西：字幕页后面没有别的应用，用一块和深浅色一致的中性底代替。 */
internal fun captionBackstop(dark: Boolean) = if (dark) Color(0xFF0E0F12) else Color(0xFFF2F3F5)

/**
 * 页面底：和键盘一模一样地画主题背景（纯色、渐变或图片；开玻璃时按键盘的背景透明度和模糊），
 * 垫在中性底上——这正是键盘做可读性检查时假设的最坏情况，所以文字对比有保证。
 */
@Composable
internal fun Modifier.captionPageBackground(): Modifier {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.onSurface.luminance() > .5f
    val themeId = remember { SettingsPreferences.getKeyboardTheme(context) }
    val palette = LocalKeyboardPalette.current
    val fallback = palette?.background?.copy(alpha = 1f) ?: MaterialTheme.colorScheme.surface
    val glass = rememberKeyboardInputPreferences(dark).frostedGlass
    return background(captionBackstop(dark)).keyboardBackground(KeyboardThemes.getRenderingScheme(themeId).keyboardBackground,
        dark, fallback, glass)
}

/**
 * 不透明主题色才按 [alpha] 变半透明；已经是玻璃色（主题给出的透明度）就原样用，
 * 不能把玻璃键帽的 20% 透明度改成 78% 的白板。
 */
internal fun Color.translucent(alpha: Float): Color = if (this.alpha < 1f) this else copy(alpha = alpha)
