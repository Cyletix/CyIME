package com.kingzcheung.xime.ui.keyboard

/** 累积细微位移；改变方向后从转向点重新计算，避免抵消上一方向的余量。 */
internal class CursorStepAccumulator(private val stepPx: Float) {
    init {
        require(stepPx.isFinite() && stepPx > 0f)
    }

    private var remainder = 0f

    fun move(deltaPx: Float): Int {
        if (!deltaPx.isFinite()) return 0
        if (remainder * deltaPx < 0f) remainder = 0f
        remainder += deltaPx
        val steps = (remainder / stepPx).toInt()
        remainder -= steps * stepPx
        return steps
    }
}
