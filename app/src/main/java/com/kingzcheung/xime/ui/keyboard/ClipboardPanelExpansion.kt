package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val LocalClipboardPanelExpansion = staticCompositionLocalOf<ClipboardPanelExpansion?> { null }

internal class ClipboardPanelExpansion(
    private val bounds: ClipboardPanelBounds,
    private val scope: CoroutineScope,
    private val onExpandedChange: (Boolean) -> Unit,
) {
    val collapsedHeight: Int get() = bounds.collapsed
    var height by mutableFloatStateOf(bounds.collapsed.toFloat())
        private set
    private var expandedTarget = false
    private var animation: Job? = null
    private var drag: ClipboardPanelDrag? = null

    fun beginDrag(screenY: Float) {
        animation?.cancel()
        drag = ClipboardPanelDrag(bounds, height, screenY)
    }

    fun dragTo(screenY: Float) {
        drag?.let { height = it.heightAt(screenY) }
    }

    fun finishDrag(cancelled: Boolean = false) {
        if (drag == null) return
        drag = null
        val expand = if (cancelled) expandedTarget else bounds.shouldExpand(height)
        setExpanded(expand)
    }

    fun setExpanded(expanded: Boolean) {
        animateTo(expanded)
        onExpandedChange(expanded)
    }

    fun animateTo(expanded: Boolean) {
        if (drag != null || (expandedTarget == expanded && animation?.isActive == true)) return
        expandedTarget = expanded
        animation?.cancel()
        val target = (if (expanded) bounds.expanded else bounds.collapsed).toFloat()
        if (height == target) return
        animation = scope.launch {
            animate(height, target, animationSpec = tween(200)) { value, _ -> height = bounds.clamp(value) }
        }
    }

    fun dispose() {
        drag = null
        animation?.cancel()
    }
}

@Composable
internal fun rememberClipboardPanelExpansion(
    enabled: Boolean,
    sessionId: Long,
    bounds: ClipboardPanelBounds,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
): ClipboardPanelExpansion? {
    val scope = rememberCoroutineScope()
    val latestChange by rememberUpdatedState(onExpandedChange)
    val panel = remember(enabled, sessionId, bounds) {
        if (enabled) ClipboardPanelExpansion(bounds, scope) { latestChange(it) } else null
    }
    DisposableEffect(panel) { onDispose { panel?.dispose() } }
    LaunchedEffect(panel, expanded) {
        if (panel == null) {
            if (expanded) latestChange(false)
        } else panel.animateTo(expanded)
    }
    return panel
}

/** Attach only to panel headers; list scrolling and horizontal tab scrolling remain independent. */
internal fun Modifier.clipboardPanelExpandGesture(): Modifier = composed {
    val panel = LocalClipboardPanelExpansion.current
    if (panel == null) this else {
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        onGloballyPositioned { coordinates[0] = it }.pointerInput(panel) {
            fun screenY(localY: Float): Float? = coordinates[0]
                ?.takeIf { it.isAttached }?.positionOnScreen()?.y?.let { (it + localY) / density }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startY = screenY(down.position.y) ?: return@awaitEachGesture
                val accepted = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    ?: return@awaitEachGesture
                panel.beginDrag(startY)
                var finished = false
                try {
                    screenY(accepted.position.y)?.let(panel::dragTo)
                    finished = verticalDrag(accepted.id) { change ->
                        screenY(change.position.y)?.let(panel::dragTo)
                        change.consume()
                    }
                } finally {
                    panel.finishDrag(cancelled = !finished)
                }
            }
        }
    }
}
