package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.SwipeableIconKeyButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeableIconKeyButtonTest {
    @get:Rule val rule = createComposeRule()

    @Test fun verticalActionsPreviewInsideTheHeldKeyAndCommitOnlyOnRelease() {
        var clears = 0
        var undo = 0
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(240.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black), onClick = {},
                    onSwipeUp = { clears++ }, onSwipeDown = { undo++ },
                    swipeUpLabel = "上滑清空", swipeDownLabel = "下滑撤回",
                    backgroundColor = Color.Gray, iconColor = Color.White,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.mainClock.autoAdvance = false
        val key = rule.onNodeWithTag("delete")
        val bounds = key.fetchSemanticsNode().boundsInRoot
        key.performTouchInput { down(center); moveTo(center - Offset(0f, 65f * density)) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("key-flick-preview").assertTextEquals("清空")
        key.performTouchInput { moveTo(center - Offset(0f, 95f * density)) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("key-flick-preview").assertTextEquals("清空")
        assertEquals(bounds, key.fetchSemanticsNode().boundsInRoot)
        assertEquals(0, clears)
        key.performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(1, clears)
        rule.onNodeWithTag("key-flick-preview").assertDoesNotExist()
        key.performTouchInput { down(center); moveTo(center + Offset(0f, 95f * density)) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("key-flick-preview").assertTextEquals("撤回")
        key.performTouchInput { moveTo(center); up() }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0, undo)
    }

    @Test fun movementBeforeAndDuringHoldDoesNotInterruptRepeat() {
        var deletes = 0
        var clears = 0
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(200.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black), onClick = { deletes++ },
                    onLongClick = { deletes++ }, onSwipeUp = { clears++ },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("delete").performTouchInput {
            down(center)
            moveTo(center + Offset(20f * density, 20f * density))
        }
        rule.mainClock.advanceTimeBy(750)
        val beforeMove = deletes
        org.junit.Assert.assertTrue(beforeMove > 1)
        rule.onNodeWithTag("delete").performTouchInput {
            moveTo(center + Offset(25f * density, -35f * density))
        }
        rule.mainClock.advanceTimeBy(200)
        org.junit.Assert.assertTrue(deletes > beforeMove)
        rule.onNodeWithTag("delete").performTouchInput { up() }
        val stopped = deletes
        rule.mainClock.advanceTimeBy(200)
        assertEquals(stopped, deletes)
        assertEquals(0, clears)
    }

    @Test fun clearWaitsForLargeSwipeReleaseAndCancelledSwipeDoesNothing() {
        var clears = 0
        var taps = 0
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(240.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black), onClick = { taps++ },
                    onSwipeUp = { clears++ },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.onNodeWithTag("delete").performTouchInput {
            down(center); moveTo(center - Offset(0f, 95f * density))
        }
        rule.runOnIdle { assertEquals(0, clears) }
        rule.onNodeWithTag("delete").performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, clears); assertEquals(0, taps) }
        rule.onNodeWithTag("delete").performTouchInput {
            down(center); moveTo(center - Offset(0f, 95f * density)); cancel()
        }
        rule.runOnIdle { assertEquals(1, clears); assertEquals(0, taps) }
        rule.onNodeWithTag("delete").performTouchInput {
            down(center); moveTo(center - Offset(0f, 95f * density)); moveTo(center); up()
        }
        rule.runOnIdle { assertEquals(1, clears); assertEquals(0, taps) }
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun tapUsesCurrentInputSessionAfterRecomposition() {
        var session by mutableIntStateOf(0)
        val events = mutableListOf<String>()
        rule.setContent {
            val capturedSession = session
            Box(Modifier.size(64.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black),
                    onClick = { events += "tap:$capturedSession" },
                    onPress = { events += "press:$capturedSession" },
                    onRelease = { events += "release:$capturedSession" },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle { session = 1; events.clear() }
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(setOf("press:1", "release:1", "tap:1"), events.toSet())
            assertEquals(3, events.size)
        }
    }

    @Test fun repeatUsesUpdatedCallbackDuringHold() {
        var session by mutableIntStateOf(0)
        val sessions = mutableListOf<Int>()
        rule.setContent {
            val capturedSession = session
            Box(Modifier.size(64.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black), onClick = {},
                    onLongClick = { sessions += capturedSession },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("delete").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        org.junit.Assert.assertTrue(sessions.isNotEmpty())
        rule.runOnIdle { session = 1 }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { sessions.clear() }
        rule.mainClock.advanceTimeBy(150)
        rule.onNodeWithTag("delete").performTouchInput { up() }
        rule.runOnIdle {
            org.junit.Assert.assertTrue(sessions.isNotEmpty())
            org.junit.Assert.assertTrue(sessions.all { it == 1 })
        }
    }

    @Test fun repeatedDeleteStopsOnReleaseAndTheNextTapStillWorks() {
        var deletes = 0
        var feedback = 0
        rule.setContent {
            Box(Modifier.size(64.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black),
                    onClick = { deletes++ }, onLongClick = { deletes++ }, onPress = { feedback++ },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("delete").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithTag("delete").performTouchInput { up() }
        val afterRelease = deletes
        val feedbackAfterRelease = feedback
        assertEquals("initial press plus every repeated deletion", deletes + 1, feedback)
        org.junit.Assert.assertTrue(afterRelease > 1)
        rule.mainClock.advanceTimeBy(150)
        assertEquals(afterRelease, deletes)
        assertEquals(feedbackAfterRelease, feedback)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(afterRelease + 1, deletes)
            assertEquals(feedbackAfterRelease + 1, feedback)
        }
    }

}
