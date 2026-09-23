package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeBubbleControllerTest {
    private val bounds = Rect(10f, 20f, 70f, 80f)

    @Test fun disabledTapPreviewDoesNotPublishLayoutStateOrScheduleCleanup() = runTest {
        val controller = SwipeBubbleController(this)
        repeat(100) {
            controller.update(SwipeState(isPressed = true, pressedText = "a"), bounds)
            controller.update(SwipeState(), bounds)
        }
        assertEquals(SwipeState(), controller.state)
        assertEquals(Rect.Zero, controller.keyBounds)
        assertFalse(coroutineContext[Job]!!.children.any())
    }

    @Test fun disabledTapBubblesStillShowSwipeAndLongPressChoices() = runTest {
        val controller = SwipeBubbleController(this)
        controller.update(SwipeState(isSwiping = true, swipeText = "1"), bounds)
        assertEquals("1", controller.state.swipeText)
        assertEquals(bounds, controller.keyBounds)
        controller.update(SwipeState(), bounds)
        assertEquals(SwipeState(), controller.state)
        controller.update(SwipeState(isLongPress = true, longPressItems = listOf("a", "á")), bounds)
        assertTrue(controller.state.isLongPress)
        controller.update(SwipeState(), bounds)
        assertEquals(SwipeState(), controller.state)
        assertFalse(coroutineContext[Job]!!.children.any())
    }

    @Test fun enabledTapRetainsPreviewBrieflyWithoutClearingTheFollowingKey() = runTest {
        val controller = SwipeBubbleController(this, showPressBubble = true)
        controller.update(SwipeState(isPressed = true, pressedText = "a"), bounds)
        controller.update(SwipeState(), bounds)
        runCurrent()
        advanceTimeBy(30)
        assertEquals("a", controller.state.pressedText)
        controller.update(SwipeState(isPressed = true, pressedText = "b"), bounds)
        advanceTimeBy(100)
        runCurrent()
        assertEquals("b", controller.state.pressedText)
        controller.update(SwipeState(), bounds)
        runCurrent()
        advanceTimeBy(60)
        runCurrent()
        assertEquals(SwipeState(), controller.state)
    }
}
