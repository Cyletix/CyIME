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
import androidx.compose.ui.unit.dp

/** The short edge of a normal key defines one physical gap for the entire grid. */
internal data class KeyboardKeySpacingScale(val value: Float = 1f)

internal fun keyboardKeySpacingScale(cellWidthDp: Float, cellHeightDp: Float): KeyboardKeySpacingScale {
    if (!cellWidthDp.isFinite() || !cellHeightDp.isFinite() || cellWidthDp <= 0 || cellHeightDp <= 0)
        return KeyboardKeySpacingScale()
    // 2dp inset per side at a 50dp cell: gap = 8% of the short cell edge.
    // Do not clamp small floating keyboards or scale horizontal and vertical separately.
    return KeyboardKeySpacingScale(minOf(cellWidthDp, cellHeightDp) / 50f)
}

internal val LocalKeyboardKeySpacingScale = staticCompositionLocalOf { KeyboardKeySpacingScale() }

@Composable
internal fun KeyboardKeySpacingScope(
    modifier: Modifier,
    columns: Float = 10f,
    rows: Float = 4f,
    horizontalInset: Dp = 8.dp,
    verticalInset: Dp = 8.dp,
    widthFraction: Float = 1f,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val scale = keyboardKeySpacingScale(
            (maxWidth.value - horizontalInset.value) * widthFraction / columns,
            (maxHeight.value - verticalInset.value) / rows,
        )
        val normalCapEdge = scale.value * 50f * 0.92f
        CompositionLocalProvider(
            LocalKeyboardKeySpacingScale provides scale,
            LocalKeyboardKeyContentScale provides KeyboardKeyMetrics.contentScale(normalCapEdge, normalCapEdge),
        ) {
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
            start = padding.calculateStartPadding(direction) * scale.value,
            top = padding.calculateTopPadding() * scale.value,
            end = padding.calculateEndPadding(direction) * scale.value,
            bottom = padding.calculateBottomPadding() * scale.value,
        )
    }
}
