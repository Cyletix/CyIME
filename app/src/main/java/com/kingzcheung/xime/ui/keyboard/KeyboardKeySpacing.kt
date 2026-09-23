package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/** One pair of axis scales for the measured body, including both halves of a split keyboard. */
internal data class KeyboardKeySpacingScale(val horizontal: Float = 1f, val vertical: Float = 1f)

internal fun keyboardKeySpacingScale(widthDp: Float, heightDp: Float): KeyboardKeySpacingScale {
    fun axis(actual: Float, reference: Float): Float =
        if (!actual.isFinite() || actual <= 0f) 1f else (actual / reference).coerceIn(1f, 4f)
    // Keep normal phone gaps; when a tablet grows only in width, its horizontal gap must grow
    // by the same proportion. Tall enter and wide space use this same body-level pair.
    return KeyboardKeySpacingScale(axis(widthDp, 420f), axis(heightDp, 280f))
}

internal val LocalKeyboardKeySpacingScale = staticCompositionLocalOf { KeyboardKeySpacingScale() }

@Composable
internal fun KeyboardKeySpacingScope(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        val scale = keyboardKeySpacingScale(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalKeyboardKeySpacingScale provides scale) {
            content(Modifier.fillMaxSize())
        }
    }
}

/** Configured padding stays unscaled in its local; this accessor applies the body scale once. */
@Composable
internal fun scaledKeyVisualPadding(padding: PaddingValues = LocalKeyVisualPadding.current): PaddingValues {
    val scale = LocalKeyboardKeySpacingScale.current
    val direction = LocalLayoutDirection.current
    return remember(padding, scale, direction) {
        PaddingValues(
            start = padding.calculateStartPadding(direction) * scale.horizontal,
            top = padding.calculateTopPadding() * scale.vertical,
            end = padding.calculateEndPadding(direction) * scale.horizontal,
            bottom = padding.calculateBottomPadding() * scale.vertical,
        )
    }
}

@Composable
internal fun keyboardKeyGapX(gap: Dp): Dp = gap * LocalKeyboardKeySpacingScale.current.horizontal

@Composable
internal fun keyboardKeyGapY(gap: Dp): Dp = gap * LocalKeyboardKeySpacingScale.current.vertical
