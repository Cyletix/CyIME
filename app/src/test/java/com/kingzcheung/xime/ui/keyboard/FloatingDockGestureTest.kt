package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDockGestureTest {
    @Test fun aDeliberateDownwardDragDocksOnReleaseWithoutWaiting() {
        val gesture = FloatingDockGesture()
        gesture.move(0f, 120f, false)
        assertEquals(0f, gesture.progress, 0f)
        gesture.move(0f, 26f, true)
        assertEquals(1f, gesture.progress, 0f)
        assertTrue(floatingDockOnRelease(gesture.progress, gesture.armed, true))
        gesture.move(0f, -2f, false)
        assertEquals(0f, gesture.progress, 0f)
        gesture.move(0f, 10f, true)
        assertTrue(gesture.progress < 1f)
    }

    @Test fun aShortDownwardDragNearTheEdgeCanUseTheShortHold() {
        val gesture = FloatingDockGesture()
        gesture.move(0f, 12f, true)
        val first = gesture.epoch
        gesture.advanceHold(299, first)
        assertTrue(gesture.progress < 1f)
        gesture.move(0f, -3f, false)
        gesture.move(0f, 12f, true)
        gesture.advanceHold(500, first)
        assertEquals(0f, gesture.progress, 0f)
        gesture.advanceHold(300, gesture.epoch)
        assertEquals(1f, gesture.progress, 0f)
        gesture.cancel()
        assertFalse(gesture.armed)
        assertEquals(0f, gesture.progress, 0f)
    }

    @Test fun beingAtTheBottomOrDraggingSidewaysCannotArmDocking() {
        val gesture = FloatingDockGesture()
        gesture.move(120f, 30f, true)
        gesture.advanceHold(2000, gesture.epoch)
        assertFalse(gesture.armed)
        gesture.cancel()
        gesture.move(0f, 0f, true)
        gesture.advanceHold(2000, gesture.epoch)
        assertFalse(gesture.armed)
        assertTrue(modeSwitchPullProgress(150f, 90f) < 1f)
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
