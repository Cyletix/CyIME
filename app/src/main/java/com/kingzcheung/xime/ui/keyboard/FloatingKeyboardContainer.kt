package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 悬浮键盘卡片圆角；调整覆盖层的四角示意与它同心，避免示意被圆角切掉。 */
internal val FloatingKeyboardCardCorner = 16.dp
internal val FloatingKeyboardCardShape = RoundedCornerShape(FloatingKeyboardCardCorner)
// 固定面板底边与导航区连成一体，仅保留顶部圆角；预览使用同一形状。
internal val FixedKeyboardCardShape = RoundedCornerShape(
    topStart = FloatingKeyboardCardCorner, topEnd = FloatingKeyboardCardCorner,
)
/** 固定键盘顶部圆角关掉、底部圆角开着时，只有底部两角圆。 */
private val FixedKeyboardBottomRoundedShape = RoundedCornerShape(
    bottomStart = FloatingKeyboardCardCorner, bottomEnd = FloatingKeyboardCardCorner,
)
internal fun keyboardPanelShape(isFloating: Boolean, roundedBottom: Boolean = false, roundedTop: Boolean = true) = when {
    isFloating || roundedTop && roundedBottom -> FloatingKeyboardCardShape
    roundedTop -> FixedKeyboardCardShape
    roundedBottom -> FixedKeyboardBottomRoundedShape
    else -> RectangleShape
}

