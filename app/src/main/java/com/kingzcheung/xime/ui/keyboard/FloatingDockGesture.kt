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
    tolerance: Float = FLOATING_DOCK_ENTER_DP,
): FloatingDockEdge? {
    return if ((offsetY - bottomInset).coerceAtLeast(0f) <= tolerance) FloatingDockEdge.BOTTOM else null
}

/** 进入底部贴边恢复区的阈值（dp）。 */
internal const val FLOATING_DOCK_ENTER_DP = 12f
/** 已经准备贴底后，向上离开超过该阈值（dp）才取消准备，避免松手微抖丢状态。 */
internal const val FLOATING_DOCK_EXIT_DP = 28f
/** 卡片顶到底边后允许记录的虚拟下顶缓冲（dp），手指向下顶后再轻微回弹不会立刻拉离底边。 */
internal const val FLOATING_DOCK_OVERSHOOT_CUSHION_DP = 10f

/**
 * 进入底部贴边恢复状态并保持不松开时，显示最终定位框并可松手还原的等待时长（ms）。
 * 由 450ms 大幅缩短为 150ms，配合最低驻留保护 [FLOATING_DOCK_MIN_DWELL_MILLIS]，
 * 兼顾贴底快速响应与快速拖到底不误取消悬浮窗。
 */
internal const val FLOATING_DOCK_DURATION_MILLIS = 150L
/** 快速拖到底过冲时也不得在进入底边区后的极短时间内立即满格，避免误取消悬浮。 */
internal const val FLOATING_DOCK_MIN_DWELL_MILLIS = 110L
/** 卡片原本就停在底边贴边区内时，需要净向下再顶这么多（dp）才准备贴底，防止纯横移误触发。 */
internal const val FLOATING_DOCK_ARM_PUSH_DP = 4f
/** 从上方拖入底边区前，有效的向下接近行程（dp）；达到后一进底边区即刻进入准备状态。 */
internal const val FLOATING_DOCK_APPROACH_DOWN_DP = 12f
/** 已经准备贴底后，相对进入点横向偏离超过这么多（dp）才取消准备。 */
internal const val FLOATING_DOCK_ARMED_LATERAL_CANCEL_DP = 40f
/** 已经准备贴底后，继续往下顶过死区再走完这段行程（dp）也可提前满格，适配屏底剩余行程有限的手机。 */
internal const val FLOATING_DOCK_PUSH_DEAD_ZONE_DP = 8f
internal const val FLOATING_DOCK_PUSH_SPAN_DP = 24f

internal fun floatingDockPushProgress(pushDp: Float): Float =
    ((pushDp - FLOATING_DOCK_PUSH_DEAD_ZONE_DP) / FLOATING_DOCK_PUSH_SPAN_DP).coerceIn(0f, 1f)

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

/**
 * 贴底变回固定只看进入底边区后的阶段：拖到底之前走了多远都不算入进度条（progress 从 0 开始）。
 * - 从上方有效下拖进底边区时会立即进入准备状态（[armed]），避免被屏底手势区或斜向接近累积的横向位移卡住；
 * - 本来就在底边区内时，需要净向下顶过 [FLOATING_DOCK_ARM_PUSH_DP] 才准备，纯横移不误触；
 * - 准备后具备纵向迟滞（[FLOATING_DOCK_EXIT_DP]）与横向容差（[FLOATING_DOCK_ARMED_LATERAL_CANCEL_DP]），微抖不丢状态。
 */
internal class FloatingDockGesture {
    private var approachDownY = 0f
    private var armPushX = 0f
    private var armPushY = 0f
    private var armedPushY = 0f
    private var armedLateralX = 0f
    private var pull by mutableFloatStateOf(0f)
    private var hold by mutableFloatStateOf(0f)
    private var heldMillis = 0L
    var armed by mutableStateOf(false)
        private set
    var epoch by mutableIntStateOf(0)
        private set
    val progress: Float
        get() {
            if (!armed) return 0f
            val raw = maxOf(pull, hold)
            return if (heldMillis >= FLOATING_DOCK_MIN_DWELL_MILLIS) raw else minOf(raw, 0.99f)
        }

