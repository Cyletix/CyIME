package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground

/** Draw on the actual panel content, never on a popup/window positioning host. */
@Composable
internal fun Modifier.keyboardPanelBackground(
    fallback: Color,
    glass: FrostedGlassConfig = LocalKeyboardInputPreferences.current.frostedGlass,
): Modifier {
    if (glass.enabled && inheritsKeyboardBackground()) return this
    val themeBackground = KeyboardThemes.getRenderingScheme(SettingsPreferences.getKeyboardTheme(LocalContext.current)).keyboardBackground
    // 渐变/图片主题不能用一块主题底色代替（iOS 渐变的底色是纯黑）；纯色主题仍按调用方给的底色。
    val layered = themeBackground?.type == "gradient" || themeBackground?.type == "image"
    return if (glass.enabled || layered) keyboardBackground(themeBackground, fallback.luminance() < 0.5f, fallback, glass)
    else background(fallback)
}
