package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpaceKeyGestureTest {
    @get:Rule val rule = createComposeRule()
    private var spaces = 0
    private var voices = 0
    private var density = 1f
    private val moves = mutableListOf<Int>()
    private val rows = mutableListOf<Int>()

    private fun setKey(mode: SpaceHoldAction = SpaceHoldAction.CURSOR, voiceSticky: Boolean = false) {
        rule.setContent {
            density = LocalDensity.current.density
            var cursorActive by remember { mutableStateOf(false) }
            CompositionLocalProvider(
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(spaceHold = mode, cursorStepDp = 10f),
                LocalKeyboardInputActions provides KeyboardInputActions(onCursorMove = { moves += it }, onCursorMoveVertical = { rows += it }, onCursorModeChange = { cursorActive = it },
                    isVoiceMode = voiceSticky, voiceSticky = voiceSticky),
            ) {
                Box(Modifier.size(240.dp, 64.dp)) {
                    SpaceKeyButton(onClick = { spaces++ }, backgroundColor = Color.White, textColor = Color.Black,
                        modifier = Modifier.testTag("space"), onVoiceModeChange = { voices++ })
                    if (cursorActive) CursorControlOverlay(Modifier.matchParentSize())
                }
            }
        }
    }

    @Test fun verticalCursorNeedsTwiceTheHorizontalDistance() {
        setKey(); rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(320L)
        rule.onNodeWithTag("space").performTouchInput { moveTo(center + Offset(0f, 12f * density)) }
        rule.runOnIdle { assertTrue(rows.isEmpty()) }
        rule.onNodeWithTag("space").performTouchInput { moveTo(center + Offset(0f, 22f * density)); up() }
        rule.runOnIdle { assertEquals(listOf(1), rows); assertTrue(moves.isEmpty()); assertEquals(0, spaces) }
    }

    @Test fun tapProducesOneSpace() {
        setKey()
        rule.onNodeWithTag("space").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, spaces); assertTrue(moves.isEmpty()); assertEquals(0, voices) }
    }

    @Test fun idleSpaceUsesAnIconWithoutTheLongPressHint() {
        setKey()
        rule.onNodeWithContentDescription("空格").assertIsDisplayed()
        rule.onNodeWithText("长按滑动光标").assertDoesNotExist()
        rule.onNodeWithText("长按语音").assertDoesNotExist()
    }

    @Test fun holdArmsAt300msAndSmallMovesAccumulateWithoutVoiceOrSpace() {
        setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(320L)
        rule.onNodeWithTag("space").performTouchInput {
            moveTo(center + Offset(6f * density, 0f))
            moveTo(center + Offset(12f * density, 0f))
            moveTo(center + Offset(2f * density, 0f))
            moveTo(center - Offset(2f * density, 0f))
            up()
        }
        rule.runOnIdle { assertEquals(listOf(1, -1), moves); assertEquals(0, spaces); assertEquals(0, voices) }
    }

    @Test fun releasingHeldSpaceWithoutMovingDoesNotInsert() {
        setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(320L)
        rule.onNodeWithTag("space").performTouchInput { up() }
        rule.runOnIdle { assertEquals(0, spaces); assertTrue(moves.isEmpty()) }
    }

    @Test fun cancelledHoldDoesNotInsertAndFollowingTapWorks() {
        setKey()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(320L)
        rule.onNodeWithText("光标调整").assertIsDisplayed()
        rule.onNodeWithTag("space").performTouchInput { cancel() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("cursor-control-overlay").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(500L)
        rule.onNodeWithTag("space").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, spaces); assertTrue(moves.isEmpty()); assertEquals(0, voices) }
    }

    @Test fun repeatedSpacesStopOnReleaseWithoutExtraTap() {
        setKey(SpaceHoldAction.REPEAT)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(470L)
        var before = 0
        rule.runOnIdle { before = spaces; assertTrue(before >= 2) }
        rule.onNodeWithTag("space").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(before, spaces); assertEquals(0, voices) }
    }

    @Test fun toolbarVoiceLeavesSpaceAndCursorControlsAvailable() {
        setKey(voiceSticky = true)
        rule.onNodeWithContentDescription("空格").assertIsDisplayed()
        rule.onNodeWithTag("space").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, spaces); assertEquals(0, voices) }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(320L)
        rule.onNodeWithTag("space").performTouchInput { moveTo(center + Offset(12f * density, 0f)); up() }
        rule.runOnIdle { assertEquals(1, spaces); assertEquals(0, voices); assertEquals(listOf(1), moves) }
    }

    @Test fun repeatedSpacesStopWhenPointerLeavesKey() {
        setKey(SpaceHoldAction.REPEAT)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("space").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(390L)
        rule.onNodeWithTag("space").performTouchInput { moveTo(Offset(center.x, -100f)); up() }
        var stopped = 0
        rule.runOnIdle { stopped = spaces; assertTrue(stopped > 0) }
        rule.mainClock.advanceTimeBy(500L)
        rule.runOnIdle { assertEquals(stopped, spaces) }
    }
}
