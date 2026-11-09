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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * 调节界面里悬浮卡片上方的一排按钮：[slot] 0 是旋转手柄，1 是挨在它右边的锁定按钮（用户 11-08：锁定和旋转按钮一样放在上方）。
 * 上方放不下（卡片顶边贴着屏幕顶）时整排挪到卡片下方，下方也放不下才收进卡片里；按钮随卡片一起转，靠边时收进可点范围。
 */
internal fun floatingRotateHandleRect(card: ResizeRect, degrees: Float, viewport: ResizeRect, density: Float, slot: Int = 0): ResizeRect {
    val size = minOf(FLOATING_ROTATE_HANDLE_SIZE_DP * density, viewport.width, viewport.height).coerceAtLeast(1f)
    val gap = FLOATING_ROTATE_HANDLE_GAP_DP * density
    val shift = slot * (size + gap)
    val above = card.top - gap - size >= viewport.top
    val below = !above && card.bottom + gap + size <= viewport.bottom
    val x = if (above || below) card.centerX + shift else card.centerX + 72f * density + size / 2 + shift
    val y = when {
        above -> card.top - gap - size / 2
        below -> card.bottom + gap + size / 2
        else -> card.top + 42f * density
    }
    val (cx, cy) = rotatePoint(x, y, card.centerX, card.centerY, degrees)
    val left = (cx - size / 2).coerceIn(viewport.left, maxOf(viewport.left, viewport.right - size))
    val top = (cy - size / 2).coerceIn(viewport.top, maxOf(viewport.top, viewport.bottom - size))
    return ResizeRect(left, top, left + size, top + size)
}

internal fun Modifier.atFloatingControl(rect: ResizeRect, density: androidx.compose.ui.unit.Density): Modifier =
    absoluteOffset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })

/** 锁定后长按底部小锁这么久解锁（用户 11-08：长按 0.5 秒）。 */
internal const val FLOATING_UNLOCK_HOLD_MS = 500

/**
 * 底部拖动条中间的标记：没锁时是短横条；锁定后换成一把小锁（用户 11-08：下方用一个小的解锁图标）。
 * 按住小锁时外圈一条弧线边转边长，满一圈解锁；没到时间松手，弧线退回去。[progress] 只在绘制时读。
 */
@Composable
internal fun FloatingFooterIndicator(locked: Boolean, color: Color, progress: State<Float>? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(FLOATING_DRAG_INDICATOR_WIDTH_DP.dp, 4.dp).testTag("floating-drag-indicator"))
        Canvas(Modifier.fillMaxSize()) {
            if (!locked) {
                val halfBar = FLOATING_DRAG_INDICATOR_WIDTH_DP.dp.toPx() / 2f
                val halfHeight = 2.dp.toPx()
                drawRoundRect(color, Offset(center.x - halfBar, center.y - halfHeight), Size(halfBar * 2, halfHeight * 2),
                    CornerRadius(halfHeight))
                return@Canvas
            }
            val unit = 1.dp.toPx()
            val stroke = 1.4f * unit
            // The outside of the shackle and bottom of the body are exactly 6dp from the same centre.
            val archTop = center.y - 6 * unit + stroke / 2
            val archRadius = 3 * unit
            drawArc(color, 180f, 180f, false, Offset(center.x - archRadius, archTop),
                Size(2 * archRadius, 2 * archRadius), style = Stroke(stroke))
            for (x in listOf(center.x - archRadius, center.x + archRadius)) {
                drawLine(color, Offset(x, archTop + archRadius), Offset(x, center.y), strokeWidth = stroke)
            }
            drawRoundRect(color, Offset(center.x - 4.5f * unit, center.y - unit), Size(9 * unit, 7 * unit),
                CornerRadius(1.6f * unit))
            val p = progress?.value ?: 0f
            if (p > 0f) {
                // 外圈弧线：一边长一边转（起点转一圈半），满一圈时解锁。
                val ring = minOf(10 * unit, size.height / 2f - stroke)
                drawArc(color, -90f + 540f * p, 360f * p, false, Offset(center.x - ring, center.y - ring),
                    Size(2 * ring, 2 * ring), style = Stroke(1.6f * unit))
            }
        }
    }
}

@Composable
internal fun floatingLockProgress(holding: Boolean): State<Float> =
    animateFloatAsState(if (holding) 1f else 0f,
        tween(if (holding) FLOATING_UNLOCK_HOLD_MS else 220, easing = LinearEasing), label = "悬浮解锁外圈")

/** 锁定时卡片四周一圈淡描边，提示位置和大小已锁住。 */
internal fun Modifier.floatingLockOutline(locked: Boolean, color: Color) = drawWithCache {
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
    }
}

/**
 * 锁定后按住底部小锁 [FLOATING_UNLOCK_HOLD_MS] 解锁；挪动超过触摸阈值或按在小锁两边不算。
 * 没锁时不管：锁定改由调节界面里旋转手柄旁的锁定按钮来做（用户 11-08）。
 */
@Composable
internal fun Modifier.floatingFooterHold(locked: Boolean, onHolding: (Boolean) -> Unit, onToggle: () -> Unit): Modifier {
    val holding by rememberUpdatedState(onHolding)
    val toggle by rememberUpdatedState(onToggle)
    if (!locked) return this
    return semantics {
        role = Role.Button
        contentDescription = "悬浮键盘底部小锁"
        stateDescription = "已锁定"
        onLongClick(label = "解锁") { toggle(); true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (abs(down.position.x - size.width / 2f) > 32.dp.toPx()) return@awaitEachGesture
            holding(true)
            try {
                val ended = withTimeoutOrNull(FLOATING_UNLOCK_HOLD_MS.toLong()) {
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
