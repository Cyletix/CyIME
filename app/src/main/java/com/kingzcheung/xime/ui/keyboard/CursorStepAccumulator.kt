package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

/** 累积细微位移；改变方向后从转向点重新计算，避免抵消上一方向的余量。 */
internal class CursorStepAccumulator(stepPx: Float) {
    /** 输入框排版在拖动开始后才到，步长可在拖动中更新。 */
    var stepPx: Float = stepPx
        set(value) {
            require(value.isFinite() && value > 0f)
            field = value
        }

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

/**
 * 光标距离设置的单位按真实长度换算：一个单位 = 用户手机（S23 Ultra，FHD+ 1080 宽、450 dpi，实际约 375.8 ppi）上的 1 dp，
 * 约 0.19 毫米，手机手感保持原样；平板逻辑 dp 的实际长度不同、用户改显示缩放也会变，按屏幕真实像素密度换算后同一档滑同样的距离。
 * 读不到物理密度时退回逻辑 dp。
 */
internal fun cursorUnitPx(physical: KeyboardPhysicalMetrics?, pixelsPerDp: Float): Float =
    physical?.let { it.xDpi * CURSOR_UNIT_MM / 25.4f } ?: pixelsPerDp

/**
 * 输入框一行能放几个字：输入法服务在移光标开始时向输入框要排版（CursorAnchorInfo）后填入，0 表示还没到或输入框不提供。
 */
internal object CursorLineMetrics {
    @Volatile var requester: (() -> Unit)? = null
    @Volatile var charsPerLine = 0f
        private set

    fun request() {
        charsPerLine = 0f
        requester?.invoke()
    }

    fun update(chars: Float?) {
        charsPerLine = chars?.takeIf { it.isFinite() && it >= 2f } ?: 0f
    }
}

/**
 * 从中间滑到边缘（半个窗口宽）走 [lines] 行：每字距离 = 半宽 ÷（一行字数 × 行数），字号、屏幕和横竖屏都自动对上。
 * 读不到一行字数时用 [fallbackStepPx]（设置里的每字距离）；结果限制在约 0.6–7.6 毫米，太小会抖、太大一步跨太远。
 */
internal fun lineCursorStepPx(halfWidthPx: Float, lines: Float, charsPerLine: Float, fallbackStepPx: Float, unitPx: Float): Float {
    if (charsPerLine < 2f || !(halfWidthPx > 0f) || !(lines > 0f)) return fallbackStepPx
    return (halfWidthPx / (charsPerLine * lines)).coerceIn(3f * unitPx, 40f * unitPx)
}

internal fun cursorDistanceMillimeters(distance: Float): Float = distance * CURSOR_UNIT_MM

internal const val CURSOR_UNIT_MM = 2.8125f / 375.8f * 25.4f

@Composable
internal fun rememberCursorUnitPx(): Float {
    val context = LocalContext.current
    val display = LocalView.current.display
    val pixelsPerDp = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    return remember(context, display, pixelsPerDp, configuration) {
        cursorUnitPx(keyboardPhysicalMetrics(context, display, pixelsPerDp), pixelsPerDp)
    }
}
