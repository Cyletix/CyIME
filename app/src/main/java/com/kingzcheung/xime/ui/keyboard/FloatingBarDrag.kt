package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 可拖动的悬浮条宿主：与实体键盘浮条同一套位置（[HardwareToolbarPosition] 比例位置）、
 * 同一套几何与吸边规则（[hardwareToolbarGeometry]、[HardwareToolbarGeometry.positionAt]）。
 * 手指移动超过触摸阈值才算拖动并取消按钮点击；松手后按比例位置记住，旋转、换输入框都保持。
 * 接收手势的外层在按下期间停在起点，只移动里面的内容，避免拖动时坐标系跟着跑。
 */
@Composable
internal fun DraggableFloatingBar(
    position: HardwareToolbarPosition,
    onPositionChange: (HardwareToolbarPosition) -> Unit,
    barWidth: Dp,
    modifier: Modifier = Modifier,
    dockAtEdge: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentPosition by rememberUpdatedState(position)
    val currentPositionChange by rememberUpdatedState(onPositionChange)
    val currentDock by rememberUpdatedState(dockAtEdge)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        var barHeightPx by remember { mutableStateOf(with(density) { 56.dp.roundToPx() }) }
        val widthPx = with(density) { barWidth.roundToPx() }
        val geometry = hardwareToolbarGeometry(constraints.maxWidth, constraints.maxHeight, widthPx, barHeightPx,
            with(density) { 8.dp.roundToPx() })
        val currentGeometry by rememberUpdatedState(geometry)
        var dragging by remember { mutableStateOf<HardwareToolbarOffset?>(null) }
        var gestureOrigin by remember { mutableStateOf<HardwareToolbarOffset?>(null) }
        val resting = geometry.offset(position)
        val animated by animateOffsetAsState((dragging ?: resting).let { Offset(it.x, it.y) },
            if (dragging != null) snap() else tween(220), label = "floating-bar-position")
        val displayed = dragging?.let { geometry.constrain(it.x, it.y) } ?: geometry.constrain(animated.x, animated.y)
        val currentDisplayed by rememberUpdatedState(displayed)
        Box(
            Modifier.absoluteOffset {
                val origin = gestureOrigin ?: displayed
                IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
            }.width(barWidth)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val origin = currentDisplayed
                        gestureOrigin = origin
                        var moved = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == down.id } ?: break
                                val distance = change.position - down.position
                                if (!change.isConsumed && (moved || distance.getDistance() > touchSlop)) {
                                    moved = true
                                    dragging = currentGeometry.constrain(origin.x + distance.x, origin.y + distance.y)
                                    change.consume() // 拖动时取消里面按钮的点击和长按
                                }
                                if (!change.pressed) {
                                    if (moved) currentPositionChange(currentGeometry.positionAt(
                                        origin.x + distance.x, origin.y + distance.y, currentPosition, snapX = currentDock))
                                    break
                                }
                            }
                        } finally { dragging = null; gestureOrigin = null }
                    }
                },
        ) {
            Box(Modifier.absoluteOffset {
                val origin = gestureOrigin ?: displayed
                IntOffset((displayed.x - origin.x).roundToInt(), (displayed.y - origin.y).roundToInt())
            }.onSizeChanged { barHeightPx = it.height }) { content() }
        }
    }
}
