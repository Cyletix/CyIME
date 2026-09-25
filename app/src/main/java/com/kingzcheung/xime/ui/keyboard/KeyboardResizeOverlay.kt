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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var currentHeightDp by remember { mutableFloatStateOf(initialHeightDp.toFloat()) }
    var currentWidthDp by remember { mutableFloatStateOf(initialWidthDp.toFloat()) }
    var currentBottomPaddingDpState by remember { mutableFloatStateOf(currentBottomPaddingDp.toFloat()) }
    var floatingMode by remember(isFloatingMode) { mutableStateOf(isFloatingMode) }
    var splitMode by remember(isSplitKeyboard) { mutableStateOf(isSplitKeyboard) }
    var opacity by remember { mutableFloatStateOf(initialOpacity.coerceIn(0.3f, 1f)) }

    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(onBack = onCancel)
    }

    val currentOnGeometryChange by rememberUpdatedState(onGeometryChange)
    val currentOnReset by rememberUpdatedState(onReset)
    val currentOnPreviewOpacity by rememberUpdatedState(onOpacityChange)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val viewRect = viewRectOf(maxWidth.value * density.density, maxHeight.value * density.density)
        val previewState = LocalKeyboardResizePreviewState.current
        var dragRect by remember { mutableStateOf<ResizeRect?>(null) }
        var dragEdge by remember { mutableStateOf(ResizeHandle.NONE) }
        val frame = dragRect ?: previewState.rect ?: viewRect
        val currentPreviewRect by rememberUpdatedState(previewState.rect)
        val currentInitialPreviewRect by rememberUpdatedState(previewState.initialRect)
        val currentOnPreviewRectChange by rememberUpdatedState(previewState.onRectChange)
        val currentViewRect by rememberUpdatedState(viewRect)

        val surfaceColor = MaterialTheme.colorScheme.surface
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val outlineColor = MaterialTheme.colorScheme.outline
        val inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        val accentColor = MaterialTheme.colorScheme.primary

        // 一层只负责暗背景、边框和手势。真实键盘由同一个 previewRect 驱动。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .testTag("keyboard-resize-frame")
                .semantics { contentDescription = "拖动边框调整大小，拖动内部移动悬浮键盘" }
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val tabThickness = 6.dp.toPx()
                    val tabLength = 34.dp.toPx().coerceAtMost(frame.width / 4f)
                    val cornerPx = FloatingKeyboardCardCorner.toPx()
                    val rectSize = Size(frame.width.coerceAtLeast(1f), frame.height.coerceAtLeast(1f))

                    // 边框只画一次。所有 stroke 都向框内收半线宽，避免贴屏幕/圆角时被裁成畸形。
                    val inset = stroke / 2f
                    val borderLeft = frame.left + inset
                    val borderTop = frame.top + inset
                    val borderWidth = (frame.width - stroke).coerceAtLeast(1f)
                    val borderHeight = (frame.height - stroke).coerceAtLeast(1f)
                    if (floatingMode && previewState.rect != null) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(borderLeft, borderTop),
                            size = Size(borderWidth, borderHeight),
                            cornerRadius = CornerRadius(
                                (cornerPx - inset).coerceAtLeast(0f),
                                (cornerPx - inset).coerceAtLeast(0f),
                            ),
                            style = Stroke(stroke),
                        )
                    } else {
                        drawRect(
                            color = accentColor,
                            topLeft = Offset(borderLeft, borderTop),
                            size = Size(borderWidth, borderHeight),
                            style = Stroke(stroke),
                        )
                    }

                    // 手柄全部画在边框内侧，不跨出 Rect，因此不会被宿主裁切，也不会和圆角重复描边。
                    fun horizontalHandle(y: Float, insideSign: Float) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(
                                frame.centerX - tabLength / 2f,
                                y + insideSign * (tabThickness / 2f + stroke),
                            ),
                            size = Size(tabLength, tabThickness),
                            cornerRadius = CornerRadius(tabThickness),
                        )
                    }
                    horizontalHandle(frame.top, 1f)
                    // 悬浮键盘底部是永久移动拖条，不再画 resize 手柄。固定键盘仍保留底边调节。
                    if (!floatingMode) horizontalHandle(frame.bottom, -1f)

                    if (floatingMode && previewState.rect != null) {
                        val sideLength = tabLength.coerceAtMost(frame.height / 4f)
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(
                                frame.left + stroke,
                                frame.centerY - sideLength / 2f,
                            ),
                            size = Size(tabThickness, sideLength),
                            cornerRadius = CornerRadius(tabThickness),
                        )
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(
                                frame.right - tabThickness - stroke,
                                frame.centerY - sideLength / 2f,
                            ),
                            size = Size(tabThickness, sideLength),
                            cornerRadius = CornerRadius(tabThickness),
                        )
                    }
                }
                .pointerInput(floatingMode, isLandscape) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val hit = 26.dp.toPx()
                        val gestureViewRect = currentViewRect
                        val gestureRect = currentPreviewRect ?: dragRect ?: gestureViewRect
                        val gestureFloating = floatingMode && currentPreviewRect != null
                        dragEdge = resizeHandleAt(gestureRect, down.position, hit, gestureFloating)

                        val marginPx = 12.dp.toPx()
                        val stableBounds = ResizeRect(
                            left = gestureViewRect.left + if (gestureFloating) marginPx else 0f,
                            top = gestureViewRect.top + if (gestureFloating) marginPx else 0f,
                            right = gestureViewRect.right - if (gestureFloating) marginPx else 0f,
                            bottom = gestureViewRect.bottom - if (gestureFloating) marginPx else 0f,
                        )
                        val floatingHeightRange = floatingResizeHeightBounds(maxHeight.value.roundToInt(), isLandscape)
                        val dragBarDp = if (gestureFloating) FLOATING_DRAG_BAR_HEIGHT_DP else 0
                        val minHeightPx = ((if (gestureFloating) floatingHeightRange.first else
                            keyboardHeightBounds(maxHeight.value.roundToInt(), isLandscape).first) + dragBarDp) * density.density
                        val screenMaxHeightPx = ((if (gestureFloating) floatingHeightRange.last else
                            keyboardHeightBounds(maxHeight.value.roundToInt(), isLandscape).last) + dragBarDp) * density.density
                        val baseMinWidthPx = if (gestureFloating) {
                            maxOf(FLOATING_RESIZE_MIN_WIDTH_DP, minWidthDp.coerceAtMost(FLOATING_RESIZE_MIN_WIDTH_DP)) * density.density
                        } else gestureRect.width
                        val maxWidthPx = if (gestureFloating) {
                            minOf(stableBounds.width, maxWidthDp.toFloat() * density.density)
                        } else gestureRect.width
                        val maxAspect = floatingResizeMaxAspect(isLandscape)

                        var workingRect = gestureRect
                        var workingPadding = currentBottomPaddingDpState

                        fun applyDelta(amount: Offset) {
                            if (gestureFloating) {
                                // 横向拖动不能把键盘压成细长柱；纵向拖动也不能超过当前宽度允许的高宽比。
                                // 只收紧当前轴的边界，不做等比缩放，因此“拖哪条边就只动哪条边”的语义不变。
                                val proposedWidth = when (dragEdge) {
                                    ResizeHandle.LEFT, ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT ->
                                        (workingRect.width - amount.x).coerceAtLeast(1f)
                                    ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT ->
                                        (workingRect.width + amount.x).coerceAtLeast(1f)
                                    else -> workingRect.width
                                }
                                val proposedHeight = when (dragEdge) {
                                    ResizeHandle.TOP, ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT ->
                                        (workingRect.height - amount.y).coerceAtLeast(1f)
                                    ResizeHandle.BOTTOM, ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT ->
                                        (workingRect.height + amount.y).coerceAtLeast(1f)
                                    else -> workingRect.height
                                }
                                val dynamicMinWidthPx = maxOf(baseMinWidthPx, proposedHeight / maxAspect)
                                    .coerceAtMost(maxWidthPx)
                                val dynamicMaxHeightPx = minOf(screenMaxHeightPx, proposedWidth * maxAspect)
                                    .coerceAtLeast(minHeightPx)
                                workingRect = workingRect.dragBy(
                                    handle = dragEdge,
                                    dx = amount.x,
                                    dy = amount.y,
                                    bounds = stableBounds,
                                    minWidth = dynamicMinWidthPx,
                                    minHeight = minHeightPx,
                                    maxWidth = maxWidthPx,
                                    maxHeight = dynamicMaxHeightPx,
                                )
                                dragRect = workingRect
                                currentOnPreviewRectChange(workingRect)
                                currentWidthDp = (workingRect.width / density.density)
                                currentHeightDp = (workingRect.height / density.density) - FLOATING_DRAG_BAR_HEIGHT_DP
                            } else {
                                when (dragEdge) {
                                    ResizeHandle.TOP -> {
                                        workingRect = workingRect.dragBy(
                                            handle = ResizeHandle.TOP,
                                            dx = 0f,
                                            dy = amount.y,
                                            bounds = stableBounds,
                                            minWidth = workingRect.width,
                                            minHeight = minHeightPx,
                                            maxWidth = workingRect.width,
                                            maxHeight = screenMaxHeightPx,
                                        )
                                        dragRect = workingRect
                                        currentOnPreviewRectChange(workingRect)
                                        currentHeightDp = workingRect.height / density.density
                                    }
                                    ResizeHandle.BOTTOM -> {
                                        // 固定键盘底边代表“底部留白”：拖上去增加留白，键盘整体上移，尺寸不变。
                                        val deltaDp = -amount.y / density.density
                                        workingPadding = (workingPadding + deltaDp).coerceIn(0f, 80f)
                                        currentBottomPaddingDpState = workingPadding
                                        val shiftPx = -deltaDp * density.density
                                        val moved = workingRect.translated(0f, shiftPx).coerceInside(stableBounds)
                                        workingRect = moved
                                        dragRect = moved
                                        currentOnPreviewRectChange(moved)
                                    }
                                    else -> Unit
                                }
                            }
                        }

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
                        dragRect = null
                    }
                },
        )

        // 控制面板回到键盘中间：尺寸收紧、四个动作等宽，跟随当前主题。
        val panelWidthDp = minOf(232f, (maxWidth.value - 16f).coerceAtLeast(188f)).dp
        val panelHeightDp = 108.dp
        val panelWidthPx = with(density) { panelWidthDp.toPx() }
        val panelHeightPx = with(density) { panelHeightDp.toPx() }
        val marginPx = with(density) { 8.dp.toPx() }
        val maxPanelX = (viewRect.right - panelWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxPanelY = (viewRect.bottom - panelHeightPx - marginPx).coerceAtLeast(marginPx)
        val panelX = (frame.centerX - panelWidthPx / 2f).coerceIn(marginPx, maxPanelX)
        val panelY = (frame.centerY - panelHeightPx / 2f).coerceIn(marginPx, maxPanelY)

        Box(
            modifier = Modifier
                .offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }
                .width(panelWidthDp)
                .height(panelHeightDp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor.copy(alpha = 0.96f))
                .border(1.dp, outlineColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .testTag("keyboard-resize-controls"),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "透明度 ${((1f - opacity) * 100).roundToInt()}%",
                    modifier = Modifier.fillMaxWidth(),
                    color = onSurfaceColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
                    Slider(
                        value = 1f - opacity,
                        onValueChange = {
                            opacity = 1f - it
                            currentOnPreviewOpacity(opacity)
                        },
                        valueRange = 0f..0.7f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = inactiveTrackColor,
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp).testTag("keyboard-opacity-slider"),
                    )
                }

                @Composable
                fun EqualActionButton(
                    modifier: Modifier,
                    enabled: Boolean = true,
                    text: String? = null,
                    description: String,
                    icon: @Composable () -> Unit,
                    onClick: () -> Unit,
                ) {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = enabled,
                        modifier = modifier.height(36.dp).semantics { contentDescription = description },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (enabled) outlineColor else outlineColor.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            contentColor = onSurfaceColor,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            disabledContentColor = onSurfaceColor.copy(alpha = 0.35f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        icon()
                        if (text != null) {
                            Spacer(Modifier.width(3.dp))
                            Text(text, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EqualActionButton(
                        modifier = Modifier.weight(1f),
                        description = "重置",
                        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    ) {
                        currentBottomPaddingDpState = 0f
                        opacity = 1f
                        currentOnPreviewOpacity(opacity)
                        currentInitialPreviewRect?.let { initial ->
                            currentOnPreviewRectChange(initial)
                            currentWidthDp = initial.width / density.density
                            currentHeightDp = if (floatingMode) {
                                initial.height / density.density - FLOATING_DRAG_BAR_HEIGHT_DP
                            } else {
                                initial.height / density.density
                            }
                        } ?: currentOnReset(defaultHeightDp)
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f).testTag("keyboard-resize-floating-button"),
                        enabled = onFloatingModeChange != null,
                        text = if (floatingMode) "固定" else "悬浮",
                        description = "悬浮键盘",
                        icon = {
                            Icon(
                                if (floatingMode) Icons.Default.Keyboard else Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) {
                        floatingMode = !floatingMode
                        onFloatingModeChange?.invoke(floatingMode)
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f).testTag("keyboard-resize-split-button"),
                        enabled = onSplitKeyboardChange != null && splitKeyboardSupported,
                        text = if (splitMode) "完整" else "分体",
                        description = "分体键盘",
                        icon = {
                            Icon(
                                if (splitMode) Icons.Default.Keyboard else Icons.Default.HorizontalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) {
                        splitMode = !splitMode
                        onSplitKeyboardChange?.invoke(splitMode)
                    }

                    EqualActionButton(
                        modifier = Modifier.weight(1f),
                        description = "确认",
                        icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    ) {
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
                            currentHeightDp = finalRect.height / density.density
                        }
                        onConfirm(
                            currentHeightDp.roundToInt(),
                            currentBottomPaddingDpState.roundToInt(),
                            floatingMode,
                            opacity,
                        )
                    }
                }
            }
        }
    }
}

/** 调节会话唯一预览矩形；真实键盘、边框、命中全部使用它。 */
internal data class KeyboardResizePreviewState(
    val rect: ResizeRect? = null,
    val initialRect: ResizeRect? = null,
    val onRectChange: (ResizeRect) -> Unit = {},
)

internal val LocalKeyboardResizePreviewState = compositionLocalOf { KeyboardResizePreviewState() }

// 兼容旧测试/调用方；新的 resize 不再依赖测量回读的 cardRect。
internal val LocalKeyboardResizeCardRect = compositionLocalOf<ResizeRect?> { null }
