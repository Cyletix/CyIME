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
    fun holdingAnEdgeArmsAfterHalfASecondAndOnlyReleaseConfirms() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 100L)
        assertFalse(gesture.update(FloatingDockEdge.BOTTOM, 599L))
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 600L))
        assertTrue(gesture.release(FloatingDockEdge.BOTTOM, 700L))
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 800L))
    }

    @Test
    fun releasingBeforeTheThresholdKeepsFloating() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 499L))
    }

    @Test
    fun leavingAnEdgeClearsThePreviewAndRestartsTheTimerOnReentry() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 1_000L))
        assertFalse(gesture.update(null, 1_100L))
        assertFalse(gesture.update(FloatingDockEdge.BOTTOM, 1_200L))
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 1_699L))
    }

    @Test
    fun movingToAnotherEdgeDoesNotCarryOverTheElapsedTime() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 1_000L))
        assertFalse(gesture.update(FloatingDockEdge.TOP, 1_100L))
        assertFalse(gesture.release(FloatingDockEdge.TOP, 1_500L))
    }

    @Test
    fun cancellationClearsAnArmedGesture() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 1_000L))
        gesture.cancel()
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 2_000L))
        gesture.start(FloatingDockEdge.BOTTOM, 3_000L)
        assertFalse(gesture.release(FloatingDockEdge.BOTTOM, 3_499L))
    }

    @Test
    fun releaseOutsideTheEdgeRejectsAStalePreview() {
        val gesture = FloatingDockGesture()
        gesture.start(FloatingDockEdge.BOTTOM, 0L)
        assertTrue(gesture.update(FloatingDockEdge.BOTTOM, 1_000L))
        assertFalse(gesture.release(null, 1_100L))
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
