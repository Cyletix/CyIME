package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDockGestureTest {
    /**
     * 一路往下拖到底边时，拖到底前的距离不能把进度条提前填满；
     * 到边进入准备态后仍须从 progress=0 开始计时或继续下顶，立刻松手不会误贴底。
     */
    @Test fun travelBeforeReachingTheBottomDoesNotPreFillProgress() {
        val gesture = FloatingDockGesture()
        // 卡片还没到底：尚未进入准备，progress 为 0。
        gesture.move(dx = 0f, dy = 40f, pushDy = 0f, distanceToBottomDp = 24f, canDock = true)
        assertFalse(gesture.armed)
        assertEquals(0f, gesture.progress, 0f)
        // 从上方有效下拖跨越进入底边区：立刻进入准备态，但 progress 从 0 起步，立刻松手不还原。
        gesture.move(dx = 0f, dy = 16f, pushDy = 0f, distanceToBottomDp = 8f, canDock = true)
        assertTrue(gesture.armed)
        assertEquals(0f, gesture.progress, 0f)
        assertFalse(floatingDockOnRelease(gesture.progress, gesture.armed, true))
        // 停留半程仍未满格，松手保持悬浮。
        gesture.advanceHold(FLOATING_DOCK_DURATION_MILLIS / 2, gesture.epoch)
        assertTrue(gesture.progress in 0.4f..0.6f)
        assertFalse(floatingDockOnRelease(gesture.progress, gesture.armed, true))
        // 停满 FLOATING_DOCK_DURATION_MILLIS（150ms）后松手还原。
        assertEquals(150L, FLOATING_DOCK_DURATION_MILLIS)
        gesture.advanceHold(FLOATING_DOCK_DURATION_MILLIS, gesture.epoch)
        assertEquals(1f, gesture.progress, 0f)
        assertTrue(floatingDockOnRelease(gesture.progress, gesture.armed, true))
    }

    /** 斜向拖入底边区（例如右下角 45°~60° 拖入）不能因横向分量污染 reach 而漏识别。 */
    @Test fun diagonalApproachIntoBottomEdgeArmsReliably() {
        val gesture = FloatingDockGesture()
        gesture.move(dx = 20f, dy = 20f, pushDy = 0f, distanceToBottomDp = 20f, canDock = true)
        assertFalse(gesture.armed)
        gesture.move(dx = 15f, dy = 20f, pushDy = 0f, distanceToBottomDp = 0f, canDock = true)
        assertTrue(gesture.armed)
        assertEquals(0f, gesture.progress, 0f)
        gesture.advanceHold(FLOATING_DOCK_DURATION_MILLIS, gesture.epoch)
        assertEquals(1f, gesture.progress, 0f)
    }

    /**
     * 卡片本来就停在底边区内时，纯横向滑行不能误触准备；
     * 但横移之后只要接着明显向下顶动，就不能被之前的横向距离拉成负债而失灵。
     */
    @Test fun alreadyAtBottomSidewaysSlideCannotArmButFollowingDownwardPushCan() {
        val gesture = FloatingDockGesture()
        gesture.move(dx = 120f, dy = 0f, pushDy = 0f, distanceToBottomDp = 0f, canDock = true)
        gesture.advanceHold(2_000L, gesture.epoch)
        assertFalse(gesture.armed)
        assertEquals(0f, gesture.progress, 0f)

        gesture.move(dx = 0f, dy = FLOATING_DOCK_ARM_PUSH_DP, pushDy = FLOATING_DOCK_ARM_PUSH_DP,
            distanceToBottomDp = 0f, canDock = true)
        assertTrue(gesture.armed)
        gesture.advanceHold(FLOATING_DOCK_DURATION_MILLIS, gesture.epoch)
        assertEquals(1f, gesture.progress, 0f)
    }

    /**
     * 准备好之后手指在底边轻微回弹（仍在退出迟滞内）或小幅左右晃动，
     * 不能打断当前准备状态与计时，只有真正上拉或横滑偏离才取消。
     */
    @Test fun armedStateSurvivesSmallUpwardRelaxationAndLateralWobble() {
        val gesture = FloatingDockGesture()
        gesture.move(dx = 0f, dy = FLOATING_DOCK_ARM_PUSH_DP, pushDy = FLOATING_DOCK_ARM_PUSH_DP,
            distanceToBottomDp = 0f, canDock = true)
        assertTrue(gesture.armed)
        val epoch = gesture.epoch

        // 向上轻微回弹（超过进入阈值 12dp，但小于退出迟滞 28dp）且左右轻晃（小于 40dp）：不丢状态。
        gesture.move(dx = 12f, dy = -18f, pushDy = 0f, distanceToBottomDp = 18f, canDock = true)
        gesture.move(dx = -20f, dy = 0f, pushDy = 0f, distanceToBottomDp = 24f, canDock = true)
        assertTrue(gesture.armed)
        assertEquals(epoch, gesture.epoch)
        gesture.advanceHold(FLOATING_DOCK_DURATION_MILLIS, epoch)
        assertEquals(1f, gesture.progress, 0f)

        // 真正上拉离开超过 FLOATING_DOCK_EXIT_DP（28dp）：干净取消并递增 epoch。
        gesture.move(dx = 0f, dy = -6f, pushDy = 0f, distanceToBottomDp = FLOATING_DOCK_EXIT_DP + 1f, canDock = true)
        assertFalse(gesture.armed)
        assertTrue(gesture.epoch > epoch)

        // 重新准备后，横向偏离超过 FLOATING_DOCK_ARMED_LATERAL_CANCEL_DP（40dp）：取消。
        gesture.move(dx = 0f, dy = FLOATING_DOCK_ARM_PUSH_DP, pushDy = FLOATING_DOCK_ARM_PUSH_DP,
            distanceToBottomDp = 0f, canDock = true)
        val secondEpoch = gesture.epoch
        assertTrue(gesture.armed)
        gesture.move(dx = FLOATING_DOCK_ARMED_LATERAL_CANCEL_DP + 1f, dy = 0f, pushDy = 0f,
            distanceToBottomDp = 0f, canDock = true)
        assertFalse(gesture.armed)
        assertTrue(gesture.epoch > secondEpoch)
    }

    /** 到底后继续往下顶可提前走满进度条；缓冲内上下微晃不会把 pull 刷满，满格后轻微回弹也保住满格。 */
    @Test fun downwardPushAtBottomAcceleratesWithoutWigglePumping() {
        val gesture = FloatingDockGesture()
        gesture.move(dx = 0f, dy = 20f, pushDy = 0f, distanceToBottomDp = 2f, canDock = true)
        assertTrue(gesture.armed)

        // 死区内上下小幅来回晃动（每次下顶 <= 2dp、随后回退）不应刷出 pull。
        repeat(6) {
            gesture.move(dx = 0f, dy = 2f, pushDy = 2f, distanceToBottomDp = 0f, canDock = true)
            gesture.move(dx = 0f, dy = -2f, pushDy = 0f, distanceToBottomDp = 0f, canDock = true)
        }
        assertEquals(0f, gesture.progress, 0f)

        // 单调下顶 FLOATING_DOCK_PUSH_DEAD_ZONE_DP + FLOATING_DOCK_PUSH_SPAN_DP（8 + 24 = 32dp）：
        // 极短时间内（快速拖到底扫过，未满 FLOATING_DOCK_MIN_DWELL_MILLIS）不得直接满格误取消悬浮。
        val totalPush = FLOATING_DOCK_PUSH_DEAD_ZONE_DP + FLOATING_DOCK_PUSH_SPAN_DP
        gesture.move(dx = 0f, dy = totalPush, pushDy = totalPush, distanceToBottomDp = 0f, canDock = true)
        assertTrue(gesture.progress < 1f)
        assertFalse(floatingDockOnRelease(gesture.progress, gesture.armed, true))

        // 驻留满 FLOATING_DOCK_MIN_DWELL_MILLIS（110ms）后下顶立即满格（快于自然等待 150ms）。
        gesture.advanceHold(FLOATING_DOCK_MIN_DWELL_MILLIS, gesture.epoch)
        assertEquals(1f, gesture.progress, 0f)
        assertTrue(floatingDockOnRelease(gesture.progress, gesture.armed, true))

        // 满格后松手瞬间轻微向上松弛（仍在退出阈值内）：满格状态不丢失。
        gesture.move(dx = 4f, dy = -12f, pushDy = 0f, distanceToBottomDp = 12f, canDock = true)
        assertTrue(gesture.armed)
        assertEquals(1f, gesture.progress, 0f)
    }

    /** 快速向下拖到底并大幅下冲过底边（如 60ms 内猛扫 100dp），不能触发取消悬浮。 */
    @Test fun fastDragThroughBottomWithinMinDwellDoesNotDock() {
        val gesture = FloatingDockGesture()
        gesture.move(dx = 0f, dy = 40f, pushDy = 0f, distanceToBottomDp = 24f, canDock = true)
        gesture.move(dx = 0f, dy = 100f, pushDy = 80f, distanceToBottomDp = 0f, canDock = true)
        assertTrue(gesture.armed)
        gesture.advanceHold(FLOATING_DOCK_MIN_DWELL_MILLIS - 20L, gesture.epoch)
        assertTrue(gesture.progress < 1f)
        assertFalse(floatingDockOnRelease(gesture.progress, gesture.armed, true))
    }

    @Test
    fun edgeDetectionOnlyAcceptsTheBottomAboveNavigation() {
        fun edge(x: Float, y: Float) = floatingDockEdge(x, y, horizontalTravel = 30f, maxOffsetY = 500f, bottomInset = 20f)
        assertEquals(FloatingDockEdge.BOTTOM, edge(0f, 20f))
        assertEquals(FloatingDockEdge.BOTTOM, edge(0f, 0f))
        assertNull(edge(-30f, 200f))
        assertNull(edge(30f, 200f))
        assertNull(edge(0f, 500f))
        assertNull(edge(0f, 200f))
        assertNull(edge(0f, 33f))
    }
    @Test fun releasingDocksExactlyWhenThePreviewShowsTheDockedShape() {
        // The outline has reached the docked shape and the card is on the edge: docks.
        assertTrue(floatingDockOnRelease(previewProgress = 1f, onBottomEdge = true, hasDestination = true))
        // Still morphing: stays floating, as the half-drawn outline shows.
        assertFalse(floatingDockOnRelease(previewProgress = 0.9f, onBottomEdge = true, hasDestination = true))
        // Moved off the edge after the preview finished: no dock.
        assertFalse(floatingDockOnRelease(previewProgress = 1f, onBottomEdge = false, hasDestination = true))
        assertFalse(floatingDockOnRelease(previewProgress = 1f, onBottomEdge = true, hasDestination = false))
    }

    @Test fun pullingAFixedKeyboardUpHasResistanceBeforeItTurnsFloating() {
        // 轻轻一拖不动；过了死区才开始变，拉满一整段才算可以松手变悬浮。
        assertEquals(0f, modeSwitchPullProgress(FIXED_TO_FLOATING_DEAD_ZONE_DP), 0f)
        assertEquals(0.5f, modeSwitchPullProgress(FIXED_TO_FLOATING_DEAD_ZONE_DP + FIXED_TO_FLOATING_PULL_DP / 2f), 0.001f)
        assertTrue(modeSwitchPullProgress(FIXED_TO_FLOATING_DEAD_ZONE_DP + FIXED_TO_FLOATING_PULL_DP - 1f) < 1f)
        assertEquals(1f, modeSwitchPullProgress(400f), 0f)
    }
}