/** 键盘卡片顶部两角的圆角，由裁切卡片的容器提供；候选栏的描边按它画，才不会被卡片圆角切掉。 */
internal val LocalKeyboardCardTopCorner = compositionLocalOf { FloatingKeyboardCardCorner }

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
    /** 拖动条颜色；KeyboardView 按主题背景与文字色算好传入，未传时按调色板折中。 */
    dragIndicatorColor: Color? = null,
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
    /** 悬浮卡片绕自身中心的旋转角（顺时针为正，-90..90）。 */
    rotationDegrees: Float = 0f,
    /** 调节界面里正在调的旋转角；非空时代替 [rotationDegrees]，只在图层里读，转动时不重组键盘。 */
    previewRotationState: State<Float>? = null,
    /** 非空时固定键盘顶边正中显示小把手，按住往上拉过阻力松手即变悬浮（平板）。 */
    onUndock: (() -> Unit)? = null,
    /**
     * 非空时悬浮卡片外四角亮着的角标就是调大小的把手：拖角直接改大小，松手把新的宽高和位置交给宿主保存
     * （和调节界面确认时同一种保存）。为空时角标只是位置提示。
     */
    onCornerResize: ((ResizeGeometry) -> Unit)? = null,
    /** 拖角时的高宽比保护（和调节界面同一套）；为空只限尺寸上下限。 */
    cornerResizeAspect: KeyboardAspectLimits? = null,
    /** 分体键盘两半贴着屏幕两边，拖角只改高度。 */
    cornerResizeHeightOnly: Boolean = false,
    /** 点一下亮着的角标（不拖）打开完整的调节界面：透明度、悬浮、分体、旋转都在那里。 */
    onCornerTap: (() -> Unit)? = null,
    /** 角标把手热区（窗口坐标）：露在卡片外的部分要并进输入法触摸区。角标没亮时为空表。 */
    onCornerHandlesPositioned: (List<android.graphics.Rect>) -> Unit = {},
    keyboardContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val roundedTop = rememberRoundedKeyboardTop()
    val fixedCardShape = keyboardPanelShape(false, rememberRoundedKeyboardBottom(), roundedTop)
    val fixedTopCorner = if (roundedTop) FloatingKeyboardCardCorner else 0.dp
    val dragPalette = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current
    val dragIndicatorColor = dragIndicatorColor ?: if (dragPalette != null)
        lerp(dragPalette.background.copy(alpha = 1f), dragPalette.text.copy(alpha = 1f), 0.5f)
        else lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.5f)
    // Read here so pointer updates remeasure the card without recomposing KeyboardView.
    // Keys, toolbar and the resize frame now use the same geometry before release.
    val previewRect = previewRectState?.value
    val hideDragBar = previewRect != null && LocalKeyboardResizeOpacityEditing.current?.value == true

    // 位置提示角标：切成悬浮、退出调节界面后亮一下（只有悬浮卡片画）；调节界面里有自己的边框，不亮。
    val highlight = rememberKeyboardPositionHighlight()
    val highlightColor = (dragPalette?.accent ?: MaterialTheme.colorScheme.primary).copy(alpha = 1f)
    val highlightGlass = LocalKeyboardInputPreferences.current.frostedGlass.enabled
    val resizing = previewRectState != null
    var seenFloating by remember { mutableStateOf(isFloatingMode) }
    var seenResizing by remember { mutableStateOf(resizing) }
    LaunchedEffect(isFloatingMode, resizing) {
        if (!resizing && (seenFloating != isFloatingMode || seenResizing)) highlight.trigger()
        seenFloating = isFloatingMode
        seenResizing = resizing
    }

    // 调节模式下固定键盘也放进稳定的全屏宿主里，previewRect 直接决定它的真实区域。
    // 这样控制面板可以放在键盘外面，边框也不会因为宿主高度变化而漂移。
    if (!isFloatingMode) {
        if (previewRect == null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = (resolvedWidthDp.takeIf { it > 0 }
                    ?: resolvedFixedKeyboardWidth(maxWidth.value.roundToInt(), fixedWidthDp)).dp.coerceAtMost(maxWidth)
                val travel = ((maxWidth - width) / 2f).value
                val undockPull = remember { UndockPullState() }
                Box(Modifier.align(Alignment.TopCenter)
                    .absoluteOffset(x = fixedOffsetX.toFloat().coerceIn(-travel, travel).dp)
                    .width(width).fillMaxSize().clip(fixedCardShape).testTag("fixed-keyboard-card")
                    .then(if (onUndock != null) Modifier.undockPull(undockPull, onUndock) else Modifier)
                    .onGloballyPositioned { coords ->
                        undockPull.card = coords
                        val pos = coords.positionInWindow()
                        onCardPositioned(pos.x.roundToInt(), pos.y.roundToInt(),
                            (pos.x + coords.size.width).roundToInt(), (pos.y + coords.size.height).roundToInt())
                    }) {
                    CompositionLocalProvider(LocalKeyboardCardTopCorner provides fixedTopCorner) {
                        LanguageMenuPanel { keyboardContent() }
                    }
                    if (onUndock != null) UndockHandle(Modifier.align(Alignment.TopCenter), undockPull,
                        dragIndicatorColor, highlightColor)
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
                    CompositionLocalProvider(LocalKeyboardCardTopCorner provides fixedTopCorner) { keyboardContent() }
                }
            }
        }
        return
    }

    val screenHeightDp = availableHeightDp.takeIf { it > 0 } ?: LocalConfiguration.current.screenHeightDp

    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInWindow() },
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
        // 旋转时只守顶部条和底部拖动按钮在屏内，卡片本身不再按直角整块限位；这个角度放不下就摆正显示。
        val bottomInsetDp = minOffsetY.coerceAtLeast(0).toFloat()
        fun rotatedBounds(degrees: Float) = floatingRotatedOffsetBounds(maxWidth.value, maxHeight.value,
            normalRect.width / density.density, normalRect.height / density.density, degrees, bottomInsetDp)
        val rotatedRect = if (previewRect != null) null else floatingRotatedCardRect(
            maxWidth.value * density.density, maxHeight.value * density.density, normalRect.width, normalRect.height,
            offsetX.toFloat(), offsetY.toFloat(), bottomInsetDp,
            rotationDegrees.coerceIn(-FLOATING_ROTATION_MAX_DEGREES, FLOATING_ROTATION_MAX_DEGREES), density.density)
        val restingRotation = if (rotatedRect != null) rotationDegrees.coerceIn(
            -FLOATING_ROTATION_MAX_DEGREES, FLOATING_ROTATION_MAX_DEGREES) else 0f
        val baseRect = rotatedRect ?: normalRect
        // 转着的卡片不参与贴底恢复固定：底部本来就允许略出屏，贴底不代表想变回固定。
        val destination = dockTarget?.takeIf { it.widthDp > 0 && it.contentHeightDp > 0 && rotatedRect == null }
            ?.rectInHost(maxWidth.value * density.density, maxHeight.value * density.density, density.density)
        // 拖角调大小时卡片直接按拖出来的矩形摆，松手后宿主保存的新尺寸接上，同一帧换回 baseRect。
        var cornerRect by remember { mutableStateOf<ResizeRect?>(null) }
        val cardRect = previewRect ?: cornerRect ?: baseRect
        val cardTotalHeight = with(density) { cardRect.height.toDp() }
        val dragAreaHeight = floatingDragAreaHeightDp(bottomContentPaddingDp,
            bottomKeyClearance?.value ?: 0f, cardTotalHeight.value).dp
        val horizontalTravel = ((maxWidth.value - normalRect.width / density.density) / 2f).coerceAtLeast(0f)
        val minimumY = minOffsetY.coerceIn(0, maxHeight.value.roundToInt()).toFloat()
        val maxOffsetY = (maxHeight.value - cardTotalHeight.value).coerceAtLeast(minimumY)
        val safeOffsetY = (maxHeight.value - baseRect.bottom / density.density)
        val safeOffsetX = (baseRect.centerX / density.density - maxWidth.value / 2f)
        val positionedEdge = if (rotatedRect != null) null
            else floatingDockEdge(safeOffsetX, safeOffsetY, horizontalTravel, maxOffsetY, minimumY)
        val dockGesture = remember(maxWidth, cardTotalHeight, screenHeightDp, minOffsetY) { FloatingDockGesture() }
        var isDragging by remember(dockGesture) { mutableStateOf(false) }
        var dockReady by remember(dockGesture) { mutableStateOf(false) }
        var dragX by remember(dockGesture) { mutableFloatStateOf(safeOffsetX) }
        var dragY by remember(dockGesture) { mutableFloatStateOf(safeOffsetY) }
        // 拖动期间卡片只按本地的 dragX/dragY 摆放，不每一下都写回状态重建整个键盘；
        // 松手时再把总位移一次性交给后台限位、取整、保存。
        var dragStartX by remember(dockGesture) { mutableFloatStateOf(safeOffsetX) }
        var dragStartY by remember(dockGesture) { mutableFloatStateOf(safeOffsetY) }
        val dragging = isDragging
        // 固定键盘上拉刚变成悬浮：卡片跟着那根手指走（只在摆放阶段读手指位置，不重组键盘）。
        val undockFollow = LocalKeyboardUndockFollow.current?.takeIf { previewRect == null && rotatedRect == null }
        val followBounds = floatingViewportBounds(maxWidth.value * density.density, maxHeight.value * density.density,
            minOffsetY.coerceAtLeast(0) * density.density)
        fun followShiftPx(): Offset {
            val finger = undockFollow?.finger ?: return Offset.Zero
            val target = undockFollowRect(normalRect, finger - hostOrigin,
                UNDOCK_FOLLOW_GRAB_DP * density.density, followBounds)
            return Offset(target.left - normalRect.left, target.top - normalRect.top)
        }
        fun liveShiftPx(): Offset = if (dragging)
            Offset((dragX.roundToInt() - dragStartX.roundToInt()) * density.density,
                -(dragY.roundToInt() - dragStartY.roundToInt()) * density.density)
            else followShiftPx()
        if (undockFollow != null) {
            // 松手：和拖底部拖动条一样，把整段位移一次交给后台限位、保存，再亮一下四角。
            val finishFollow by rememberUpdatedState {
                val shift = followShiftPx()
                if (shift != Offset.Zero) onDrag(shift.x / density.density, -shift.y / density.density)
                onDragEnd()
                highlight.trigger()
            }
            DisposableEffect(undockFollow) {
                val finish = { finishFollow() }
                undockFollow.onFinish = finish
                onDispose { if (undockFollow.onFinish === finish) undockFollow.onFinish = null }
            }
        }
        var dragEdge by remember(dockGesture) { mutableStateOf(positionedEdge) }
        var dockEpoch by remember(dockGesture) { mutableIntStateOf(0) }
        val edge = if (isDragging) dragEdge else positionedEdge
        val effectEpoch = dockEpoch
        val dockProgress = remember(dockGesture) { Animatable(0f) }
        LaunchedEffect(isDragging, edge, dockGesture, effectEpoch, destination) {
            dockReady = false
            dockProgress.snapTo(0f)
            if (isDragging && destination != null) {
                dockGesture.update(edge, 0L)
                if (edge != null) {
                    // Linear, so how far the outline has morphed is how long the card has rested; the
                    // moment it reaches the docked shape, releasing docks (see onDragEnd).
                    dockProgress.animateTo(1f, tween(FLOATING_DOCK_DURATION_MILLIS.toInt(), easing = LinearEasing))
                    if (effectEpoch == dockEpoch && isDragging && dragEdge == edge) {
                        dockReady = true
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
            KeyboardModeMorphOutline(Modifier.align(Alignment.TopStart), shown, progress, fixedCardShape, dockReady,
                "floating-dock-preview")
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
        // 位置提示角标：卡片外扩一圈，跟卡片一起移动、旋转；调节界面里有自己的边框，不画。
        // 亮着（含正按着角）时四角也是调大小的把手，见下面的 cornersActive。
        val cornersLit = highlight.lit || highlight.held
        if (previewRect == null) Box(
            Modifier.align(Alignment.TopStart)
                .absoluteOffset {
                    val shift = liveShiftPx()
                    val gap = PositionHighlightGap.toPx()
                    IntOffset((cardRect.left + shift.x - gap).roundToInt(), (cardRect.top + shift.y - gap).roundToInt())
                }
                .size(with(density) { cardRect.width.toDp() } + PositionHighlightGap * 2,
                    with(density) { cardRect.height.toDp() } + PositionHighlightGap * 2)
                .graphicsLayer { rotationZ = restingRotation }
                .keyboardPositionCorners(highlight, highlightColor, highlightGlass)
                .testTag("floating-position-corners")
                .semantics { stateDescription = if (cornersLit) "lit" else "off" },
        )
        Box(
            modifier = cardPlacement
                // 按未旋转的矩形上报；触摸区由后台按角度转过去。
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
                // 绕卡片中心转；调节界面的角度只在图层里读。
                .graphicsLayer { rotationZ = previewRotationState?.value ?: restingRotation }
                .clip(FloatingKeyboardCardShape)
                .graphicsLayer {
                    alpha = opacity.coerceIn(0.3f, 1f)
                    // 只有半透明时才需要离屏合成；不透明时用 Auto，少一次全卡片离屏绘制。
                    compositingStrategy = if (opacity < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .testTag("floating-keyboard-card")
        ) {
            Column {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * fontScaleFactor),
                        LocalFloatingKeyboardRotation provides restingRotation,
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
                    dragX = safeOffsetX
                    dragY = safeOffsetY
                    dragStartX = safeOffsetX
                    dragStartY = safeOffsetY
                    dragEdge = positionedEdge
                    dockGesture.start(dragEdge, 0L)
                    isDragging = true
                },
                onDrag = drag@{ change, dragAmount ->
                    change.consume()
                    if (rotatedRect != null) {
                        // 拖动量来自转过的卡片自身坐标，转回屏幕方向再移动。
                        val radians = Math.toRadians(restingRotation.toDouble())
                        val c = cos(radians).toFloat()
                        val s = sin(radians).toFloat()
                        val bounds = rotatedBounds(restingRotation)
                        dragX = bounds.clampX(dragX + (dragAmount.x * c - dragAmount.y * s) / density.density)
                        dragY = bounds.clampY(dragY - (dragAmount.x * s + dragAmount.y * c) / density.density)
                        return@drag
                    }
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
                    // Dock exactly when the preview shows it docked; the old separate timer could end a
                    // frame after the outline looked finished, and releasing then left the card floating.
                    val restore = floatingDockOnRelease(dockProgress.value, dragEdge != null, destination != null)
                    dockGesture.cancel()
                    dockEpoch++
                    // 松手时把整段位移一次交给后台，按原规矩限位、取整、保存。
                    onDrag(dragX - dragStartX, dragY - dragStartY)
                    isDragging = false
                    dockReady = false
                    onDragEnd()
                    // 变回固定时由模式切换亮框；留在悬浮就在松手处亮一下。
                    if (restore) onDock() else highlight.trigger()
                },
                onDragCancel = {
                    dockGesture.cancel()
                    dockEpoch++
                    onDrag(dragX - dragStartX, dragY - dragStartY)
                    isDragging = false
                    dockReady = false
                    onDragEnd()
                },
                // 点一下拖动条也亮出四角角标：亮着时拖角调大小、点角打开调节界面。
                onTap = { if (onCornerResize != null && rotatedRect == null) highlight.trigger() },
            )
        }

        // 四角角标把手：只在角标亮着（或正拖着角）时存在，转着的卡片不调大小。
        val cornersEnabled = onCornerResize != null && previewRect == null && rotatedRect == null && !isDragging
        val cornersActive = cornersEnabled && (cornersLit || cornerRect != null)
        val handleOutsidePx = FLOATING_CORNER_HANDLE_OUTSIDE_DP * density.density
        val handleInsidePx = FLOATING_CORNER_HANDLE_INSIDE_DP * density.density
        val currentOnCornerHandles by rememberUpdatedState(onCornerHandlesPositioned)
        // 拖动中整段手势已经归输入法窗口，触摸区不必每帧跟着改；松手后按新卡片报一次。
        if (cornerRect == null) {
            val windowRects = if (!cornersActive) emptyList() else FLOATING_CORNER_HANDLES.mapNotNull { handle ->
                cardRect.cornerHandleRect(handle, handleOutsidePx, handleInsidePx)?.let {
                    android.graphics.Rect((hostOrigin.x + it.left).roundToInt(), (hostOrigin.y + it.top).roundToInt(),
                        (hostOrigin.x + it.right).roundToInt(), (hostOrigin.y + it.bottom).roundToInt())
                }
            }
            SideEffect { currentOnCornerHandles(windowRects) }
        }
        DisposableEffect(Unit) { onDispose { currentOnCornerHandles(emptyList()) } }
        if (cornersActive) {
            val configuration = LocalConfiguration.current
            val currentCardRect by rememberUpdatedState(baseRect)
            val currentOnCornerResize by rememberUpdatedState(onCornerResize)
            val currentOnCornerTap by rememberUpdatedState(onCornerTap)
            val currentAspect by rememberUpdatedState(cornerResizeAspect)
            val currentHeightOnly by rememberUpdatedState(cornerResizeHeightOnly)
            val hostWidthPx = maxWidth.value * density.density
            val hostHeightPx = maxHeight.value * density.density
            val bottomInsetPx = minOffsetY.coerceAtLeast(0) * density.density
            FLOATING_CORNER_HANDLES.forEach { handle ->
                // 把手按拖之前的卡片摆，拖动中不跟着卡片走：节点一动，手指在它里面的位移就会被抵消。
                val box = baseRect.cornerHandleRect(handle, handleOutsidePx, handleInsidePx) ?: return@forEach
                Box(
                    Modifier.align(Alignment.TopStart)
                        .absoluteOffset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
                        .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                        .testTag("floating-corner-handle-${handle.name.lowercase()}")
                        .semantics { contentDescription = "拖动调整键盘大小，点一下打开调节界面" }
                        .pointerInput(handle, hostWidthPx, hostHeightPx, bottomInsetPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                highlight.held = true
                                val start = currentCardRect
                                val limits = floatingCornerResizeLimits(start, hostWidthPx, hostHeightPx, bottomInsetPx,
                                    configuration.screenWidthDp, configuration.screenWidthDp > configuration.screenHeightDp,
                                    density.density)
                                var raw = start
                                var resized = false
                                var tapped = false
                                fun step(dx: Float, dy: Float) {
                                    val (next, shown) = floatingCornerResizeStep(start, raw, handle, dx, dy, limits,
                                        currentAspect, currentHeightOnly, density.density)
                                    raw = next
                                    cornerRect = shown
                                    resized = true
                                }
                                try {
                                    val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        change.consume()
                                        step(over.x, over.y)
                                    }
                                    if (slop != null) {
                                        drag(slop.id) { change ->
                                            step(change.positionChange().x, change.positionChange().y)
                                            change.consume()
                                        }
                                    } else {
                                        tapped = currentEvent.changes.firstOrNull { it.id == down.id }?.pressed == false
                                    }
                                } finally {
                                    val result = cornerRect
                                    if (resized && result != null) currentOnCornerResize?.invoke(
                                        result.toGeometry(hostWidthPx, hostHeightPx, limits.dragBarPx, density.density))
                                    cornerRect = null
                                    highlight.held = false
                                    if (tapped) currentOnCornerTap?.invoke() else highlight.trigger()
                                }
                            }
                        },
                )
            }
        }

    }
}

