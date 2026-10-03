package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.TransparentGlassTheme
import com.kingzcheung.xime.ui.theme.keyboardBackground

/** Draw on the actual panel content, never on a popup/window positioning host. */
@Composable
internal fun Modifier.keyboardPanelBackground(
    fallback: Color,
    glass: FrostedGlassConfig = LocalKeyboardInputPreferences.current.frostedGlass,
): Modifier = if (glass.enabled) {
    // Keep this backdrop opaque as on the screen keyboard: the text keys behind an
    // overlay must not show through. Only its material and individual key fills vary.
    keyboardBackground(
        KeyboardThemes.getRenderingScheme(TransparentGlassTheme.ID).keyboardBackground,
        fallback.luminance() < 0.5f,
        fallback,
        glass,
    )
} else background(fallback)
