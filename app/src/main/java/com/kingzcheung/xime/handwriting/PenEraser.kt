package com.kingzcheung.xime.handwriting

import androidx.compose.ui.unit.dp

/**
 * S Pen 笔画橡皮的共用判定（输入法手写板和手写训练模式共用，行为一致）。
 *
 * 橡皮轨迹按“上一个橡皮点 → 当前橡皮点”的线段算，不只看采样点：快速划过时两次采样之间隔得再远，
 * 中间穿过的笔画也会被擦掉；单点按下时线段退化成一个点。笔画同样按折线算，碰到任意一段就擦掉整笔。
 */
object PenEraser {

    /** 橡皮半径。三星笔记笔画橡皮的默认大小约为笔尖周围 4~5 毫米，这里取 20dp。 */
    val Radius = 20.dp

    /**
     * 橡皮线段 (ex0,ey0)-(ex1,ey1) 是否碰到笔画 [n] 个点组成的折线（[x]/[y] 取第 i 个点）。
     */
    inline fun hits(n: Int, x: (Int) -> Float, y: (Int) -> Float,
                    ex0: Float, ey0: Float, ex1: Float, ey1: Float, radius: Float): Boolean {
        if (n <= 0) return false
        val r2 = radius * radius
        if (n == 1) return pointSegment2(x(0), y(0), ex0, ey0, ex1, ey1) <= r2
        for (i in 0 until n - 1) {
            if (segmentSegment2(x(i), y(i), x(i + 1), y(i + 1), ex0, ey0, ex1, ey1) <= r2) return true
        }
        return false
    }

    /** 点 (px,py) 到线段 (ax,ay)-(bx,by) 距离的平方。 */
    fun pointSegment2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val qx = ax + t * dx - px
        val qy = ay + t * dy - py
        return qx * qx + qy * qy
    }

    /** 两条线段之间最短距离的平方；相交时为 0。 */
    fun segmentSegment2(ax: Float, ay: Float, bx: Float, by: Float,
                        cx: Float, cy: Float, dx: Float, dy: Float): Float {
        if (intersects(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return minOf(
            minOf(pointSegment2(ax, ay, cx, cy, dx, dy), pointSegment2(bx, by, cx, cy, dx, dy)),
            minOf(pointSegment2(cx, cy, ax, ay, bx, by), pointSegment2(dx, dy, ax, ay, bx, by)),
        )
    }

    private fun cross(ox: Float, oy: Float, ax: Float, ay: Float, bx: Float, by: Float) =
        (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)

    private fun intersects(ax: Float, ay: Float, bx: Float, by: Float,
                           cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