/**
 * 悬浮、固定互相切换时的渐变框：从键盘当前位置按 [progress] 变到目标位置，满了（[ready]）松手即切换。
 * 普通悬浮贴底和调节界面里的两个方向共用这一个样子。
 */
@Composable
internal fun KeyboardModeMorphOutline(
    modifier: Modifier,
    shown: ResizeRect,
    progress: Float,
    shape: androidx.compose.ui.graphics.Shape,
    ready: Boolean,
    tag: String,
) {
    val density = LocalDensity.current
    val previewColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(shown.left.roundToInt(), shown.top.roundToInt()) }
            .size(with(density) { shown.width.toDp() }, with(density) { shown.height.toDp() })
            .background(previewColor.copy(alpha = 0.12f * progress), shape)
            .border(2.dp, Brush.horizontalGradient(listOf(
                previewColor.copy(alpha = progress * 0.4f),
                previewColor.copy(alpha = progress),
                previewColor.copy(alpha = progress * 0.4f),
            )), shape)
            .testTag(tag).semantics { stateDescription = if (ready) "ready" else "expanding" },
    )
}

/** 固定键盘顶边上拉的进度：手势在卡片上（[undockPull]），把手只负责画。 */
internal class UndockPullState {
    /** 正在往上拉（过了触摸阈值，还没松手）；只有这时才画把手。 */
    var pulling by mutableStateOf(false)
    /** 0..1：过了 [FIXED_TO_FLOATING_DEAD_ZONE_DP] 的阻力后再拉 [FIXED_TO_FLOATING_PULL_DP] 满。 */
    var progress by mutableFloatStateOf(0f)
    /** 卡片在窗口里的位置：拉满后把手指位置交给跟手时要换成窗口坐标。 */
    var card: androidx.compose.ui.layout.LayoutCoordinates? = null
}

