package com.kingzcheung.xime.ui.keyboard

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 悬浮键盘可绕卡片中心旋转的范围；正值为顺时针。 */
internal const val FLOATING_ROTATION_MAX_DEGREES = 90f
/** 靠近 0° / ±90° 这么多度以内吸过去，方便转回正。 */
internal const val FLOATING_ROTATION_SNAP_DEGREES = 4f
/** 卡片顶边正上方居中的旋转手柄。 */
internal const val FLOATING_ROTATE_HANDLE_WIDTH_DP = 48
internal const val FLOATING_ROTATE_HANDLE_HEIGHT_DP = 22
/** 旋转后必须完整留在屏内的顶部条（工具栏/候选栏）。 */
internal const val FLOATING_ROTATE_TOP_STRIP_DP = 44
/** 底部拖动条中间的拖动按钮宽度，旋转后它必须留在屏内。 */
internal const val FLOATING_DRAG_INDICATOR_WIDTH_DP = 40

internal fun isFloatingRotated(degrees: Float): Boolean = abs(degrees) >= 0.05f

internal fun snapFloatingRotation(degrees: Float): Float {
    val clamped = degrees.coerceIn(-FLOATING_ROTATION_MAX_DEGREES, FLOATING_ROTATION_MAX_DEGREES)
    for (target in floatArrayOf(0f, FLOATING_ROTATION_MAX_DEGREES, -FLOATING_ROTATION_MAX_DEGREES)) {
        if (abs(clamped - target) <= FLOATING_ROTATION_SNAP_DEGREES) return target
    }
    return clamped
}

/** 卡片位置的允许范围，单位与 offsetX（中心相对宿主中线）/ offsetY（底边离宿主底）相同。 */
internal data class FloatingOffsetBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    val feasible: Boolean get() = minX <= maxX && minY <= maxY
    fun clampX(x: Float) = if (minX <= maxX) x.coerceIn(minX, maxX) else (minX + maxX) / 2f
    fun clampY(y: Float) = if (minY <= maxY) y.coerceIn(minY, maxY) else (minY + maxY) / 2f
}

/** 旋转后要守住的点（相对卡片中心、未旋转）：顶部条四角、旋转手柄顶边两角、底部拖动按钮两端。 */
private fun guardPoints(cardWidth: Float, cardHeight: Float, unit: Float): List<Pair<Float, Float>> {
    val halfW = cardWidth / 2f
    val halfH = cardHeight / 2f
    val strip = (FLOATING_ROTATE_TOP_STRIP_DP * unit).coerceAtMost(cardHeight)
    val handleHalf = (FLOATING_ROTATE_HANDLE_WIDTH_DP * unit / 2f).coerceAtMost(halfW)
    val handleTop = -halfH - FLOATING_ROTATE_HANDLE_HEIGHT_DP * unit
    val pillY = halfH - (FLOATING_DRAG_BAR_HEIGHT_DP * unit / 2f).coerceAtMost(halfH)
    val pillHalf = (FLOATING_DRAG_INDICATOR_WIDTH_DP * unit / 2f).coerceAtMost(halfW)
    return listOf(
        -halfW to -halfH, halfW to -halfH, -halfW to -halfH + strip, halfW to -halfH + strip,
        -handleHalf to handleTop, handleHalf to handleTop,
        -pillHalf to pillY, pillHalf to pillY,
    )
}

/**
 * 旋转后卡片可放的位置：顶部条和旋转手柄始终完整在屏内，底部拖动按钮不出屏，其余部分（底角）可以略出屏。
 * 所有长度同一单位（dp 时 [unit] = 1，px 时 [unit] = density）；宿主顶为 0，底部扣掉 [bottomInset]。
 */
internal fun floatingRotatedOffsetBounds(
    hostWidth: Float,
    hostHeight: Float,
    cardWidth: Float,
    cardHeight: Float,
    degrees: Float,
    bottomInset: Float,
    unit: Float = 1f,
): FloatingOffsetBounds {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    var minQx = Float.MAX_VALUE; var maxQx = -Float.MAX_VALUE
    var minQy = Float.MAX_VALUE; var maxQy = -Float.MAX_VALUE
    for ((vx, vy) in guardPoints(cardWidth, cardHeight, unit)) {
        val qx = vx * c - vy * s
        val qy = vx * s + vy * c
        minQx = minOf(minQx, qx); maxQx = maxOf(maxQx, qx)
        minQy = minOf(minQy, qy); maxQy = maxOf(maxQy, qy)
    }
    val bottom = hostHeight - bottomInset.coerceAtLeast(0f)
    val minCx = -minQx; val maxCx = hostWidth - maxQx
    val minCy = -minQy; val maxCy = bottom - maxQy
    // offsetX = cx - hostWidth/2；offsetY = hostHeight - (cy + cardHeight/2)。
    return FloatingOffsetBounds(
        minX = minCx - hostWidth / 2f, maxX = maxCx - hostWidth / 2f,
        minY = hostHeight - cardHeight / 2f - maxCy, maxY = hostHeight - cardHeight / 2f - minCy,
    )
}

/** 从可行的 [from] 朝 [to] 转，转不到时停在最后一个还能守住顶部条的角度。 */
internal fun limitFloatingRotation(from: Float, to: Float, feasible: (Float) -> Boolean): Float {
    if (feasible(to)) return to
    if (!feasible(from)) return from
    var ok = from
    var bad = to
    repeat(16) {
        val mid = (ok + bad) / 2f
        if (feasible(mid)) ok = mid else bad = mid
    }
    return ok
}

/** 角度差折到 (-180, 180]，手指绕过 ±180° 时不跳。 */
internal fun floatingAngleDelta(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/**
 * 旋转后卡片和手柄在宿主里的四边形（px，顺时针四角），给 IME 触摸区域用：
 * 只有键盘真正盖住的地方吃触摸，旋转露出的空角留给下面的应用。
 */
internal fun floatingRotatedTouchQuads(card: ResizeRect, degrees: Float, density: Float): List<FloatArray> {
    val cx = card.centerX
    val cy = (card.top + card.bottom) / 2f
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    fun quad(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val corners = floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        for (i in 0 until 4) {
            val vx = corners[i * 2] - cx
            val vy = corners[i * 2 + 1] - cy
            corners[i * 2] = cx + vx * c - vy * s
            corners[i * 2 + 1] = cy + vx * s + vy * c
        }
        return corners
    }
    val handleHalf = (FLOATING_ROTATE_HANDLE_WIDTH_DP * density / 2f).coerceAtMost(card.width / 2f)
    return listOf(
        quad(card.left, card.top, card.right, card.bottom),
        quad(cx - handleHalf, card.top - FLOATING_ROTATE_HANDLE_HEIGHT_DP * density, cx + handleHalf, card.top),
    )
}
