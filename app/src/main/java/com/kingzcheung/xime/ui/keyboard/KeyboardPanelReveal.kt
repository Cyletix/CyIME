package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

internal class KeyboardPanelRevealState(
    val visibility: MutableTransitionState<Boolean>,
    val progress: State<Float>,
) {
    var boundsInWindow by mutableStateOf(Rect.Zero)
}

/** [instant] swaps a keyboard page in one frame: no curtain, and no touch shield while it moves. */
@Composable
internal fun rememberKeyboardPanelRevealState(
    visible: Boolean,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false) },
    instant: Boolean = false,
): KeyboardPanelRevealState {
    visibility.targetState = visible
    val transition = updateTransition(visibility, label = "keyboard-panel")
    val progress = transition.animateFloat(
        transitionSpec = { if (instant) snap() else keyboardPanelMotion(targetState) }, label = "downward-reveal",
    ) { if (it) 1f else 0f }
    return remember(visibility, progress) { KeyboardPanelRevealState(visibility, progress) }
}

/** Reveal inside an already measured slot: never resize the IME window or reflow its keys. */
@Composable
internal fun KeyboardPanelReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false) },
    revealState: KeyboardPanelRevealState? = null,
    content: @Composable () -> Unit,
) {
    val state = revealState ?: rememberKeyboardPanelRevealState(visible, visibility)
    val active = state.visibility
    if (!active.isIdle || active.currentState || active.targetState) {
        // Block the entire slot during transitions, including the not-yet-revealed area.
        Box(modifier) {
            // A sibling shield avoids merging the candidate list into a single clickable node.
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {})
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                state.boundsInWindow = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }.drawWithContent {
                clipRect(bottom = size.height * state.progress.value) { this@drawWithContent.drawContent() }
            }) { content() }
            if (!active.isIdle) {
                // Invisible candidates must not be selectable while the curtain is moving.
                Box(Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) {})
            }
        }
    }
}