/**
 * 固定键盘顶边正中（[UNDOCK_HANDLE_WIDTH_DP]×[UNDOCK_HANDLE_HEIGHT_DP]）按住往上拉：先过阻力，再拉满一段，满了振动一下，
 * 松手变悬浮；有外层跟手（[LocalKeyboardUndockFollow]）时拉满就变，悬浮键盘接着跟这根手指走。不满松手保持固定。
 * 在卡片的 Initial 轮里看手势、按下不消费：点按照样落到下面的工具栏按钮，只有往上拉过触摸阈值才接管（之后消费，
 * 下面的点击随之取消）。用户 10-31：一直画着的小条像三星分屏把手，又压着工具栏按钮的上沿点不动。
 */
private fun Modifier.undockPull(state: UndockPullState, onUndock: () -> Unit): Modifier = composed {
    val currentOnUndock by rememberUpdatedState(onUndock)
    val view = androidx.compose.ui.platform.LocalView.current
    val follow = LocalKeyboardUndockFollow.current
    pointerInput(state, follow) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val halfWidth = UNDOCK_HANDLE_WIDTH_DP.dp.toPx() / 2f
            if (kotlin.math.abs(down.position.x - size.width / 2f) > halfWidth ||
                down.position.y > UNDOCK_HANDLE_HEIGHT_DP.dp.toPx()) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (state.pulling && state.progress >= 1f) currentOnUndock()
                        break
                    }
                    val up = down.position.y - change.position.y
                    if (!state.pulling) {
                        // 还没拉起来：往上过阈值才接管，横向先过阈值就交给下面的按钮（不再管这一次）。
                        if (up > slop) state.pulling = true
                        else if (kotlin.math.abs(change.position.x - down.position.x) > slop) break
                        else continue
                    }
                    change.consume()
                    val before = state.progress
                    state.progress = fixedToFloatingPullProgress(up.coerceAtLeast(0f) / 1.dp.toPx())
                    if (state.progress >= 1f && before < 1f) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        // 拉满就变悬浮，悬浮键盘接着跟这根手指走，松手停在那里；没有外层跟手时照旧松手才变。
                        // 拿不到卡片位置时手指位置由外层在同一个事件里补上。
                        if (follow != null) {
                            follow.start(change.id, state.card?.takeIf { it.isAttached }?.localToWindow(change.position))
                            currentOnUndock()
                            break
                        }
                    }
                }
            } finally {
                state.pulling = false
                state.progress = 0f
            }
        }
    }
}

