package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import kotlin.math.roundToInt

@Composable
fun FloatingKeyboardContainer(
    isFloatingMode: Boolean,
    scaleFactor: Float,
    fontScaleFactor: Float = 1f,
    opacity: Float = 1f,
    offsetX: Int,
    offsetY: Int,
    minOffsetY: Int = 0,
    availableHeightDp: Int = 0,
    backgroundColor: Color = Color.Transparent,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDock: () -> Unit = {},
    onCardPositioned: (left: Int, top: Int, right: Int, bottom: Int) -> Unit = { _: Int, _: Int, _: Int, _: Int -> },
    keyboardContent: @Composable () -> Unit,
) {
    if (!isFloatingMode) {
        keyboardContent()
        return
    }

    val density = LocalDensity.current
    val screenHeightDp = availableHeightDp.takeIf { it > 0 } ?: LocalConfiguration.current.screenHeightDp

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val cardTotalHeight = maxHeight
        val horizontalTravel = (maxWidth.value * (1f - scaleFactor) / 2f).coerceAtLeast(0f)
        val minimumY = minOffsetY.coerceAtLeast(0).toFloat()
        val maxOffsetY = (screenHeightDp - cardTotalHeight.value).coerceAtLeast(minimumY)
        val safeOffsetY = offsetY.toFloat().coerceIn(minimumY, maxOffsetY)
        val positionedEdge = floatingDockEdge(offsetX.toFloat(), safeOffsetY, horizontalTravel, maxOffsetY, minOffsetY.toFloat())
        val dockGesture = remember(maxWidth, cardTotalHeight, screenHeightDp, minOffsetY) { FloatingDockGesture() }
        var isDragging by remember(dockGesture) { mutableStateOf(false) }
        var dockReady by remember(dockGesture) { mutableStateOf(false) }
        var dragX by remember(dockGesture) { mutableFloatStateOf(offsetX.toFloat()) }
        var dragY by remember(dockGesture) { mutableFloatStateOf(safeOffsetY) }
        var dragEdge by remember(dockGesture) { mutableStateOf(positionedEdge) }
        var dockEpoch by remember(dockGesture) { mutableIntStateOf(0) }
        val edge = if (isDragging) dragEdge else positionedEdge
        val effectEpoch = dockEpoch
        val dockProgress = remember(dockGesture) { Animatable(0f) }
        LaunchedEffect(isDragging, edge, dockGesture, effectEpoch) {
            dockReady = false
            dockProgress.snapTo(0f)
            if (isDragging) {
                dockGesture.update(edge, 0L)
                if (edge != null) {
                    coroutineScope {
                        launch { dockProgress.animateTo(1f, tween(FLOATING_DOCK_DURATION_MILLIS.toInt())) }
                        delay(FLOATING_DOCK_DURATION_MILLIS)
                        // 停留时间由可取消的协程延时确定，避免与动画使用不同的时钟。
                        if (effectEpoch == dockEpoch && isDragging && dragEdge == edge) {
                            dockReady = dockGesture.update(edge, FLOATING_DOCK_DURATION_MILLIS)
                        }
                    }
                }
            }
        }
        if (isDragging && edge != null) {
            val progress = dockProgress.value
            val restoredHeight = (cardTotalHeight.value - FLOATING_DRAG_BAR_HEIGHT_DP)
                .coerceIn(48f, screenHeightDp * 0.8f)
            val previewColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentSize(Alignment.BottomCenter, unbounded = true)
                    .width(maxWidth * (scaleFactor + (1f - scaleFactor) * progress))
                    .height(restoredHeight.dp)
                    .offset(x = (offsetX * (1f - progress)).dp, y = (-minimumY).dp)
                    .background(previewColor.copy(alpha = 0.12f * progress), RoundedCornerShape(16.dp))
                    .border(2.dp, Brush.horizontalGradient(listOf(
                        previewColor.copy(alpha = progress * 0.4f),
                        previewColor.copy(alpha = progress),
                        previewColor.copy(alpha = progress * 0.4f),
                    )), RoundedCornerShape(16.dp))
                    .testTag("floating-dock-preview").semantics { stateDescription = if (dockReady) "ready" else "expanding" },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(scaleFactor)
                .height(cardTotalHeight)
                .offset(x = offsetX.dp, y = (-safeOffsetY).dp)
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer {
                    alpha = opacity.coerceIn(0.3f, 1f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .testTag("floating-keyboard-card")
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    val size = coords.size
                    onCardPositioned(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + size.width).roundToInt(),
                        (pos.y + size.height).roundToInt()
                    )
                }
        ) {
            Column {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * fontScaleFactor)
                    ) {
                        keyboardContent()
                    }
                }
                DragBar(
                    backgroundColor = backgroundColor,
                    onDragStart = {
                        dockEpoch++
                        dockReady = false
                        dragX = offsetX.toFloat()
                        dragY = safeOffsetY
                        dragEdge = positionedEdge
                        dockGesture.start(dragEdge, 0L)
                        isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dxDp = with(density) { dragAmount.x.toDp().value }
                        val dyDp = with(density) { dragAmount.y.toDp().value }
                        dragX = (dragX + dxDp).coerceIn(-horizontalTravel, horizontalTravel)
                        dragY = (dragY - dyDp).coerceIn(minimumY, maxOffsetY)
                        val nextEdge = floatingDockEdge(dragX.roundToInt().toFloat(), dragY.roundToInt().toFloat(),
                            horizontalTravel, maxOffsetY, minOffsetY.toFloat())
                        if (nextEdge != dragEdge) {
                            // MOVE 与 UP 可能早于下一帧重组；必须在触摸回调中立即撤销准备态。
                            dragEdge = nextEdge
                            dockEpoch++
                            dockReady = false
                            dockGesture.update(nextEdge, 0L)
                        }
                        onDrag(dxDp, -dyDp)
                    },
                    onDragEnd = {
                        val restore = dockReady && dockGesture.release(dragEdge, FLOATING_DOCK_DURATION_MILLIS)
                        dockGesture.cancel()
                        dockEpoch++
                        isDragging = false
                        dockReady = false
                        onDragEnd()
                        if (restore) onDock()
                    },
                    onDragCancel = {
                        dockGesture.cancel()
                        dockEpoch++
                        isDragging = false
                        dockReady = false
                        onDragEnd()
                    },
                )
            }
        }

    }
}

@Composable
private fun DragBar(
    backgroundColor: Color,
    onDragStart: () -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FLOATING_DRAG_BAR_HEIGHT_DP.dp)
            .background(backgroundColor)
            .testTag("floating-drag-bar")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, amount -> currentOnDrag(change, amount) },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.6f))
        )
    }
}
