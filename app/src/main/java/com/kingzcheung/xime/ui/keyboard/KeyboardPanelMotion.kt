package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import com.kingzcheung.xime.ui.theme.CyMotion
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.*

internal fun <T> keyboardPanelMotion(expanding: Boolean): TweenSpec<T> = CyMotion.panel(expanding)

internal data class HandwritingExpansion(val fraction: Float, val active: Boolean)

/** Keep the full host and its normal app inset until the closing animation has actually finished. */
@Composable
internal fun rememberHandwritingExpansion(enabled: Boolean, expanded: Boolean, session: Long): HandwritingExpansion {
    val visibility = remember(enabled, session) { MutableTransitionState(false) }
    visibility.targetState = enabled && expanded
    val transition = updateTransition(visibility, label = "handwriting-fullscreen")
    val fraction by transition.animateFloat(transitionSpec = { keyboardPanelMotion(targetState) }, label = "handwriting-size") {
        if (it) 1f else 0f
    }
    return HandwritingExpansion(if (enabled) fraction else 0f,
        enabled && (visibility.currentState || visibility.targetState || !visibility.isIdle))
}
