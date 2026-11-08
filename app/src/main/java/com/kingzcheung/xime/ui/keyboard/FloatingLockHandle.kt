package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 系统栏 inset 相对窗口，卡片坐标相对宿主；宿主已经避开的空间只扣一次。 */
@Composable
internal fun floatingControlViewport(width: Float, height: Float, origin: Offset, bottomInset: Float): ResizeRect {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val root = LocalView.current.rootView
    return ResizeRect(
        (bars.getLeft(density, direction) - origin.x).coerceAtLeast(0f),
        (bars.getTop(density) - origin.y).coerceAtLeast(0f),
        minOf(width, (root.width.takeIf { it > 0 }?.toFloat() ?: width) - origin.x - bars.getRight(density, direction)),
        minOf(height - bottomInset, (root.height.takeIf { it > 0 }?.toFloat() ?: height) - origin.y - bars.getBottom(density)),
    )
}

/** 保留旋转手柄绕卡片转动的轨迹，靠边时将热区收进可点击范围。 */
internal fun floatingRotateHandleRect(card: ResizeRect, degrees: Float, viewport: ResizeRect, density: Float): ResizeRect {
    val size = minOf(FLOATING_ROTATE_HANDLE_SIZE_DP * density, viewport.width, viewport.height).coerceAtLeast(1f)
    val gap = FLOATING_ROTATE_HANDLE_GAP_DP * density
    val above = card.top - gap - size >= viewport.top
    val x = if (above) card.centerX else card.centerX + 72f * density + size / 2
    val y = if (above) card.top - gap - size / 2 else card.top + 42f * density
    val (cx, cy) = rotatePoint(x, y, card.centerX, card.centerY, degrees)
    val left = (cx - size / 2).coerceIn(viewport.left, maxOf(viewport.left, viewport.right - size))
    val top = (cy - size / 2).coerceIn(viewport.top, maxOf(viewport.top, viewport.bottom - size))
    return ResizeRect(left, top, left + size, top + size)
}

internal fun Modifier.atFloatingControl(rect: ResizeRect, density: androidx.compose.ui.unit.Density): Modifier =
    absoluteOffset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })

private const val LockHoldMs = 1000

@Composable
internal fun FloatingFooterIndicator(locked: Boolean, color: Color) {
    var showLock by remember(locked) { mutableStateOf(locked) }
    LaunchedEffect(locked) {
        while (true) {
            delay(3000)
            showLock = !showLock
        }
    }
    val fade = animateFloatAsState(if (showLock) 1f else 0f, tween(280), label = "悬浮锁提示")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(FLOATING_DRAG_INDICATOR_WIDTH_DP.dp, 4.dp).testTag("floating-drag-indicator"))
        Canvas(Modifier.fillMaxSize()) {
            val mix = fade.value
            val halfBar = FLOATING_DRAG_INDICATOR_WIDTH_DP.dp.toPx() / 2f
            val halfHeight = 2.dp.toPx()
            if (mix < 1f) drawRoundRect(color.copy(alpha = color.alpha * (1f - mix)),
                Offset(center.x - halfBar, center.y - halfHeight), Size(halfBar * 2, halfHeight * 2),
                CornerRadius(halfHeight))
            if (mix > 0f) {
                val tint = color.copy(alpha = color.alpha * mix)
                val unit = 1.dp.toPx()
                val stroke = 1.4f * unit
                // The outside of the shackle and bottom of the body are exactly 6dp from the same centre.
                val archTop = center.y - 6 * unit + stroke / 2
                val archRadius = 3 * unit
                drawArc(tint, 180f, 180f, false, Offset(center.x - archRadius, archTop),
                    Size(2 * archRadius, 2 * archRadius), style = Stroke(stroke))
                for (x in listOf(center.x - archRadius, center.x + archRadius)) {
                    drawLine(tint, Offset(x, archTop + archRadius), Offset(x, center.y), strokeWidth = stroke)
                }
                drawRoundRect(tint, Offset(center.x - 4.5f * unit, center.y - unit), Size(9 * unit, 7 * unit),
                    CornerRadius(1.6f * unit))
            }
        }
    }
}

@Composable
internal fun floatingLockProgress(holding: Boolean): State<Float> =
    animateFloatAsState(if (holding) 1f else 0f,
        tween(if (holding) LockHoldMs else 220, easing = LinearEasing), label = "悬浮锁描边")

internal fun Modifier.floatingLockOutline(locked: Boolean, progress: State<Float>, color: Color) = drawWithCache {
    val left = 0.5f
    val top = 0.5f
    val right = size.width - left
    val bottom = size.height - top
    val cx = size.width / 2f
    val radius = minOf(FloatingKeyboardCardCorner.toPx() - left, cx - left, size.height / 2f - top).coerceAtLeast(0f)
    val diameter = 2f * radius
    val half = Path().apply {
        moveTo(cx, bottom)
        lineTo(left + radius, bottom)
        arcTo(Rect(left, bottom - diameter, left + diameter, bottom), 90f, 90f, false)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, left + diameter, top + diameter), 180f, 90f, false)
        lineTo(cx, top)
    }
    val measure = PathMeasure().apply { setPath(half, false) }
    val segment = Path()
    val stroke = Stroke(width = 1f)
    onDrawWithContent {
        drawContent()
        if (right <= left || bottom <= top) return@onDrawWithContent
        // Both halves use the same path length, including the rounded corners.
        fun halves(path: Path, tint: Color) {
            drawPath(path, tint, style = stroke)
            scale(-1f, 1f, center) { drawPath(path, tint, style = stroke) }
        }
        if (locked) halves(half, color.copy(alpha = color.alpha * .55f))
        if (progress.value > 0f) {
            segment.reset()
            measure.getSegment(0f, measure.length * progress.value, segment, true)
            halves(segment, color)
        }
    }
}

/** Observe the centre of the existing drag strip. Moving past slop leaves the whole drag to its usual handler. */
@Composable
internal fun Modifier.floatingFooterHold(locked: Boolean, onHolding: (Boolean) -> Unit, onToggle: () -> Unit): Modifier {
    val holding by rememberUpdatedState(onHolding)
    val toggle by rememberUpdatedState(onToggle)
    val action = if (locked) "解锁" else "锁定"
    return semantics {
        role = Role.Button
        contentDescription = "悬浮键盘底部短横条"
        stateDescription = if (locked) "已锁定" else "可移动"
        onLongClick(label = action) { toggle(); true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (abs(down.position.x - size.width / 2f) > 32.dp.toPx()) return@awaitEachGesture
            holding(true)
            try {
                val ended = withTimeoutOrNull(LockHoldMs.toLong()) {
                    do {
                        val pointer = awaitPointerEvent(PointerEventPass.Initial).changes.singleOrNull() ?: break
                        if (pointer.id != down.id || pointer.isConsumed ||
                            (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                            pointer.position.x !in 0f..size.width.toFloat() ||
                            pointer.position.y !in 0f..size.height.toFloat()) break
                        if (!pointer.pressed) {
                            // A cancelled hold only retracts the outline; a short tap still lights the resize corners.
                            if (pointer.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) pointer.consume()
                            break
                        }
                    } while (true)
                    true
                }
                if (ended == null) {
                    toggle()
                    holding(false)
                    // This finger has already acted, including if it later moves or lifts over the strip.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            } finally {
                holding(false)
            }
        }
    }
}