    /**
     * [dx] 手指横向位移（dp），[dy] 手指纵向位移（向下为正，dp），
     * [pushDy] 卡片顶在底边时手指继续往下顶出的位移（dp），
     * [distanceToBottomDp] 卡片底边距离底边限位的距离（dp），
     * [canDock] 是否具备可还原的固定目标（未旋转且有目标尺寸）。
     */
    fun move(
        dx: Float,
        dy: Float,
        pushDy: Float,
        distanceToBottomDp: Float,
        canDock: Boolean,
    ) {
        if (!canDock) {
            resetAll(bumpEpoch = armed)
            return
        }
        if (armed) {
            if (distanceToBottomDp > FLOATING_DOCK_EXIT_DP) {
                resetAll(bumpEpoch = true)
                return
            }
            armedLateralX += dx
            if (abs(armedLateralX) > FLOATING_DOCK_ARMED_LATERAL_CANCEL_DP) {
                resetAll(bumpEpoch = true)
                return
            }
            if (dy > 0f && pushDy > 0f) {
                armedPushY += pushDy
                pull = maxOf(pull, floatingDockPushProgress(armedPushY))
            } else if (dy < 0f) {
                // 防止在缓冲区间内上下高频微晃叠加进度；已达到的最高进度保留，避免回弹抖动掉落。
                armedPushY = (armedPushY + dy).coerceAtLeast(0f)
            }
            return
        }

        if (distanceToBottomDp > FLOATING_DOCK_ENTER_DP) {
            armPushX = 0f
            armPushY = 0f
            if (dy < 0f) {
                approachDownY = (approachDownY + dy).coerceAtLeast(0f)
            } else if (dy > 0f && dy * 3f >= abs(dx)) {
                approachDownY = (approachDownY + dy).coerceAtMost(FLOATING_DOCK_APPROACH_DOWN_DP * 2f)
            }
            return
        }

        // 处理单帧快速从进入区上方跨入底边区的情况。
        val beforeDistance = distanceToBottomDp + (dy - pushDy.coerceAtLeast(0f)).coerceAtLeast(0f)
        if (beforeDistance > FLOATING_DOCK_ENTER_DP && dy > 0f && dy * 3f >= abs(dx)) {
            approachDownY = (approachDownY + (beforeDistance - FLOATING_DOCK_ENTER_DP))
                .coerceAtMost(FLOATING_DOCK_APPROACH_DOWN_DP * 2f)
        }

        if (approachDownY >= FLOATING_DOCK_APPROACH_DOWN_DP) {
            arm(initialPushDy = pushDy.coerceAtLeast(0f))
            return
        }

        // 本来就在底边区内（或横向偏离取消后重新尝试）：需要明显向下顶动，横向移动不积累负债。
        if (dy > 0f) {
            armPushY += dy
            armPushX += abs(dx)
            val net = armPushY - armPushX
            if (net <= 0f) {
                armPushY = 0f
                armPushX = 0f
            } else if (net >= FLOATING_DOCK_ARM_PUSH_DP) {
                arm(initialPushDy = pushDy.coerceAtLeast(0f))
            }
        } else if (dy < 0f) {
            armPushY = (armPushY + dy).coerceAtLeast(0f)
            if (armPushY == 0f) armPushX = 0f
        } else if (abs(dx) > 0f) {
            armPushY = 0f
            armPushX = 0f
        }
    }

    private fun arm(initialPushDy: Float) {
        armed = true
        hold = 0f
        heldMillis = 0L
        armedLateralX = 0f
        armedPushY = initialPushDy
        pull = floatingDockPushProgress(armedPushY)
        approachDownY = 0f
        armPushX = 0f
        armPushY = 0f
        epoch++
    }

    private fun resetAll(bumpEpoch: Boolean) {
        approachDownY = 0f
        armPushX = 0f
        armPushY = 0f
        armedPushY = 0f
        armedLateralX = 0f
        pull = 0f
        hold = 0f
        heldMillis = 0L
        armed = false
        if (bumpEpoch) epoch++
    }

    fun advanceHold(elapsedMillis: Long, startedEpoch: Int) {
        if (armed && epoch == startedEpoch) {
            heldMillis = maxOf(heldMillis, elapsedMillis.coerceAtLeast(0L))
            hold = (elapsedMillis.toFloat() / FLOATING_DOCK_DURATION_MILLIS).coerceIn(0f, 1f)
        }
    }

    fun cancel() {
        resetAll(bumpEpoch = true)
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
