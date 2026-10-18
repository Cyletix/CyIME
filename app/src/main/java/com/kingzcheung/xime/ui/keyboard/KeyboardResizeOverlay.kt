package com.kingzcheung.xime.ui.keyboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun KeyboardResizeOverlay(
    initialHeightDp: Int,
    defaultHeightDp: Int,
    currentBottomPaddingDp: Int,
    isFloatingMode: Boolean,
    initialWidthDp: Int = 0,
    minWidthDp: Int = 200,
    maxWidthDp: Int = Int.MAX_VALUE,
    initialOpacity: Float = 1f,
    onHeightChange: (Int) -> Unit,
    onWidthChange: (Int) -> Unit = {},
    onGeometryChange: (ResizeGeometry) -> Unit = {},
    onBottomPaddingChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: (Int) -> Unit,
    onConfirm: (Int, Int, Boolean, Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onFloatingModeChange: ((Boolean) -> Unit)? = null,
    isSplitKeyboard: Boolean = false,
    onSplitKeyboardChange: ((Boolean) -> Unit)? = null,
    splitKeyboardSupported: Boolean = true,
    onPositionDrag: ((Float, Float) -> Unit)? = null,
    onPositionDragEnd: (() -> Unit)? = null,
    defaultSize: ProtectedKeyboardSize? = null,
    /** 吸附到特定位置时的振动；为空时用系统触感。 */
    onSnapFeedback: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val currentSnapFeedback by rememberUpdatedState(onSnapFeedback ?: {
        hostView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    })
    val roundedBottom = rememberRoundedKeyboardBottom()
    val frostedGlassEnabled = rememberKeyboardInputPreferences().frostedGlass.enabled
    val opacityEditingState = LocalKeyboardResizeOpacityEditing.current ?: remember { mutableStateOf(false) }
    var editingOpacity by opacityEditingState
    val opacityInteraction = remember { MutableInteractionSource() }
    val draggingOpacity by opacityInteraction.collectIsDraggedAsState()
    val pressingOpacity by opacityInteraction.collectIsPressedAsState()
    // 手指在滑条上时只留滑条本身，看到的就是真实透明效果；松手后再给出返回/完成。
    val adjustingOpacity = draggingOpacity || pressingOpacity
    LaunchedEffect(draggingOpacity) { if (draggingOpacity) editingOpacity = true }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val frameStyle = remember(configuration.smallestScreenWidthDp) {
        resizeFrameStyle(configuration.smallestScreenWidthDp)
    }

    var currentHeightDp by remember { mutableFloatStateOf(initialHeightDp.toFloat()) }
    var currentWidthDp by remember { mutableFloatStateOf(initialWidthDp.toFloat()) }
    var currentBottomPaddingDpState by remember { mutableFloatStateOf(currentBottomPaddingDp.toFloat()) }
    var floatingMode by remember(isFloatingMode) { mutableStateOf(isFloatingMode) }
    var splitMode by remember(isSplitKeyboard) { mutableStateOf(isSplitKeyboard) }
    // 分体两半贴边：开启前的宽度留着，关闭分体时还原。
    var rectBeforeSplit by remember { mutableStateOf<ResizeRect?>(null) }
    val splitActive = splitMode && splitKeyboardSupported
    var opacity by remember { mutableFloatStateOf(initialOpacity.coerceIn(0.3f, 1f)) }

    if (LocalOnBackPressedDispatcherOwner.current != null) {
        // The IME consumes system Back as cancellation of the entire resize transaction.
        // Only the visible return button keeps the draft and returns to size controls.
        BackHandler(onBack = onCancel)
    }

    val currentOnGeometryChange by rememberUpdatedState(onGeometryChange)
    val currentOnReset by rememberUpdatedState(onReset)
    val currentOnPreviewOpacity by rememberUpdatedState(onOpacityChange)

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        val viewRect = viewRectOf(maxWidth.value * density.density, maxHeight.value * density.density)
        val previewState = LocalKeyboardResizePreviewState.current
        var dragRect by remember(floatingMode, maxWidth, maxHeight) { mutableStateOf<ResizeRect?>(null) }
        var dragEdge by remember { mutableStateOf(ResizeHandle.NONE) }
        val frame = dragRect ?: previewState.rect ?: viewRect
        val dragAreaHeightDp = floatingDragAreaHeightDp(previewState.bottomPaddingDp,
            previewState.floatingBottomClearanceDp, frame.height / density.density)
        val dragAreaHeightPx = dragAreaHeightDp * density.density
        val currentPreviewRect by rememberUpdatedState(previewState.rect)
        val currentInitialPreviewRect by rememberUpdatedState(previewState.initialRect)
        val currentOnPreviewRectChange by rememberUpdatedState(previewState.onRectChange)
        val currentViewRect by rememberUpdatedState(viewRect)
        val currentPreviewState by rememberUpdatedState(previewState)
        fun splitGapFraction(rect: ResizeRect): Float = SplitKeyboardGeometry.resolvedGap(
            previewState.splitGapFraction, rect.width / density.density,
            SplitKeyboardGeometry.gutterDp(floatingMode), previewState.splitPitchDp)
        // 中缝两条内侧竖边：优先用实际渲染出的中缝，测量前按居中对称估算。
        val splitGapPx = if (splitActive) splitGapFraction(frame) * frame.width else 0f
        val measuredHole = LocalSplitKeyboardGap.current?.hole(0f)?.takeIf { splitActive }
        val splitInnerLeft = measuredHole?.let { it.left - overlayOrigin.x } ?: (frame.centerX - splitGapPx / 2f)
        val splitInnerRight = measuredHole?.let { it.right - overlayOrigin.x } ?: (frame.centerX + splitGapPx / 2f)
        val currentSplitEdges by rememberUpdatedState(splitInnerLeft to splitInnerRight)
        val currentSplitGapFraction by rememberUpdatedState<(ResizeRect) -> Float>(::splitGapFraction)

        // 调节界面的面板、按钮和滑条使用键盘本身的配色（键帽色、文字色、强调色），
        // 与键盘设计一致；没有键盘配色时（预览、测试）退回 Material 主题色。
        val palette = com.kingzcheung.xime.ui.theme.LocalKeyboardPalette.current
        val surfaceColor = palette?.key?.copy(alpha = 1f) ?: MaterialTheme.colorScheme.surface
        val onSurfaceColor = palette?.text ?: MaterialTheme.colorScheme.onSurface
        val outlineColor = palette?.text?.copy(alpha = .45f) ?: MaterialTheme.colorScheme.outline
        val inactiveTrackColor = onSurfaceColor.copy(alpha = 0.22f)
        val accentColor = palette?.accent ?: MaterialTheme.colorScheme.primary
        // 透明度控件使用键帽色和弱描边，始终与键盘预览保持对比。
        val outlineVariantColor = onSurfaceColor.copy(alpha = 0.18f)
        val panelShape = remember(floatingMode, roundedBottom) {
            keyboardPanelShape(floatingMode, roundedBottom)
        }

        // This full-size pointer layer catches uncovered hits below the controls.
        // Do not consume down events on their common parent: that cancels Slider drags.
        // 一层只负责暗背景、边框和手势。真实键盘由同一个 previewRect 驱动。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (editingOpacity) Color.Transparent else Color.Black.copy(alpha = 0.25f))
                .testTag(if (editingOpacity) "keyboard-opacity-backdrop" else "keyboard-resize-frame")
                .semantics { contentDescription = if (editingOpacity) "透明度调整" else "拖动边框调整大小，拖动内部移动悬浮键盘" }
                .drawBehind {
                    if (editingOpacity) return@drawBehind
                    val stroke = frameStyle.strokeDp.dp.toPx()
                    val tabThickness = frameStyle.handleThicknessDp.dp.toPx()
                    // 描边和四边手柄共用内侧中心线。
                    val handleInset = frameStyle.insetDp.dp.toPx()
                    val tabLength = frameStyle.handleLengthDp.dp.toPx().coerceAtMost(frame.width / 4f)
                    val rectSize = Size(frame.width.coerceAtLeast(1f), frame.height.coerceAtLeast(1f))

                    // 正常面板与调节态共用用户选择的底部轮廓；悬浮始终保留四角圆角。
                    // Glass already has its own backdrop in the live keyboard preview.
                    // Keep the dimming and resize handles without painting over that material.
                    if (!frostedGlassEnabled) {
                        // 底部只显示真实移动条，不能被调节态的面板涂层盖住。
                        clipRect(bottom = if (floatingMode) frame.bottom - dragAreaHeightPx else size.height) {
                            clipRect(splitInnerLeft, frame.top, splitInnerRight, frame.bottom, ClipOp.Difference) {
                                translate(frame.left, frame.top) {
                                    drawOutline(
                                        panelShape.createOutline(rectSize, layoutDirection, this),
                                        color = surfaceColor.copy(alpha = 0.88f),
                                    )
                                }
                            }
                        }
                    }
                    val innerLeft = frame.left + handleInset
                    val innerTop = frame.top + handleInset
                    val innerRight = frame.right - handleInset
                    val innerBottom = frame.bottom - handleInset
                    val innerSize = Size(
                        (innerRight - innerLeft).coerceAtLeast(1f),
                        (innerBottom - innerTop).coerceAtLeast(1f),
                    )
                    val cornerRadius = frameStyle.cornerRadiusDp.dp.toPx().coerceAtMost(minOf(innerSize.width, innerSize.height) / 2f)
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.78f),
                        topLeft = Offset(innerLeft, innerTop),
                        size = innerSize,
                        cornerRadius = CornerRadius(cornerRadius),
                        style = Stroke(stroke),
                    )
                    // 强调四边的拖动位置，同时保持短条与整圈圆弧描边相接。
                    fun horizontalHandle(y: Float) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(
                                frame.centerX - tabLength / 2f,
                                y - tabThickness / 2f,
                            ),
                            size = Size(tabLength, tabThickness),
                            cornerRadius = CornerRadius(tabThickness),
                        )
                    }
                    horizontalHandle(innerTop)
                    horizontalHandle(innerBottom)
                    resizeCornerArcs(ResizeRect(innerLeft, innerTop, innerRight, innerBottom), cornerRadius)
                        .forEach { arc ->
                            drawArc(accentColor, arc.startAngle, 90f, useCenter = false,
                                topLeft = arc.topLeft, size = Size(cornerRadius * 2, cornerRadius * 2),
                                style = Stroke(tabThickness, cap = StrokeCap.Round))
                        }
                    val sideLength = tabLength.coerceAtMost(frame.height / 4f)
                    fun verticalHandle(x: Float) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(x - tabThickness / 2f, frame.centerY - sideLength / 2f),
                            size = Size(tabThickness, sideLength),
                            cornerRadius = CornerRadius(tabThickness),
                        )
                    }
                    if (splitGapPx > 0f) {
                        // 分体：外侧贴边不可拖，改为拖内侧两条竖边对称调整中缝。
                        listOf(splitInnerLeft, splitInnerRight).forEach { x ->
                            drawLine(accentColor.copy(alpha = 0.78f), Offset(x, innerTop), Offset(x, innerBottom), stroke)
                            verticalHandle(x)
                        }
                    } else {
                        verticalHandle(innerLeft)
                        verticalHandle(innerRight)
                    }
                }
                .pointerInput(editingOpacity, floatingMode, isLandscape, maxWidth, maxHeight, splitActive) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (editingOpacity) {
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        val hit = RESIZE_EDGE_HIT_DP.dp.toPx()
                        val cornerHit = RESIZE_CORNER_HIT_DP.dp.toPx()
                        val gestureViewRect = currentViewRect
                        val gestureRect = currentPreviewRect ?: dragRect ?: gestureViewRect
                        val gestureFloating = floatingMode && currentPreviewRect != null
                        dragEdge = resizeHandleAt(gestureRect, down.position, hit, gestureFloating, cornerHit)
                        // 分体：内侧竖边优先；外侧贴边，左右边与四角只保留高度方向。
                        var splitEdge = 0
                        val splitStartGap = if (splitActive) currentSplitGapFraction(gestureRect) * gestureRect.width else 0f
                        if (splitActive) {
                            val (edgeLeft, edgeRight) = currentSplitEdges
                            val toLeft = kotlin.math.abs(down.position.x - edgeLeft)
                            val toRight = kotlin.math.abs(down.position.x - edgeRight)
                            if (down.position.y in gestureRect.top..gestureRect.bottom && minOf(toLeft, toRight) <= hit) {
                                splitEdge = if (toLeft <= toRight) -1 else 1
                            }
                            dragEdge = when (dragEdge) {
                                ResizeHandle.LEFT, ResizeHandle.RIGHT -> ResizeHandle.NONE
                                ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> ResizeHandle.TOP
                                ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> ResizeHandle.BOTTOM
                                else -> dragEdge
                            }
                            if (splitEdge != 0) dragEdge = ResizeHandle.NONE
                        }

                        val session = currentPreviewState
                        val stableBounds = session.bounds ?: gestureViewRect
                        val fixedRange = session.fixedHeightRange ?:
                            keyboardHeightBounds(configuration.screenHeightDp, isLandscape)
                        // 框外不是移动区域；框内空白和底部拖条仍可移动，控件自己消费触摸。
                        if (dragEdge == ResizeHandle.NONE && splitEdge == 0 &&
                            (down.position.x < gestureRect.left || down.position.x > gestureRect.right ||
                                down.position.y < gestureRect.top || down.position.y > gestureRect.bottom)) {
                            return@awaitEachGesture
                        }
                        val floatingHeightRange = floatingResizeHeightBounds(maxHeight.value.roundToInt(), isLandscape)
                        val dragBarDp = if (gestureFloating) FLOATING_DRAG_BAR_HEIGHT_DP else 0
                        // 手机基准下限与可用区域取小：平板横屏不允许因为"按比例算"反而比手机更小。
                        val availableWidthDp = (stableBounds.width / density.density).roundToInt()
                        val availableHeightDp = (stableBounds.height / density.density).roundToInt()
                        val minContentHeightDp = minOf(
                            floatingHeightRange.first,
                            floatingResizeMinHeightDp(availableHeightDp - dragBarDp),
                        )
                        val minHeightPx = ((if (gestureFloating) minContentHeightDp else
                            fixedRange.first + session.bottomPaddingDp) + dragBarDp) * density.density
                        val screenMaxHeightPx = ((if (gestureFloating) floatingHeightRange.last else
                            fixedRange.last + session.bottomPaddingDp) + dragBarDp) * density.density
                        val baseMinWidthPx = if (gestureFloating) {
                            maxOf(
                                floatingResizeMinWidthDp(availableWidthDp),
                                minWidthDp.coerceAtMost(availableWidthDp.coerceAtLeast(1)),
                            ).toFloat() * density.density
                        } else gestureRect.width
                        val maxWidthPx = if (gestureFloating) {
                            minOf(stableBounds.width, maxWidthDp.toFloat() * density.density)
                        } else gestureRect.width
                        val aspect = session.aspectLimits
                        val squareSpacing = com.kingzcheung.xime.settings.KeysConfigHelper.getKeyboardKeyConfig()
                            .spacingFor(session.squareSnap?.section ?: "qwerty")

                        var workingRect = gestureRect
                        var rawResizeRect = gestureRect
                        val fixedMove = FixedKeyboardMoveGesture(gestureRect, stableBounds, 12.dp.toPx())
                        var workingPadding = session.bottomPaddingDp.toFloat()
                        var didDrag = false
                        // 进入吸附位置振动一次；离开后再次进入才再振动。
                        var engagedSnap: String? = null
                        fun snapFeedback(target: String?) {
                            if (target != null && target != engagedSnap) currentSnapFeedback()
                            engagedSnap = target
                        }
                        // 中缝吸附点：自动缝宽（标定键距）和按键正方形的缝宽。
                        val splitWidthDp = gestureRect.width / density.density
                        val splitGutterDp = SplitKeyboardGeometry.gutterDp(floatingMode)
                        val splitRange = SplitKeyboardGeometry.gapRange(splitWidthDp, splitGutterDp)
                        val splitTargets = if (splitEdge == 0) emptyMap() else buildMap {
                            put("split-default", SplitKeyboardGeometry.defaultGap(splitWidthDp, splitGutterDp, session.splitPitchDp))
                            val extra = (if (gestureFloating) dragBarDp else session.bottomPaddingDp) * density.density
                            fun difference(fraction: Float) = KeyboardSquareSnap(10f, true, false, fraction)
                                .capDifference(gestureRect, density.density, extra, gestureFloating, squareSpacing)
                            var low = splitRange.start
                            var high = splitRange.endInclusive
                            if (difference(low) >= 0f && difference(high) <= 0f) {
                                repeat(24) {
                                    val mid = (low + high) / 2f
                                    if (difference(mid) > 0f) low = mid else high = mid
                                }
                                put("split-square", (low + high) / 2f)
                            }
                        }
                        var splitDrag = 0f
                        // Snap band grows with the keyboard: 6dp on phones, up to 16dp on tablets.
                        val snapBandDp = (gestureRect.width / density.density * 0.012f).coerceIn(6f, 16f)

                        fun applyDelta(amount: Offset) {
                            didDrag = true
                            if (splitEdge != 0) {
                                splitDrag += amount.x
                                val raw = (splitStartGap + 2f * splitEdge * splitDrag) / gestureRect.width
                                var fraction = raw.coerceIn(splitRange.start, splitRange.endInclusive)
                                // Past a limit the edge stops; drop the excess so reversing moves it at once.
                                if (fraction != raw) splitDrag = (fraction * gestureRect.width - splitStartGap) / (2f * splitEdge)
                                val tolerance = snapBandDp.dp.toPx() / gestureRect.width
                                val target = splitTargets.entries.minByOrNull { kotlin.math.abs(it.value - fraction) }
                                    ?.takeIf { kotlin.math.abs(it.value - fraction) <= tolerance }
                                if (target != null) fraction = target.value
                                snapFeedback(target?.key)
                                session.onSplitGapChange(fraction)
                                return
                            }
                            if (gestureFloating) {
                                val proposed = rawResizeRect.dragBy(
                                    handle = dragEdge,
                                    dx = amount.x,
                                    dy = amount.y,
                                    bounds = stableBounds,
                                    minWidth = minOf(baseMinWidthPx, gestureRect.width),
                                    minHeight = minOf(minHeightPx, gestureRect.height),
                                    maxWidth = maxWidthPx,
                                    maxHeight = screenMaxHeightPx,
                                )
                                rawResizeRect = proposed
                                val reachable = if (dragEdge == ResizeHandle.NONE) proposed else
                                    gestureRect.resizeWithReachableWidth(proposed, dragEdge, stableBounds, aspect,
                                        density.density, dragBarDp * density.density)
                                workingRect = if (dragEdge == ResizeHandle.NONE) proposed else
                                    reachable.snapSquareKeys(dragEdge, session.squareSnap, stableBounds, density.density,
                                            dragBarDp * density.density, true, squareSpacing, thresholdDp = snapBandDp)
                                snapFeedback(if (workingRect != reachable) "square" else null)
                                dragRect = workingRect
                                currentOnPreviewRectChange(workingRect)
                                currentWidthDp = (workingRect.width / density.density)
                                currentHeightDp = (workingRect.height / density.density) - FLOATING_DRAG_BAR_HEIGHT_DP
                            } else {
                                when (dragEdge) {
                                    ResizeHandle.TOP, ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT,
                                    ResizeHandle.LEFT, ResizeHandle.RIGHT, ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> {
                                        val edge = when (dragEdge) {
                                            ResizeHandle.BOTTOM_LEFT -> ResizeHandle.LEFT
                                            ResizeHandle.BOTTOM_RIGHT -> ResizeHandle.RIGHT
                                            else -> dragEdge
                                        }
                                        val proposed = rawResizeRect.dragBy(
                                            handle = edge,
                                            dx = amount.x,
                                            dy = amount.y,
                                            bounds = stableBounds,
                                            minWidth = minOf(floatingResizeMinWidthDp(availableWidthDp) * density.density, gestureRect.width),
                                            minHeight = minOf(minHeightPx, gestureRect.height),
                                            maxWidth = stableBounds.width,
                                            maxHeight = screenMaxHeightPx,
                                        )
                                        rawResizeRect = proposed
                                        val reachable = gestureRect.resizeWithReachableWidth(proposed, edge, stableBounds, aspect,
                                            density.density, workingPadding * density.density)
                                        workingRect = reachable
                                            .snapSquareKeys(edge, session.squareSnap, stableBounds, density.density,
                                                workingPadding * density.density, false, squareSpacing, thresholdDp = snapBandDp)
                                        val snapped = workingRect.snapFixedEdgeToCenter(edge, stableBounds,
                                            floatingResizeMinWidthDp(availableWidthDp) * density.density, 12.dp.toPx())
                                        snapFeedback(when {
                                            snapped != workingRect -> "center"
                                            workingRect != reachable -> "square"
                                            else -> null
                                        })

                                        dragRect = snapped
                                        currentOnPreviewRectChange(snapped)
                                        currentHeightDp = workingRect.height / density.density - workingPadding
                                    }
                                    ResizeHandle.BOTTOM -> {
                                        // 固定键盘底边代表“底部留白”：拖上去增加留白，键盘整体上移，尺寸不变。
                                        val contentHeight = workingRect.height / density.density - workingPadding
                                        val maxPadding = minOf(80f,
                                            (stableBounds.height / density.density - contentHeight).coerceAtLeast(0f))
                                        workingPadding = (workingPadding - amount.y / density.density).coerceIn(0f, maxPadding)
                                        currentBottomPaddingDpState = workingPadding
                                        session.onBottomPaddingChange(workingPadding.roundToInt())
                                        workingRect = fixedKeyboardRect(
                                            gestureViewRect.width, gestureViewRect.height,
                                            contentHeight * density.density, workingPadding * density.density,
                                            session.fixedBottomInsetDp * density.density,
                                            workingRect.width, workingRect.centerX - gestureViewRect.width / 2f,
                                        )
                                        dragRect = workingRect
                                        currentOnPreviewRectChange(workingRect)
                                    }
                                    ResizeHandle.NONE -> {
                                        workingRect = fixedMove.move(amount.x)
                                        snapFeedback(if (fixedMove.snapped) "center" else null)
                                        dragRect = workingRect
                                        currentOnPreviewRectChange(workingRect)
                                    }
                                }
                            }
                        }

                        try {
                            val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                applyDelta(over)
                            }
                            if (start != null) {
                                drag(start.id) { change ->
                                    applyDelta(change.positionChange())
                                    change.consume()
                                }
                            }
                        } finally {
                            dragRect = null
                            if (didDrag) currentPreviewState.onDragEnd()
                        }
                    }
                },
        )

        // 控件直接占用键盘框内的可用区域；没有第二层小卡片/底板/描边。
        // 顶部工具栏、左右拖边及底部永久移动条均留出命中空间。
        val defaultControls = resizeControlsRect(frame, density.density, floatingMode,
            dragAreaHeightPx)
        val handleHitPx = RESIZE_EDGE_HIT_DP * density.density
        val controls = if (splitGapPx > 0f && splitInnerRight - splitInnerLeft - 2f * handleHitPx >= 240f * density.density)
            defaultControls.copy(left = splitInnerLeft + handleHitPx, right = splitInnerRight - handleHitPx) else defaultControls
        val controlsWidthDp = with(density) { controls.width.toDp() }
        val controlsHeightDp = with(density) { controls.height.toDp() }
        // 滑条和操作共用宽度；宽屏不拉长按钮，窄屏仍避开两侧拖动热区。
        val contentWidthDp = (minOf(controlsWidthDp, 560.dp) - 24.dp).coerceAtLeast(1.dp)
        val buttonHeight = minOf(48f, (controlsHeightDp.value / 3f).coerceAtLeast(28f)).dp
        val opacityHeight = maxOf(buttonHeight, (24f * density.fontScale).dp)
        val showButtonLabels = contentWidthDp.value >= 288f * density.fontScale
        fun confirmResize() {
            val finalRect = currentPreviewRect ?: previewState.rect
            if (floatingMode && finalRect != null) {
                val geometry = finalRect.toGeometry(
                    viewWidthPx = viewRect.width,
                    viewHeightPx = viewRect.height,
                    dragBarHeightPx = FLOATING_DRAG_BAR_HEIGHT_DP * density.density,
                    density = density.density,
                )
                currentWidthDp = geometry.widthDp.toFloat()
                currentHeightDp = geometry.heightDp.toFloat()
                currentOnGeometryChange(geometry)
            } else if (finalRect != null) {
                currentHeightDp = finalRect.height / density.density - previewState.bottomPaddingDp
                currentOnGeometryChange(finalRect.toGeometry(viewRect.width, viewRect.height, 0f, density.density))
            }
            if (splitActive) previewState.onSplitGapCommit()
            onConfirm(currentHeightDp.roundToInt(), previewState.bottomPaddingDp, floatingMode, opacity)
        }
        Box(
            modifier = Modifier
                .absoluteOffset { IntOffset(controls.left.roundToInt(), controls.top.roundToInt()) }
                .size(controlsWidthDp, controlsHeightDp)
                .testTag("keyboard-resize-controls"),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.width(contentWidthDp).fillMaxHeight().align(Alignment.Center),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 使用整组控件的可用宽度，不再对滑条单独限宽后按比例缩一次。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(opacityHeight)
                        .testTag("keyboard-resize-opacity-panel")
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (adjustingOpacity) Modifier else Modifier
                            .background(surfaceColor.copy(alpha = 0.92f))
                            .border(1.dp, outlineVariantColor, RoundedCornerShape(10.dp))),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides opacityHeight) {
                        Slider(
                            value = 1f - opacity,
                            onValueChange = {
                                editingOpacity = true
                                opacity = 1f - it
                                currentOnPreviewOpacity(opacity)
                            },
                            valueRange = 0f..0.7f,
                            interactionSource = opacityInteraction,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = inactiveTrackColor,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(opacityHeight)
                                .padding(horizontal = 6.dp)
                                .semantics { contentDescription = "键盘透明度" }
                                .testTag("keyboard-opacity-slider"),
                        )
                    }
                    if (!editingOpacity) Text(
                        text = "透明度 ${((1f - opacity) * 100).roundToInt()}%",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(IntrinsicSize.Max)
                            .clip(RoundedCornerShape(6.dp))
                            .background(surfaceColor.copy(alpha = 0.92f))
                            .clickable { editingOpacity = true }
                            .padding(horizontal = 2.dp)
                            .testTag("keyboard-opacity-overlay-label"),
                        color = onSurfaceColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }

                @Composable
                fun EqualActionButton(
                    modifier: Modifier,
                    enabled: Boolean = true,
                    text: String? = null,
                    description: String,
                    // 非空即开关：文字始终是功能名，开启时反色填充，任何配色下都能一眼分辨。
                    selected: Boolean? = null,
                    icon: @Composable () -> Unit,
                    onClick: () -> Unit,
                ) {
                    val on = selected == true
                    OutlinedButton(
                        onClick = onClick,
                        enabled = enabled,
                        modifier = modifier.height(buttonHeight).semantics {
                            contentDescription = description
                            if (selected != null) this.selected = selected
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (enabled) outlineColor else outlineColor.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (on) onSurfaceColor else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (on) surfaceColor else onSurfaceColor,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = onSurfaceColor.copy(alpha = 0.35f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        icon()
                        if (text != null && showButtonLabels) {
                            Spacer(Modifier.width(3.dp))
                            // 文字只占剩余宽度：放不下时被裁掉，而不是把整组图标和文字挤出中心。
                            Text(text, modifier = Modifier.weight(1f, fill = false), fontSize = 11.sp,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        }
                    }
                }

                if (editingOpacity) Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (adjustingOpacity) 0f else 1f)
                        .testTag("keyboard-opacity-actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqualActionButton(modifier = Modifier.weight(1f), description = "返回",
                        icon = { Text("返回") }, onClick = { editingOpacity = false })
                    EqualActionButton(modifier = Modifier.weight(1f), description = "完成",
                        icon = { Text("完成") }, onClick = ::confirmResize)
                } else Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (adjustingOpacity) 0f else 1f)
                        .testTag("keyboard-resize-actions"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqualActionButton(
                        modifier = Modifier.weight(1f),
                        text = "重置",
                        description = "重置",
                        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    ) {
                        opacity = 1f
                        currentOnPreviewOpacity(opacity)
                        if (splitActive) previewState.onSplitGapChange(-1f)
                        if (floatingMode) {
                            val initial = currentInitialPreviewRect ?: frame
                            if (defaultSize != null) {
                                val bounds = previewState.bounds ?: viewRect
                                val width = if (splitActive) bounds.width else defaultSize.width * density.density
                                val totalHeight = (defaultSize.height + FLOATING_DRAG_BAR_HEIGHT_DP) * density.density
                                val reset = ResizeRect(bounds.centerX - width / 2f, initial.bottom - totalHeight,
                                    bounds.centerX + width / 2f, initial.bottom).coerceInside(bounds)
                                currentOnPreviewRectChange(reset)
                                currentWidthDp = reset.width / density.density
                                currentHeightDp = reset.height / density.density - FLOATING_DRAG_BAR_HEIGHT_DP
                            } else currentOnPreviewRectChange(initial)
                        } else {
                            currentBottomPaddingDpState = 0f
                            previewState.onBottomPaddingChange(0)
                            val range = previewState.fixedHeightRange ?:
                                keyboardHeightBounds(configuration.screenHeightDp, isLandscape)
                            val availableWidth = (viewRect.width / density.density).roundToInt()
                            val size = defaultSize ?: protectKeyboardSize(availableWidth, availableWidth,
                                defaultHeightDp.coerceIn(range), 0, previewState.aspectLimits)
                            val height = size.height
                            currentOnPreviewRectChange(fixedKeyboardRect(
                                viewRect.width, viewRect.height, height * density.density, 0f,
                                previewState.fixedBottomInsetDp * density.density,
                                widthPx = if (splitActive) viewRect.width else size.width * density.density,
                            ))
                            currentHeightDp = height.toFloat()
                        }
                        currentPreviewState.onDragEnd()
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f).testTag("keyboard-resize-floating-button"),
                        enabled = onFloatingModeChange != null,
                        text = "悬浮",
                        description = "悬浮键盘",
                        selected = floatingMode,
                        icon = {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    ) {
                        floatingMode = !floatingMode
                        if (!floatingMode) {
                            opacity = 1f // 透明度 0%，即完全不透明。
                            currentOnPreviewOpacity(opacity)
                        }
                        onFloatingModeChange?.invoke(floatingMode)
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f).testTag("keyboard-resize-split-button"),
                        enabled = onSplitKeyboardChange != null && splitKeyboardSupported,
                        text = "分体",
                        description = "分体键盘",
                        selected = splitMode,
                        icon = {
                            Icon(
                                Icons.Default.HorizontalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    ) {
                        splitMode = !splitMode
                        onSplitKeyboardChange?.invoke(splitMode)
                        val current = currentPreviewRect ?: frame
                        if (splitMode && splitKeyboardSupported) {
                            val bounds = previewState.bounds ?: viewRect
                            rectBeforeSplit = current
                            currentOnPreviewRectChange(current.copy(left = bounds.left, right = bounds.right))
                        } else if (!splitMode) {
                            rectBeforeSplit?.let { currentOnPreviewRectChange(it.copy(top = current.top, bottom = current.bottom)) }
                            rectBeforeSplit = null
                        }
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f),
                        text = "确认",
                        description = "确认",
                        icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp)) },
                        onClick = ::confirmResize,
                    )
                }
            }
        }

        // 悬浮模式的底部整条移动拖条：最高层级、独立于控件布局，按钮与 slider 不会吃掉它的触摸；
        // 中央只移动位置；两端为四角调整热区，不被此条覆盖。
        val currentOnPositionDragEnd by rememberUpdatedState(onPositionDragEnd)
        val moveKeyboardBy by rememberUpdatedState<(Float, Float) -> Unit> { dxPx, dyPx ->
            val base = dragRect ?: currentPreviewRect
            if (base != null) {
                val moveBounds = currentPreviewState.bounds ?: currentViewRect
                val moved = base.translated(dxPx, if (floatingMode) dyPx else 0f).coerceInside(moveBounds)
                dragRect = moved
                currentOnPreviewRectChange(moved)
                // 预览矩形是调节事务的唯一位置源；确认时统一保存，避免同时改宿主偏移。
            }
        }
        if (!editingOpacity && !floatingMode) {
            val stripHeight = 26.dp
            Box(Modifier.absoluteOffset {
                IntOffset(frame.left.roundToInt(), (frame.bottom - stripHeight.toPx()).roundToInt())
            }.size(with(density) { frame.width.toDp() }, stripHeight)
                .testTag("keyboard-resize-padding-bar"), contentAlignment = Alignment.TopCenter) {
                Text("上下拖动调节底部留白", color = onSurfaceColor.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
        if (!editingOpacity && splitGapPx > 0f) {
            Box(Modifier.absoluteOffset {
                IntOffset(frame.left.roundToInt(), (frame.top + (if (floatingMode) 4.dp else 28.dp).toPx()).roundToInt())
            }.size(with(density) { frame.width.toDp() }, 28.dp)
                .testTag("keyboard-resize-split-hint"), contentAlignment = Alignment.Center) {
                Text("拖动内侧两条竖边，调整中间留空", color = onSurfaceColor, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!editingOpacity && !floatingMode && splitGapPx <= 0f) {
            val centered = kotlin.math.abs(frame.centerX - viewRect.centerX) < 0.5f
            val edgeCentered = kotlin.math.abs(frame.left - viewRect.centerX) < 0.5f ||
                kotlin.math.abs(frame.right - viewRect.centerX) < 0.5f
            val alignmentLabel = if (centered) "已居中" else if (edgeCentered) "边缘已对齐中线" else "未居中"
            Box(Modifier.absoluteOffset {
                IntOffset((frame.left + RESIZE_CORNER_HIT_DP.dp.toPx()).roundToInt(), (frame.top + 28.dp.toPx()).roundToInt())
            }.size(with(density) { (frame.width.toDp() - (RESIZE_CORNER_HIT_DP * 2).dp).coerceAtLeast(1.dp) }, 28.dp)
                .zIndex(1f).testTag("keyboard-resize-fixed-move-bar")
                .semantics { stateDescription = alignmentLabel }
                .pointerInput(floatingMode, maxWidth, maxHeight) {
                    var movement: FixedKeyboardMoveGesture? = null
                    var moveSnapped = false
                    detectDragGestures(onDragStart = {
                        val base = currentPreviewRect ?: currentViewRect
                        movement = FixedKeyboardMoveGesture(base,
                            currentPreviewState.bounds ?: currentViewRect, 12.dp.toPx())
                    }, onDragEnd = {
                        movement = null
                        dragRect = null
                        currentPreviewState.onDragEnd()
                    }, onDragCancel = {
                        movement = null
                        dragRect = null
                        currentPreviewState.onDragEnd()
                    }) { change, amount ->
                        change.consume()
                        movement?.let { gesture ->
                            val moved = gesture.move(amount.x)
                            if (gesture.snapped && !moveSnapped) currentSnapFeedback()
                            moveSnapped = gesture.snapped
                            dragRect = moved
                            currentOnPreviewRectChange(moved)
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                Text(if (centered || edgeCentered) "$alignmentLabel · 左右拖动" else "左右拖动移动键盘",
                    color = onSurfaceColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!editingOpacity && floatingMode) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset((frame.left + RESIZE_CORNER_HIT_DP.dp.toPx()).roundToInt(), (frame.bottom - dragAreaHeightPx).roundToInt())
                    }
                    .size(with(density) { (frame.width.toDp() - (RESIZE_CORNER_HIT_DP * 2).dp).coerceAtLeast(1.dp) }, dragAreaHeightDp.dp)
                    .zIndex(1f)
                    .testTag("keyboard-resize-move-bar")
                    .semantics { contentDescription = "拖动移动键盘，不改变尺寸" }
                    .pointerInput(floatingMode) {
                        detectDragGestures(
                            onDragEnd = {
                                dragRect = null
                                currentPreviewState.onDragEnd()
                                currentOnPositionDragEnd?.invoke()
                            },
                            onDragCancel = {
                                dragRect = null
                                currentPreviewState.onDragEnd()
                                currentOnPositionDragEnd?.invoke()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                moveKeyboardBy(amount.x, amount.y)
                            },
                        )
                    },
            )
        }
    }
}

/** 调节会话唯一预览矩形；真实键盘、边框、命中全部使用它。 */
internal data class KeyboardResizePreviewState(
    val aspectLimits: KeyboardAspectLimits = KeyboardAspectLimits.Letters,
    val squareSnap: KeyboardSquareSnap? = null,
    val rect: ResizeRect? = null,
    val initialRect: ResizeRect? = null,
    val onRectChange: (ResizeRect) -> Unit = {},
    val bounds: ResizeRect? = null,
    val fixedHeightRange: IntRange? = null,
    val fixedBottomInsetDp: Int = 0,
    val bottomPaddingDp: Int = 0,
    val floatingBottomClearanceDp: Float = 0f,
    val initialBottomPaddingDp: Int = 0,
    val onBottomPaddingChange: (Int) -> Unit = {},
    val onDragEnd: () -> Unit = {},
    /** 分体缝宽：本次调节的预览值或已保存值；null 表示自动。 */
    val splitGapFraction: Float? = null,
    val splitPitchDp: Float? = null,
    val onSplitGapChange: (Float) -> Unit = {},
    val onSplitGapCommit: () -> Unit = {},
)

internal val LocalKeyboardResizePreviewState = compositionLocalOf { KeyboardResizePreviewState() }

/** Shares only the editor's visibility with the real floating drag bar; geometry stays unchanged. */
internal val LocalKeyboardResizeOpacityEditing = compositionLocalOf<MutableState<Boolean>?> { null }

// 兼容旧测试/调用方；新的 resize 不再依赖测量回读的 cardRect。
internal val LocalKeyboardResizeCardRect = compositionLocalOf<ResizeRect?> { null }
