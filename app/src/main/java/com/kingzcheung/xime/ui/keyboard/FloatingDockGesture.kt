package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlin.math.abs

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

/** 已经靠近底边，剩余行程不足一次长拖时的短停顿。 */
internal const val FLOATING_DOCK_DURATION_MILLIS = 300L

/**
 * Releasing docks exactly when the preview shows the docked shape: the outline has reached it and the
 * card is still on the bottom edge. The preview, not a separate timer, decides.
 */
internal fun floatingDockOnRelease(previewProgress: Float, onBottomEdge: Boolean, hasDestination: Boolean): Boolean =
    hasDestination && onBottomEdge && previewProgress >= 1f
internal const val FLOATING_DRAG_BAR_HEIGHT_DP = 28
internal fun floatingFooterHeightDp(shortSideDp: Int): Int =
    if (shortSideDp in 1 until 600) 16 else FLOATING_DRAG_BAR_HEIGHT_DP

internal val android.content.res.Configuration.floatingFooterHeightDp: Int
    get() = floatingFooterHeightDp(minOf(screenWidthDp, screenHeightDp))

/** 调节界面里固定键盘上拖变悬浮：先有 [FIXED_TO_FLOATING_DEAD_ZONE_DP] 不动的阻力，再拉 [FIXED_TO_FLOATING_PULL_DP] 才满。 */
internal const val FIXED_TO_FLOATING_DEAD_ZONE_DP = 48f
internal const val FIXED_TO_FLOATING_PULL_DP = 96f

internal fun modeSwitchPullProgress(verticalDp: Float, horizontalDp: Float = 0f): Float =
    ((verticalDp - abs(horizontalDp) - FIXED_TO_FLOATING_DEAD_ZONE_DP) / FIXED_TO_FLOATING_PULL_DP).coerceIn(0f, 1f)

/** 移动条覆盖键帽以下的完整空白，卡片本身仍只额外预留原来的 28dp。 */
internal fun floatingDragAreaHeightDp(contentPaddingDp: Int, keyClearanceDp: Float, totalHeightDp: Float,
    footerDp: Int = FLOATING_DRAG_BAR_HEIGHT_DP): Float =
    (footerDp + contentPaddingDp.coerceAtLeast(0) + keyClearanceDp.coerceAtLeast(0f))
        .coerceAtMost(totalHeightDp.coerceAtLeast(0f))

/** 距离来自手指在屏幕上的净位移，即使卡片先碰到边也能识别继续往下拖。 */
internal class FloatingDockGesture {
    private var x = 0f
    private var y = 0f
    private var pull by mutableFloatStateOf(0f)
    private var hold by mutableFloatStateOf(0f)
    var armed by mutableStateOf(false)
        private set
    var epoch by mutableIntStateOf(0)
        private set
    val progress get() = if (armed) maxOf(pull, hold) else 0f

    fun move(dx: Float, dy: Float, onBottom: Boolean) {
        x += dx
        y += dy
        pull = modeSwitchPullProgress(y, x)
        val next = onBottom && y - abs(x) >= 8f && dy >= 0f
        if (armed != next) {
            if (!next) {
                // 离开或回拉表示这次贴底作废，再次靠近要从新的方向和行程开始。
                x = 0f
                y = 0f
                pull = 0f
            }
            armed = next
            hold = 0f
            epoch++
        }
    }

    fun advanceHold(elapsedMillis: Long, startedEpoch: Int) {
        if (armed && epoch == startedEpoch)
            hold = (elapsedMillis.toFloat() / FLOATING_DOCK_DURATION_MILLIS).coerceIn(0f, 1f)
    }

    fun cancel() {
        x = 0f
        y = 0f
        pull = 0f
        hold = 0f
        armed = false
        epoch++
    }
}

/** 用帧时间计时，系统动画倍率不会把手势的等待时间放大。 */
@Composable
internal fun TrackDockHold(gesture: FloatingDockGesture) {
    val epoch = gesture.epoch
    LaunchedEffect(gesture, epoch) {
        if (!gesture.armed) return@LaunchedEffect
        val start = withFrameMillis { it }
        do {
            val elapsed = withFrameMillis { it } - start
            gesture.advanceHold(elapsed, epoch)
        } while (elapsed < FLOATING_DOCK_DURATION_MILLIS && gesture.armed && gesture.epoch == epoch)
    }
}
