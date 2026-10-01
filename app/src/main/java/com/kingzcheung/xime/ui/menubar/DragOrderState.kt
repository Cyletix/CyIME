package com.kingzcheung.xime.ui.menubar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Stable item identity and viewport coordinates keep a dragged card under the finger.
 * The list moves intervening items, rather than exchanging two card contents. */
@Stable
internal class DragOrderState(val list: LazyListState, initial: List<String>, val save: (List<String>) -> Unit) {
    var order by mutableStateOf(initial)
    var dragging by mutableStateOf<String?>(null)
        private set
    private var original = initial
    private var hasMoved = false
    private var top by mutableFloatStateOf(0f)
    private var grabOffset = 0f
    val translation: Float get() = top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == dragging }?.offset?.toFloat() ?: top)

    fun start(id: String, localY: Float? = null) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        original = order; hasMoved = false; dragging = id; top = item.offset.toFloat(); grabOffset = localY ?: item.size / 2f
    }
    fun drag(delta: Float) { hasMoved = hasMoved || delta != 0f; top += delta; moveAcrossCards() }
    private fun moveAcrossCards() {
        val id = dragging ?: return
        val pointer = top + grabOffset
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && it.key in order && pointer >= it.offset && pointer <= it.offset + it.size
        } ?: return
        val from = order.indexOf(id); val to = order.indexOf(item.key)
        if (from >= 0 && to >= 0 && (to > from && pointer >= item.offset + item.size / 2f ||
                    to < from && pointer <= item.offset + item.size / 2f)) {
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    fun edgeSpeed(edge: Float): Float {
        if (dragging == null || !hasMoved) return 0f
        val info = list.layoutInfo
        val pointer = top + grabOffset
        return when {
            pointer < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - pointer) / edge).coerceIn(0f, 1f)
            pointer > info.viewportEndOffset - edge -> ((pointer - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    suspend fun scroll(pixels: Float) { list.scrollBy(pixels); moveAcrossCards() }
    fun finish() { if (dragging == null) return; dragging = null; if (order != original) save(order) }
    fun cancel() { if (dragging == null) return; dragging = null; order = original }
}

@Composable
internal fun rememberDragOrder(ids: List<String>, onSave: (List<String>) -> Unit): DragOrderState {
    val list = rememberLazyListState()
    val save by rememberUpdatedState(onSave)
    val state = remember(list) { DragOrderState(list, ids) { save(it) } }
    LaunchedEffect(ids) {
        if (state.dragging != null && ids.toSet() != state.order.toSet()) state.cancel()
        if (state.dragging == null) state.order = ids
    }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    val speed = with(LocalDensity.current) { 600.dp.toPx() }
    LaunchedEffect(state.dragging, edge) {
        if (state.dragging == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (state.dragging != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1e9f).coerceAtMost(.05f)
            previous = now
            val direction = state.edgeSpeed(edge)
            if (direction != 0f) state.scroll(direction * speed * seconds)
        }
    }
    return state
}

@Composable
internal fun LazyItemScope.dragOrderItem(state: DragOrderState, id: String, enabled: Boolean = true): Modifier {
    val active = state.dragging == id
    val offset by animateFloatAsState(if (active) state.translation else 0f,
        animationSpec = if (active) snap() else spring(), label = "cardDrop")
    return Modifier.animateItem(placementSpec = if (active) null else spring())
        .zIndex(if (active) 1f else 0f)
        .graphicsLayer { translationY = offset; shadowElevation = if (active) 8.dp.toPx() else 0f }
        .then(if (enabled) Modifier.reorderOnLongPress(
            onStart = { state.start(id, it.y) }, onDrag = state::drag,
            onEnd = { if (state.dragging == id) state.finish() },
            onCancel = { if (state.dragging == id) state.cancel() }) else Modifier)
}
