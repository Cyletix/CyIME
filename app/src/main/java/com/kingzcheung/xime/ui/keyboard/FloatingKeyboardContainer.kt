package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import kotlin.math.roundToInt

/** 悬浮键盘卡片圆角；调整覆盖层的四角示意与它同心，避免示意被圆角切掉。 */
internal val FloatingKeyboardCardCorner = 16.dp
internal val FloatingKeyboardCardShape = RoundedCornerShape(FloatingKeyboardCardCorner)
// 固定面板底边与导航区连成一体，仅保留顶部圆角；预览使用同一形状。
internal val FixedKeyboardCardShape = RoundedCornerShape(
    topStart = FloatingKeyboardCardCorner, topEnd = FloatingKeyboardCardCorner,
)
internal fun keyboardPanelShape(isFloating: Boolean, roundedBottom: Boolean = false) =
    if (isFloating || roundedBottom) FloatingKeyboardCardShape else FixedKeyboardCardShape

@Composable
internal fun FloatingKeyboardContainer(
    isFloatingMode: Boolean,
    scaleFactor: Float,
    fontScaleFactor: Float = 1f,
    opacity: Float = 1f,
    offsetX: Int,
    offsetY: Int,
    minOffsetY: Int = 0,
    availableHeightDp: Int = 0,
    /** 非调节态真实键盘内容高度；宿主可以是全屏，但卡片不能再拿宿主高度当自身高度。 */
    contentHeightDp: Int,
    backgroundColor: Color = Color.Transparent,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDock: () -> Unit = {},
    /** 调节模式中的唯一预览矩形；非空时直接决定真实卡片最终位置与尺寸。 */
    previewRectState: State<ResizeRect>? = null,
    onCardPositioned: (left: Int, top: Int, right: Int, bottom: Int) -> Unit = { _: Int, _: Int, _: Int, _: Int -> },
    fixedWidthDp: Int = 0,
    fixedOffsetX: Int = 0,
    resolvedWidthDp: Int = 0,
    dockTarget: KeyboardDockTarget? = null,
    bottomContentPaddingDp: Int = 0,
    bottomKeyClearance: State<Float>? = null,
    keyboardContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val fixedCardShape = keyboardPanelShape(false, rememberRoundedKeyboardBottom())
    val dragPalette = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current
    val dragIndicatorColor = if (dragPalette != null)
        lerp(dragPalette.background.copy(alpha = 1f), dragPalette.text.copy(alpha = 1f), 0.5f)
        else lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.5f)
    // Read here so pointer updates remeasure the card without recomposing KeyboardView.
    // Keys, toolbar and the resize frame now use the same geometry before release.
    val previewRect = previewRectState?.value
    val hideDragBar = previewRect != null && LocalKeyboardResizeOpacityEditing.current?.value == true

    // 调节模式下固定键盘也放进稳定的全屏宿主里，previewRect 直接决定它的真实区域。
    // 这样控制面板可以放在键盘外面，边框也不会因为宿主高度变化而漂移。
    if (!isFloatingMode) {
        if (previewRect == null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = (resolvedWidthDp.takeIf { it > 0 }
                    ?: resolvedFixedKeyboardWidth(maxWidth.value.roundToInt(), fixedWidthDp)).dp.coerceAtMost(maxWidth)
                val travel = ((maxWidth - width) / 2f).value
                Box(Modifier.align(Alignment.TopCenter)
                    .absoluteOffset(x = fixedOffsetX.toFloat().coerceIn(-travel, travel).dp)
                    .width(width).fillMaxSize().clip(fixedCardShape).testTag("fixed-keyboard-card")
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        onCardPositioned(pos.x.roundToInt(), pos.y.roundToInt(),
                            (pos.x + coords.size.width).roundToInt(), (pos.y + coords.size.height).roundToInt())
                    }) {
                    LanguageMenuPanel { keyboardContent() }
                }
            }
        } else {
            val previewWidth = with(density) { previewRect.width.toDp() }
            val previewHeight = with(density) { previewRect.height.toDp() }
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .absoluteOffset { IntOffset(previewRect.left.roundToInt(), previewRect.top.roundToInt()) }
                        .size(previewWidth, previewHeight)
                        .clip(fixedCardShape)
                        .testTag("fixed-keyboard-resize-preview")
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            val size = coords.size
                            onCardPositioned(
                                pos.x.roundToInt(),
                                pos.y.roundToInt(),
                                (pos.x + size.width).roundToInt(),
                                (pos.y + size.height).roundToInt(),
                            )
                        }
                ) {
                    // Resize controls own input; keep the menu host out of the live preview.
                    keyboardContent()
                }
            }
        }
        return
    }

    val screenHeightDp = availableHeightDp.takeIf { it > 0 } ?: LocalConfiguration.current.screenHeightDp

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val previewWidthDp = previewRect?.let { with(density) { it.width.toDp() } }
        val previewHeightDp = previewRect?.let { with(density) { it.height.toDp() } }
        // 宿主在悬浮模式下可以是整屏；卡片高度必须来自键盘内容高度，而不是 BoxWithConstraints.maxHeight。
        val normalRect = floatingCardRect(
            maxWidth.value * density.density, maxHeight.value * density.density,
            (resolvedWidthDp.takeIf { it > 0 }?.toFloat() ?: (maxWidth.value * scaleFactor)) * density.density,
            (contentHeightDp.coerceAtLeast(1) + FLOATING_DRAG_BAR_HEIGHT_DP) * density.density,
            offsetX * density.density, offsetY * density.density,
            minOffsetY.coerceAtLeast(0) * density.density,
        )
        val destination = dockTarget?.takeIf { it.widthDp > 0 && it.contentHeightDp > 0 }
            ?.rectInHost(maxWidth.value * density.density, maxHeight.value * density.density, density.density)
        val cardRect = previewRect ?: normalRect
        val cardTotalHeight = with(density) { cardRect.height.toDp() }
        val dragAreaHeight = floatingDragAreaHeightDp(bottomContentPaddingDp,
            bottomKeyClearance?.value ?: 0f, cardTotalHeight.value).dp
        val horizontalTravel = ((maxWidth.value - normalRect.width / density.density) / 2f).coerceAtLeast(0f)
        val minimumY = minOffsetY.coerceIn(0, maxHeight.value.roundToInt()).toFloat()
        val maxOffsetY = (maxHeight.value - cardTotalHeight.value).coerceAtLeast(minimumY)
        val safeOffsetY = (maxHeight.value - normalRect.bottom / density.density)
        val safeOffsetX = (normalRect.centerX / density.density - maxWidth.value / 2f)
        val positionedEdge = floatingDockEdge(safeOffsetX, safeOffsetY, horizontalTravel, maxOffsetY, minimumY)
        val dockGesture = remember(maxWidth, cardTotalHeight, screenHeightDp, minOffsetY) { FloatingDockGesture() }
        var isDragging by remember(dockGesture) { mutableStateOf(false) }
        var dockReady by remember(dockGesture) { mutableStateOf(false) }
        var armedDestination by remember(dockGesture) { mutableStateOf<ResizeRect?>(null) }
        var dragX by remember(dockGesture) { mutableFloatStateOf(safeOffsetX) }
        var dragY by remember(dockGesture) { mutableFloatStateOf(safeOffsetY) }
        // 拖动期间卡片只按本地的 dragX/dragY 摆放，不每一下都写回状态重建整个键盘；
        // 松手时再把总位移一次性交给后台限位、取整、保存。
        var dragStartX by remember(dockGesture) { mutableFloatStateOf(safeOffsetX) }
        var dragStartY by remember(dockGesture) { mutableFloatStateOf(safeOffsetY) }
        val dragging = isDragging
        fun liveShiftPx(): Offset = if (dragging)
            Offset((dragX.roundToInt() - dragStartX.roundToInt()) * density.density,
                -(dragY.roundToInt() - dragStartY.roundToInt()) * density.density)
            else Offset.Zero
        var dragEdge by remember(dockGesture) { mutableStateOf(positionedEdge) }
        var dockEpoch by remember(dockGesture) { mutableIntStateOf(0) }
        val edge = if (isDragging) dragEdge else positionedEdge
        val effectEpoch = dockEpoch
        val dockProgress = remember(dockGesture) { Animatable(0f) }
        LaunchedEffect(isDragging, edge, dockGesture, effectEpoch, destination) {
            dockReady = false
            armedDestination = null
            dockProgress.snapTo(0f)
            if (isDragging && destination != null) {
                dockGesture.update(edge, 0L)
                if (edge != null) {
                    coroutineScope {
                        launch { dockProgress.animateTo(1f, tween(FLOATING_DOCK_DURATION_MILLIS.toInt())) }
                        delay(FLOATING_DOCK_DURATION_MILLIS)
                        // 停留时间由可取消的协程延时确定，避免与动画使用不同的时钟。
                        if (effectEpoch == dockEpoch && isDragging && dragEdge == edge) {
                            dockReady = dockGesture.update(edge, FLOATING_DOCK_DURATION_MILLIS)
                            if (dockReady) armedDestination = destination
                        }
                    }
                }
            }
        }
        if (isDragging && edge != null && destination != null) {
            val progress = dockProgress.value
            fun between(start: Float, end: Float) = start + (end - start) * progress
            // 「变回固定」预览从卡片的实时位置插值到目标位置。
            val liveRect = liveShiftPx().let { normalRect.translated(it.x, it.y) }
            val shown = ResizeRect(between(liveRect.left, destination.left), between(liveRect.top, destination.top),
                between(liveRect.right, destination.right), between(liveRect.bottom, destination.bottom))
            val previewColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .absoluteOffset { IntOffset(shown.left.roundToInt(), shown.top.roundToInt()) }
                    .size(with(density) { shown.width.toDp() }, with(density) { shown.height.toDp() })
                    .background(previewColor.copy(alpha = 0.12f * progress), fixedCardShape)
                    .border(2.dp, Brush.horizontalGradient(listOf(
                        previewColor.copy(alpha = progress * 0.4f),
                        previewColor.copy(alpha = progress),
                        previewColor.copy(alpha = progress * 0.4f),
                    )), fixedCardShape)
                    .testTag("floating-dock-preview").semantics { stateDescription = if (dockReady) "ready" else "expanding" },
            )
        }
        // 正常态和调节态共用 TopStart 放置，不在确认时重新换坐标系。
        val cardPlacement = Modifier
            .align(Alignment.TopStart)
            // 拖动位移只在摆放 lambda 里读：手指每动一下只改位置，不重组键盘。
            .absoluteOffset {
                val shift = liveShiftPx()
                IntOffset((cardRect.left + shift.x).roundToInt(), (cardRect.top + shift.y).roundToInt())
            }
            .size(with(density) { cardRect.width.toDp() }, with(density) { cardRect.height.toDp() })
        Box(
            modifier = cardPlacement
                .clip(FloatingKeyboardCardShape)
                .graphicsLayer {
                    alpha = opacity.coerceIn(0.3f, 1f)
                    // 只有半透明时才需要离屏合成；不透明时用 Auto，少一次全卡片离屏绘制。
                    compositingStrategy = if (opacity < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
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
                        // The resize overlay owns input while the card is being measured.
                        if (previewRect != null) keyboardContent()
                        else LanguageMenuPanel { keyboardContent() }
                    }
                }
                Spacer(Modifier.fillMaxWidth().height(FLOATING_DRAG_BAR_HEIGHT_DP.dp)
                    .keyboardPanelBackground(backgroundColor))
            }
            // Keep the fixed-height Spacer above: hiding the affordance must not resize the keyboard.
            if (!hideDragBar) DragBar(
                modifier = Modifier.align(Alignment.BottomCenter).height(dragAreaHeight),
                indicatorColor = dragIndicatorColor,
                onDragStart = {
                    dockEpoch++
                    dockReady = false
                    armedDestination = null
                    dragX = safeOffsetX
                    dragY = safeOffsetY
                    dragStartX = safeOffsetX
                    dragStartY = safeOffsetY
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
                },
                onDragEnd = {
                    val restore = destination != null && armedDestination == destination && dockReady &&
                        dockGesture.release(dragEdge, FLOATING_DOCK_DURATION_MILLIS)
                    dockGesture.cancel()
                    dockEpoch++
                    // 松手时把整段位移一次交给后台，按原规矩限位、取整、保存。
                    onDrag(dragX - dragStartX, dragY - dragStartY)
                    isDragging = false
                    dockReady = false
                    armedDestination = null
                    onDragEnd()
                    if (restore) onDock()
                },
                onDragCancel = {
                    dockGesture.cancel()
                    dockEpoch++
                    onDrag(dragX - dragStartX, dragY - dragStartY)
                    isDragging = false
                    dockReady = false
                    armedDestination = null
                    onDragEnd()
                },
            )
        }

    }
}

@Composable
private fun DragBar(
    modifier: Modifier,
    indicatorColor: Color,
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
        modifier = modifier
            .fillMaxWidth()
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
        // 40dp×4dp 全圆头；颜色是键盘配色里按键文字色和键盘底色各一半混出来的实色，平时和调节时相同。
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(indicatorColor)
                .testTag("floating-drag-indicator")
        )
    }
}
