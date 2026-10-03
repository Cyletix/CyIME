package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

/** Reveal inside an already measured slot: never resize the IME window or reflow its keys. */
@Composable
internal fun KeyboardPanelReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false) },
    content: @Composable () -> Unit,
) {
    visibility.targetState = visible
    val transition = updateTransition(visibility, label = "keyboard-panel")
    val fraction by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 240 else 180) },
        label = "downward-reveal",
    ) { if (it) 1f else 0f }
    if (!visibility.isIdle || visibility.currentState || visibility.targetState) {
        // Block the entire slot during transitions, including the not-yet-revealed area.
        Box(modifier) {
            // A sibling shield avoids merging the candidate list into a single clickable node.
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {})
            Box(Modifier.fillMaxSize().drawWithContent {
                clipRect(bottom = size.height * fraction) { this@drawWithContent.drawContent() }
            }) { content() }
            if (!visibility.isIdle) {
                // Invisible candidates must not be selectable while the curtain is moving.
                Box(Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) {})
            }
        }
    }
}
