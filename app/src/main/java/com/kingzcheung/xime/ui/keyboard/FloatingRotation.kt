package com.kingzcheung.xime.ui.keyboard

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 悬浮键盘可绕卡片中心旋转的范围；正值为顺时针。 */
internal const val FLOATING_ROTATION_MAX_DEGREES = 90f
/** 靠近 0° / ±90° 这么多度以内吸过去，方便转回正。 */
internal const val FLOATING_ROTATION_SNAP_DEGREES = 4f
/** 调节界面里键盘顶边正上方居中的旋转手柄。 */
internal const val FLOATING_ROTATE_HANDLE_SIZE_DP = 36
internal const val FLOATING_ROTATE_HANDLE_GAP_DP = 10
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

/** 旋转后要守住的点（相对卡片中心、未旋转）：顶部条四角、底部拖动按钮两端。 */
private fun guardPoints(cardWidth: Float, cardHeight: Float, unit: Float): List<Pair<Float, Float>> {
    val halfW = cardWidth / 2f
    val halfH = cardHeight / 2f
    val strip = (FLOATING_ROTATE_TOP_STRIP_DP * unit).coerceAtMost(cardHeight)
    val pillY = halfH - (FLOATING_DRAG_BAR_HEIGHT_DP * unit / 2f).coerceAtMost(halfH)
    val pillHalf = (FLOATING_DRAG_INDICATOR_WIDTH_DP * unit / 2f).coerceAtMost(halfW)
    return listOf(
        -halfW to -halfH, halfW to -halfH, -halfW to -halfH + strip, halfW to -halfH + strip,
        -pillHalf to pillY, pillHalf to pillY,
    )
}

/**
 * 旋转后卡片可放的位置：顶部条始终完整在屏内，底部拖动按钮不出屏，其余部分（底角）可以略出屏。
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

/**
 * 转着的悬浮卡片按旋转规则摆放后的未旋转矩形（宿主 px）；没转或这个角度放不下时返回 null，按直角规则摆。
 * 偏移量单位 dp，与保存的 floatingOffsetX/Y 相同。
 */
internal fun floatingRotatedCardRect(
    hostWidthPx: Float,
    hostHeightPx: Float,
    widthPx: Float,
    totalHeightPx: Float,
    offsetXDp: Float,
    offsetYDp: Float,
    bottomInsetDp: Float,
    degrees: Float,
    density: Float,
): ResizeRect? {
    if (!isFloatingRotated(degrees)) return null
    val bounds = floatingRotatedOffsetBounds(hostWidthPx / density, hostHeightPx / density,
        widthPx / density, totalHeightPx / density, degrees, bottomInsetDp)
    if (!bounds.feasible) return null
    val left = (hostWidthPx / density / 2f + bounds.clampX(offsetXDp)) * density - widthPx / 2f
    val bottom = (hostHeightPx / density - bounds.clampY(offsetYDp)) * density
    return ResizeRect(left, bottom - totalHeightPx, left + widthPx, bottom)
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

/** 绕 [pivotX],[pivotY] 转 [degrees] 后的点。 */
internal fun rotatePoint(x: Float, y: Float, pivotX: Float, pivotY: Float, degrees: Float): Pair<Float, Float> {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val vx = x - pivotX
    val vy = y - pivotY
    return (pivotX + vx * c - vy * s) to (pivotY + vx * s + vy * c)
}

/** 旋转后卡片在宿主里的四边形（px，顺时针四角）。 */
internal fun floatingRotatedTouchQuad(card: ResizeRect, degrees: Float): FloatArray {
    val corners = floatArrayOf(card.left, card.top, card.right, card.top, card.right, card.bottom, card.left, card.bottom)
    for (i in 0 until 4) {
        val (x, y) = rotatePoint(corners[i * 2], corners[i * 2 + 1], card.centerX, card.centerY, degrees)
        corners[i * 2] = x
        corners[i * 2 + 1] = y
    }
    return corners
}

/**
 * IME 触摸区域：把转过的卡片近似成 [strips] 条横条（每条取该高度范围内卡片的最左到最右，只会多不会少）。
 * 不能直接用 Region.setPath：斜边会被拆成上千个一像素高的矩形，系统每帧比对、下发这种区域，打字和拖动都会卡。
 */
internal fun floatingRotatedTouchStrips(card: ResizeRect, degrees: Float, strips: Int = 12): List<ResizeRect> {
    if (!isFloatingRotated(degrees)) return listOf(card)
    val q = floatingRotatedTouchQuad(card, degrees)
    val top = minOf(q[1], q[3], q[5], q[7])
    val bottom = maxOf(q[1], q[3], q[5], q[7])
    val step = (bottom - top) / strips.coerceAtLeast(1)
    return (0 until strips.coerceAtLeast(1)).mapNotNull { i ->
        val y0 = top + step * i
        val y1 = if (i == strips - 1) bottom else y0 + step
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        fun take(x: Float) { minX = minOf(minX, x); maxX = maxOf(maxX, x) }
        for (k in 0 until 4) {
            val ax = q[k * 2]; val ay = q[k * 2 + 1]
            val bx = q[(k + 1) % 4 * 2]; val by = q[(k + 1) % 4 * 2 + 1]
            if (ay in y0..y1) take(ax)
            // 边与横条上下沿的交点。
            for (y in floatArrayOf(y0, y1)) {
                if ((ay - y) * (by - y) <= 0f && ay != by) take(ax + (bx - ax) * (y - ay) / (by - ay))
            }
        }
        if (minX <= maxX) ResizeRect(minX, y0, maxX, y1) else null
    }
}
