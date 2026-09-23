package com.kingzcheung.xime.ui.keyboard

internal enum class FloatingDockEdge { BOTTOM, LEFT, RIGHT, TOP }

/** 只在导航栏上方的底部贴边区恢复；横向和顶部位置不参与恢复判定。 */
@Suppress("UNUSED_PARAMETER")
internal fun floatingDockEdge(
    offsetX: Float,
    offsetY: Float,
    horizontalTravel: Float,
    maxOffsetY: Float,
    bottomInset: Float = 0f,
    tolerance: Float = 12f,
): FloatingDockEdge? {
    return if ((offsetY - bottomInset).coerceAtLeast(0f) <= tolerance) FloatingDockEdge.BOTTOM else null
}

internal const val FLOATING_DOCK_DURATION_MILLIS = 500L
internal const val FLOATING_DRAG_BAR_HEIGHT_DP = 28

/** 持续贴底半秒完成恢复预览，只有正常松手才能确认。 */
internal class FloatingDockGesture(private val holdMillis: Long = FLOATING_DOCK_DURATION_MILLIS) {
    private var dragging = false
    private var edge: FloatingDockEdge? = null
    private var enteredAt = 0L

    fun start(currentEdge: FloatingDockEdge?, now: Long) {
        cancel()
        dragging = true
        update(currentEdge, now)
    }

    fun update(currentEdge: FloatingDockEdge?, now: Long): Boolean {
        if (!dragging) return false
        val bottomEdge = currentEdge?.takeIf { it == FloatingDockEdge.BOTTOM }
        if (edge != bottomEdge) {
            edge = bottomEdge
            enteredAt = now
        }
        return edge != null && now - enteredAt >= holdMillis
    }

    fun release(currentEdge: FloatingDockEdge?, now: Long): Boolean {
        val restore = update(currentEdge, now)
        cancel()
        return restore
    }

    fun cancel() {
        dragging = false
        edge = null
        enteredAt = 0L
    }
}
