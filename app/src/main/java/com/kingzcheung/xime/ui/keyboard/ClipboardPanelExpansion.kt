package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    // Only endpoint changes resize fixed cards; intermediate drag heights resize the viewport.
    var contentHeight by mutableIntStateOf(bounds.collapsed)
        private set
    var isInMotion by mutableStateOf(false)
        private set
    private var motionGeneration = 0L
    private var expandedTarget = false
    private var animation: Job? = null
    private var drag: ClipboardPanelDrag? = null

    fun beginDrag(screenY: Float) {
        motionGeneration++
        animation?.cancel()
        isInMotion = true
        drag = ClipboardPanelDrag(bounds, height, screenY)
    }

    fun dragTo(screenY: Float) {
        drag?.let { height = it.heightAt(screenY) }
    }

    fun finishDrag(cancelled: Boolean = false, velocityY: Float = 0f) {
        val finishedDrag = drag ?: return
        drag = null
        val expand = if (cancelled) expandedTarget else
            bounds.shouldExpand(height, finishedDrag.startHeight, expandedTarget, velocityY)
        setExpanded(expand)
    }

    fun setExpanded(expanded: Boolean) {
        animateTo(expanded)
        onExpandedChange(expanded)
    }

    fun animateTo(expanded: Boolean) {
        if (drag != null || (expandedTarget == expanded && animation?.isActive == true)) return
        expandedTarget = expanded
        val generation = ++motionGeneration
        animation?.cancel()
        contentHeight = if (expanded) bounds.expanded else bounds.collapsed
        val target = contentHeight.toFloat()
        if (height == target) {
            isInMotion = false
            return
        }
        isInMotion = true
        animation = scope.launch {
            try {
                animate(height, target, animationSpec = keyboardPanelMotion(expanded)) { value, _ -> height = bounds.clamp(value) }
            } finally {
                // An interrupted animation must not unfreeze a new drag or settling animation.
                if (generation == motionGeneration && drag == null) isInMotion = false
            }
        }
    }

    fun dispose() {
        motionGeneration++
        drag = null
        animation?.cancel()
        isInMotion = false
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

/** Header dragging uses screen coordinates even while the IME window changes height. */
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
                // Keep the samples before touch slop too: a short flick may have only
                // one move left after activation, which is insufficient for velocity fitting.
                val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, Offset(0f, startY)) }
                var dragging = false
                var finished = false
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val y = screenY(change.position.y) ?: break
                        velocity.addPosition(change.uptimeMillis, Offset(0f, y))
                        if (!dragging && change.pressed && abs(y - startY) > viewConfiguration.touchSlop / density) {
                            panel.beginDrag(startY)
                            dragging = true
                        }
                        if (dragging) {
                            panel.dragTo(y)
                            change.consume()
                        }
                        if (!change.pressed) { finished = true; break }
                        if (!dragging) {
                            val finalChange = awaitPointerEvent(PointerEventPass.Final)
                                .changes.firstOrNull { it.id == down.id }
                            if (finalChange == null || finalChange.isConsumed) break
                        }
                    }
                } finally {
                    if (dragging) panel.finishDrag(cancelled = !finished, velocityY = velocity.calculateVelocity().y)
                }
            }
        }
    }
}


/** Only a downward drag starting at the top of an expanded list may resize it.
 * Observe Initial without consuming until the direction is decided, so card taps,
 * long presses, horizontal categories and ordinary list scrolls retain ownership.
 */
internal fun Modifier.clipboardPanelCollapseGesture(enabled: Boolean, atTop: () -> Boolean): Modifier = composed {
    val panel = LocalClipboardPanelExpansion.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAtTop by rememberUpdatedState(atTop)
    if (panel == null) this else {
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        onGloballyPositioned { coordinates[0] = it }.pointerInput(panel) {
            fun screenPoint(local: Offset): Offset? = coordinates[0]?.takeIf { it.isAttached }
                ?.positionOnScreen()?.let { (it + local) / density }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!currentEnabled || !currentAtTop() || panel.height <= panel.collapsedHeight) return@awaitEachGesture
                val start = screenPoint(down.position) ?: return@awaitEachGesture
                val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, start) }
                var dragging = false
                var finished = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.id != down.id && it.pressed }) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || !currentEnabled) break
                        val point = screenPoint(change.position) ?: break
                        velocity.addPosition(change.uptimeMillis, point)
                        if (!dragging) {
                            // A shown long-press menu belongs to the card even if the finger later moves.
                            if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                            val delta = point - start
                            val slop = viewConfiguration.touchSlop / density
                            if (abs(delta.x) > slop || abs(delta.y) > slop) {
                                if (delta.y <= slop || delta.y <= abs(delta.x) * 1.1f || !currentAtTop()) break
                                panel.beginDrag(start.y)
                                dragging = true
                            }
                        }
                        if (dragging) {
                            panel.dragTo(point.y)
                            change.consume()
                        }
                        if (!change.pressed) { finished = true; break }
                        if (!dragging) {
                            val finalChange = awaitPointerEvent(PointerEventPass.Final)
                                .changes.firstOrNull { it.id == down.id }
                            if (finalChange == null || finalChange.isConsumed) break
                        }
                    }
                } finally {
                    if (dragging) panel.finishDrag(cancelled = !finished, velocityY = velocity.calculateVelocity().y)
                }
            }
        }
    }
}

/** Keep the covered keyboard at its normal measured size while the clipboard grows above it.
 * The slot still fills the panel, so overlays/candidate reveals retain their current bounds.
 */
internal fun Modifier.clipboardUnderlaySize(panel: ClipboardPanelExpansion?, totalHeightDp: Int): Modifier = composed {
    if (panel == null) this else {
        val density = LocalDensity.current
        val normalSlot = remember(panel, density.density) { intArrayOf(-1, -1) }
        layout { measurable, constraints ->
            if (normalSlot[0] != constraints.maxWidth) {
                normalSlot[0] = constraints.maxWidth
                val chrome = with(density) { totalHeightDp.dp.roundToPx() } - constraints.maxHeight
                normalSlot[1] = (with(density) { panel.collapsedHeight.dp.roundToPx() } - chrome).coerceAtLeast(0)
            }
            val height = normalSlot[1].coerceAtMost(constraints.maxHeight)
            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelative(0, constraints.maxHeight - height)
            }
        }
    }
}
