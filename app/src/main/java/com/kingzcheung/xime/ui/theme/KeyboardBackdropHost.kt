package com.kingzcheung.xime.ui.theme

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.FrostedGlassConfig

/** One window-space image frame for the keyboard, bottom padding and navigation inset. */
internal data class KeyboardBackdropFrame(val origin: Offset, val size: IntSize) {
    fun offsetIn(surfaceOrigin: Offset): Offset = origin - surfaceOrigin
}

internal fun keyboardBackdropFrame(
    hostOrigin: Offset,
    hostSize: IntSize,
    requestedHeightPx: Int,
): KeyboardBackdropFrame {
    val height = requestedHeightPx.coerceIn(0, hostSize.height.coerceAtLeast(0))
    return KeyboardBackdropFrame(
        hostOrigin + Offset(0f, (hostSize.height - height).toFloat()),
        IntSize(hostSize.width.coerceAtLeast(0), height),
    )
}

internal data class SharedKeyboardBackdrop(
    val frame: KeyboardBackdropFrame,
    val image: ImageBitmap?,
    val fallback: Color,
    val tint: Color,
    val windowRoot: View,
)

internal val LocalSharedKeyboardBackdrop = staticCompositionLocalOf<SharedKeyboardBackdrop?> { null }

/** Supplies pixels without painting the host itself: narrow keyboards retain transparent margins. */
@Composable
internal fun KeyboardBackdropHost(
    enabled: Boolean,
    background: BackgroundConfig?,
    isDark: Boolean,
    fallback: Color,
    glass: FrostedGlassConfig,
    backdropHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowRoot = LocalView.current.rootView
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = keyboardBackdropFrame(origin, size, with(density) { backdropHeight.roundToPx() })
    val image = if (enabled) {
        rememberFrostedBackgroundImage(background, isDark, fallback, glass, frame.size)
    } else null
    val backdrop = if (enabled) {
        SharedKeyboardBackdrop(
            frame = frame,
            image = image,
            fallback = fallback,
            tint = (if (isDark) Color.Black else Color.White)
                .copy(alpha = glass.normalized().backgroundOpacity),
            windowRoot = windowRoot,
        )
    } else null
    val positionModifier = if (enabled) {
        Modifier.onGloballyPositioned {
            origin = it.positionInWindow()
            size = it.size
        }
    } else Modifier

    Box(modifier.then(positionModifier)) {
        CompositionLocalProvider(LocalSharedKeyboardBackdrop provides backdrop) { content() }
    }
}