/**
 * 固定键盘顶边正中的把手：和悬浮键盘底部拖动条同样的 40×4dp 圆头条，平时不画，按住往上拉时才出现，
 * 随进度变长、变成强调色。手势见 [undockPull]；这里的方块只用来定位和给测试、无障碍报状态。
 */
@Composable
private fun UndockHandle(
    modifier: Modifier,
    state: UndockPullState,
    indicatorColor: Color,
    readyColor: Color,
) {
    val progress = state.progress
    Box(
        modifier = modifier
            .size(UNDOCK_HANDLE_WIDTH_DP.dp, UNDOCK_HANDLE_HEIGHT_DP.dp)
            .testTag("fixed-undock-handle")
            .semantics { stateDescription = if (progress >= 1f) "ready" else "docked" },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state.pulling) Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width((40f + 24f * progress).dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(lerp(indicatorColor, readyColor, progress))
                .testTag("fixed-undock-indicator")
        )
    }
}

internal const val UNDOCK_HANDLE_WIDTH_DP = 96
internal const val UNDOCK_HANDLE_HEIGHT_DP = 14

@Composable
private fun DragBar(
    modifier: Modifier,
    indicatorColor: Color,
    onDragStart: () -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTap: () -> Unit = {},
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("floating-drag-bar")
            // 点一下（没拖动）：自己等按下和抬起，不要求按下没被消费，拖动手势先吃掉按下时也认得出点击；
            // 拖过了触摸阈值时拖动手势会消费移动，这里就不算点击。
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (waitForUpOrCancellation() != null) currentOnTap()
                }
            }
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
