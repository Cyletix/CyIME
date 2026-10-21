package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDockGestureTest {
    @Test
    fun aStationaryKeyboardAtTheEdgeNeverArmsRestoration() {
        val gesture = FloatingDockGesture()
        assertFalse(gesture.update(FloatingDockEdge.BOTTOM, 10_000L))
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 10_000L))
    }

    @Test
    fun holdingAnEdgeArmsAfterASecondAndOnlyReleaseConfirms() {
        // One second (twice the earlier half second): a tall floating card, e.g. with clipboard search
        // open, reaches the bottom edge easily while being dragged and must not dock by accident.
        assertEquals(1_000L, FLOATING_DOCK_DURATION_MILLIS)
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 100L)
        assertFalse(gesture.update(FloatingDockEdge.BOTTOM, 1_099L))
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 1_100L))
        assertTrue(gesture.release(FloatingDockEdge.BOTTOM, 1_200L))
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 1_300L))
    }

    @Test
    fun releasingBeforeTheThresholdKeepsFloating() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 999L))
    }

    @Test
    fun leavingAnEdgeClearsThePreviewAndRestartsTheTimerOnReentry() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 2_000L))
        assertFalse(gesture.update(null, 2_100L))
        assertFalse(gesture.update(FloatingDockEdge.BOTTOM, 2_200L))
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 3_199L))
    }

    @Test
    fun movingToAnotherEdgeDoesNotCarryOverTheElapsedTime() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 2_000L))
        assertFalse(gesture.update(FloatingDockEdge.TOP, 2_100L))
        assertFalse(gesture.release(FloatingDockEdge.TOP, 3_000L))
    }

    @Test
    fun cancellationClearsAnArmedGesture() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 2_000L))
        gesture.cancel()
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 3_000L))
        gesture.start(FloatingDockEdge.BOTTOM, 4_000L)
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 4_999L))
    }

    @Test
    fun releaseOutsideTheEdgeRejectsAStalePreview() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 2_000L))
        assertFalse(gesture.release(null, 2_100L))
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
    @Test fun sideAndTopEdgesNeverArmEvenWhenHeld() {
        for (edge in listOf(FloatingDockEdge.LEFT, FloatingDockEdge.RIGHT, FloatingDockEdge.TOP)) {
            val gesture = FloatingDockGesture()
            gesture.start(edge, 0)
            assertFalse(gesture.update(edge, 2000))
            assertFalse(gesture.release(edge, 3000))
        }
    }

}
