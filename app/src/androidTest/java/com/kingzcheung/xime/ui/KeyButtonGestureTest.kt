package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.KeyButton
import com.kingzcheung.xime.ui.keyboard.SwipeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The legacy KeyButton must obey the same exclusive release contract as configured keys. */
@RunWith(AndroidJUnit4::class)
class KeyButtonGestureTest {
    @get:Rule val rule = createComposeRule()

    private class Events {
        val commits = mutableListOf<String>()
        var state = SwipeState()
        var density = 1f
        var presses = 0
        var releases = 0
    }

    private fun setKey(): Events {
        val events = Events()
        rule.setContent {
            events.density = LocalDensity.current.density
            Box(Modifier.size(200.dp)) {
                KeyButton(
                    text = "q", onClick = { events.commits += "tap:q" },
                    backgroundColor = Color.White, textColor = Color.Black,
                    modifier = Modifier.testTag("key"), swipeText = "1", swipeDownText = "!",
                    onSwipe = { events.commits += "up:$it" },
                    onSwipeDown = { events.commits += "down:$it" },
                    onSwipeStateChange = { events.state = it },
                    onPress = { events.presses++ }, onRelease = { events.releases++ },
                    onLongClick = { events.commits += "long:q" }, shadowEnabled = false,
                )
            }
        }
        return events
    }

    @Test fun upwardSwipeWaitsForReleaseAndCannotAlsoTap() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
        }
        rule.runOnIdle {
            assertTrue(events.commits.isEmpty())
            assertEquals("1", events.state.swipeText)
        }
        rule.onNodeWithTag("key").performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(listOf("up:1"), events.commits)
            assertEquals(1, events.presses)
            assertEquals(1, events.releases)
            assertEquals(SwipeState(), events.state)
        }
    }

    @Test fun downwardSwipeWaitsForReleaseAndCannotAlsoTap() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 75f * events.density))
        }
        rule.runOnIdle { assertTrue(events.commits.isEmpty()) }
        rule.onNodeWithTag("key").performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf("down:!"), events.commits) }
    }

    @Test fun returningToCenterAfterSwipeDoesNotProduceAnAccidentalLetter() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
            moveTo(center)
            up()
        }
        rule.runOnIdle { assertTrue(events.commits.isEmpty()) }
    }

    @Test fun cancelledSwipeDoesNotCommitAndNextTapCommitsOnce() {
        val events = setKey()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * events.density))
            cancel()
        }
        rule.runOnIdle { assertTrue(events.commits.isEmpty()) }
        rule.onNodeWithTag("key").performTouchInput { down(center); up() }
        rule.runOnIdle {
            assertEquals(listOf("tap:q"), events.commits)
            assertEquals(2, events.releases)
        }
    }

    @Test fun longPressDoesNotAlsoTapOrSwipeOnRelease() {
        val events = setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(650L)
        rule.runOnIdle { assertEquals(listOf("long:q"), events.commits) }
        rule.onNodeWithTag("key").performTouchInput {
            moveTo(center - Offset(0f, 75f * events.density))
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf("long:q"), events.commits)
            assertEquals(1, events.releases)
        }
    }
}
