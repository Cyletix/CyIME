package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.SwipeState
import com.kingzcheung.xime.ui.keyboard.SwipeableKeyButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verify complete pointer sequences so a gesture cannot commit through multiple callbacks. */
@RunWith(AndroidJUnit4::class)
class SwipeableKeyButtonGestureTest {
    @get:Rule
    val rule = createComposeRule()

    private class Events {
        val commits = mutableListOf<String>()
        var state = SwipeState()
        var presses = 0
        var releases = 0
        var density = 1f
    }

    private fun setKey(
        longPressItems: List<String>? = listOf("q", "Q", "ä"),
        parentConsumesMoves: Boolean = false,
        parentConsumesDown: Boolean = false,
    ): Events {
        val events = Events()
        rule.setContent {
            events.density = LocalDensity.current.density
            val parentInput = if (parentConsumesMoves || parentConsumesDown) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(
                                if (parentConsumesDown) PointerEventPass.Initial else PointerEventPass.Main
                            )
                            event.changes.forEach { change ->
                                if ((parentConsumesDown && change.pressed && !change.previousPressed) ||
                                    (parentConsumesMoves && change.pressed &&
                                        change.position != change.previousPosition)
                                ) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            } else Modifier
            Box(Modifier.size(200.dp).then(parentInput)) {
                SwipeableKeyButton(
                    text = "q",
                    onClick = { events.commits += "tap:q" },
                    backgroundColor = Color.White,
                    textColor = Color.Black,
                    modifier = Modifier.testTag("key"),
                    swipeText = "1",
                    swipeDownText = "!",
                    onSwipe = { events.commits += "up:$it" },
                    onSwipeDown = { events.commits += "down:$it" },
                    onPress = { events.presses++ },
                    onRelease = { events.releases++ },
                    onSwipeStateChange = { state, _ -> events.state = state },
                    longPressItems = longPressItems,
                    onLongPressSelect = { events.commits += "long:$it" },
                    shadowEnabled = false,
                )
            }
        }
        return events
    }

    @Test
    fun tapCommitsExactlyOnceWithLongPressOptions() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf("tap:q"), events.commits)
            assertEquals(1, events.presses)
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test
    fun smallFingerMovementDoesNotLoseTheTap() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center + Offset(4f * events.density, 4f * events.density))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("tap:q"), events.commits) }
    }

    @Test
    fun movementBelowSwipeThresholdStillCommitsOnlyTheTap() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 30f * events.density))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("tap:q"), events.commits) }
    }

    @Test
    fun upwardSwipePreviewsWithoutCommittingThenCommitsOnlySymbolOnRelease() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertTrue(events.state.isSwiping)
            assertEquals("1", events.state.swipeText)
        }
        rule.onNodeWithTag("key").performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(listOf("up:1"), events.commits)
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test
    fun downwardSwipeWithoutLongPressOptionsAlsoWaitsForRelease() {
        val events = setKey(longPressItems = null)
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 75f * events.density))
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertTrue(events.state.isSwipeDown)
            assertEquals("!", events.state.swipeText)
        }
        rule.onNodeWithTag("key").performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf("down:!"), events.commits) }
    }

    @Test
    fun returningInsideTheThresholdCancelsSwipeWithoutFallingBackToTap() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
            moveTo(center)
            up()
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test
    fun cancelledSwipeCommitsNothingAndTheNextTapStillWorks() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
            cancel()
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle { assertEquals(listOf("tap:q"), events.commits) }
    }

    @Test
    fun longPressSelectionCommitsOnlyTheSelectedOptionOnce() {
        val events = setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(450L)
        rule.runOnIdle {
            assertTrue(events.state.isLongPress)
            assertTrue(events.commits.isEmpty())
        }
        rule.onNodeWithTag("key").performTouchInput {
            moveTo(center + Offset(70f * events.density, 0f))
        }
        rule.runOnIdle {
            assertEquals(1, events.state.selectedLongPressIndex)
            assertTrue(events.commits.isEmpty())
        }
        rule.onNodeWithTag("key").performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(listOf("long:Q"), events.commits)
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test
    fun cancelledLongPressDoesNotSelectAnOption() {
        val events = setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(450L)
        rule.runOnIdle { assertTrue(events.state.isLongPress) }
        rule.onNodeWithTag("key").performTouchInput { cancel() }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test
    fun ancestorConsumptionCancelsTapEvenWhenFingerReturnsToTheKey() {
        val events = setKey(parentConsumesMoves = true)
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center + Offset(30f * events.density, 0f))
            moveTo(center)
            up()
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals(1, events.releases)
            assertFalse(events.state.isPressed)
        }
    }

    @Test
    fun alreadyConsumedDownDoesNotStartAKeyGesture() {
        val events = setKey(parentConsumesDown = true)
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals(0, events.presses)
            assertEquals(0, events.releases)
        }
    }
}
