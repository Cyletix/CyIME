package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.EditorActionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorActionKeyTest {
    @get:Rule
    val rule = createComposeRule()

    private class Events {
        var count = 0
        var hide: () -> Unit = {}
    }

    private fun setKey(repeatable: Boolean = true): Events {
        val events = Events()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            events.hide = { visible = false }
            Box(Modifier.size(240.dp)) {
                if (visible) EditorActionKey(
                    icon = Icons.Default.KeyboardArrowLeft,
                    label = "向左",
                    onAction = { events.count++ },
                    background = Color.White,
                    foreground = Color.Black,
                    modifier = Modifier.size(80.dp).testTag("edit-key"),
                    repeatable = repeatable,
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        return events
    }

    @Test
    fun shortTapRunsOnceOnRelease() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100L)
        rule.runOnIdle { assertEquals(0, events.count) }
        rule.onNodeWithTag("edit-key").performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1, events.count) }
    }

    @Test
    fun longPressRepeatsAndReleaseDoesNotAppendAnotherAction() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(520L)
        val repeated = rule.runOnIdle { events.count }
        assertTrue(repeated >= 3)
        rule.onNodeWithTag("edit-key").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(repeated, events.count) }
    }

    @Test
    fun pointerCancellationStopsAnActiveRepeat() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(450L)
        val repeated = rule.runOnIdle { events.count }
        assertTrue(repeated > 0)
        rule.onNodeWithTag("edit-key").performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(repeated, events.count) }
    }

    @Test
    fun cancellationBeforeTheRepeatThresholdDoesNotClick() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100L)
        rule.onNodeWithTag("edit-key").performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(0, events.count) }
    }

    @Test
    fun movingOutsideTheKeyStopsRepeatingEvenBeforeRelease() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(450L)
        val repeated = rule.runOnIdle { events.count }
        rule.onNodeWithTag("edit-key").performTouchInput {
            moveTo(Offset(width * 2f, center.y))
        }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(repeated, events.count) }
        rule.onNodeWithTag("edit-key").performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(repeated, events.count) }
    }

    @Test
    fun disposingTheKeyCancelsItsRepeater() {
        val events = setKey()
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(450L)
        val repeated = rule.runOnIdle { events.count }
        rule.runOnIdle { events.hide() }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(repeated, events.count) }
    }

    @Test
    fun nonRepeatableEditingActionsRunOnlyOnceAfterHolding() {
        val events = setKey(repeatable = false)
        rule.onNodeWithTag("edit-key").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800L)
        rule.runOnIdle { assertEquals(0, events.count) }
        rule.onNodeWithTag("edit-key").performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(1, events.count) }
    }
}
