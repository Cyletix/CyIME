package com.kingzcheung.xime.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.JapaneseKanaAction
import com.kingzcheung.xime.ui.keyboard.KanaFlickButton
import com.kingzcheung.xime.ui.keyboard.japaneseKanaKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JapaneseKanaGestureTest {
    @get:Rule val rule = createComposeRule()
    private val actions = mutableListOf<JapaneseKanaAction>()
    private var density = 1f
    private var parentCursorMoves = 0

    private fun setKey(romaji: String = "a") {
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed && !change.isConsumed && kotlin.math.abs(change.position.x - down.position.x) > 60.dp.toPx()) parentCursorMoves++
                    } while (change.pressed)
                }
            }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                KanaFlickButton(japaneseKanaKeys.first { it.center.romaji == romaji }, { actions += it },
                    Color.White, Color.Black, Modifier.size(140.dp).testTag("kana"), shadowEnabled = false)
            }
        }
    }

    @Test fun heldKeyShowsAllFiveChoicesAndFlickPreviewTracksEveryDirectionThenReturns() {
        setKey("ta")
        val key = rule.onNodeWithTag("kana")
        key.performTouchInput { down(center) }
        val preview = rule.onNodeWithTag("kana-flick-preview")
        preview.assertIsDisplayed()
        for (label in listOf("ち", "つ", "て", "と")) rule.onNodeWithText(label).assertIsDisplayed()
        assertEquals("た", preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val directions = listOf(Triple(Offset(-40f, 0f), "LEFT", "ち"), Triple(Offset(0f, -40f), "UP", "つ"),
            Triple(Offset(40f, 0f), "RIGHT", "て"), Triple(Offset(0f, 40f), "DOWN", "と"))
        for ((delta, direction, label) in directions) {
            key.performTouchInput { moveTo(center + delta * density) }
            assertEquals(label, preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            rule.onNodeWithTag("kana-direction:$direction", useUnmergedTree = true).assertIsDisplayed()
            rule.runOnIdle { assertTrue(actions.isEmpty()) }
        }
        key.performTouchInput { moveTo(center) }
        assertEquals("た", preview.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        key.performTouchInput { up() }
        preview.assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("ta")), actions) }
    }

    @Test fun punctuationOnlyShowsCenterAtRestAndCommitsTheFinalFlick() {
        setKey(",")
        rule.onNodeWithText("、").assertIsDisplayed()
        for (label in listOf("。", "？", "！", "・")) rule.onNodeWithText(label).assertDoesNotExist()
        val key = rule.onNodeWithTag("kana")
        key.performTouchInput { down(center); moveTo(center + Offset(0f, -40f * density)) }
        assertEquals("？", rule.onNodeWithTag("kana-flick-preview").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        key.performTouchInput { moveTo(center + Offset(40f * density, 0f)); up() }
        rule.onNodeWithTag("kana-flick-preview").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("!")), actions) }
    }

    @Test fun directionBubbleAnimatesInsteadOfJumpingToTheFinalSize() {
        setKey("ta")
        rule.mainClock.autoAdvance = false
        val key = rule.onNodeWithTag("kana")
        key.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200)
        val bubble = rule.onNodeWithTag("kana-preview-bubble", useUnmergedTree = true)
        val initial = bubble.fetchSemanticsNode().boundsInRoot.width
        key.performTouchInput { moveTo(center + Offset(0f, -40f * density)) }
        rule.mainClock.advanceTimeBy(48)
        val intermediate = bubble.fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(120)
        val finalWidth = bubble.fetchSemanticsNode().boundsInRoot.width
        assertTrue(initial > intermediate && intermediate > finalWidth)
        key.performTouchInput { cancel() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("kana-flick-preview").assertDoesNotExist()
        rule.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun tapCommitsTheBaseKanaExactlyOnceOnRelease() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput { down(center) }
        rule.runOnIdle { assertTrue(actions.isEmpty()) }
        rule.onNodeWithTag("kana").performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("a")), actions) }
    }

    @Test fun allFourFlicksOnlyCommitTheSelectedKanaOnRelease() {
        setKey()
        listOf(Offset(-35f, 0f) to "i", Offset(0f, -35f) to "u", Offset(35f, 0f) to "e", Offset(0f, 35f) to "o").forEachIndexed { index, (delta, romaji) ->
            rule.onNodeWithTag("kana").performTouchInput {
                down(center)
                moveTo(center + delta * density)
            }
            rule.runOnIdle { assertEquals(index, actions.size) }
            rule.onNodeWithTag("kana").performTouchInput { up() }
            rule.runOnIdle { assertEquals(JapaneseKanaAction.Input(romaji), actions.last()) }
        }
        rule.runOnIdle { assertEquals(4, actions.size) }
    }

    @Test fun cancelledFlickDoesNotCommitAndTheNextTapStillWorks() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(40f * density, 0f))
            cancel()
        }
        rule.runOnIdle { assertTrue(actions.isEmpty()) }
        rule.onNodeWithTag("kana").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("a")), actions) }
    }

    @Test fun slightMovementRemainsABaseKanaTap() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(5f * density, -4f * density))
            up()
        }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("a")), actions) }
    }

    @Test fun returningToTheCenterBeforeReleaseChoosesTheBaseKana() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(40f * density, 0f))
            moveTo(center)
            up()
        }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("a")), actions) }
    }

    @Test fun anUnassignedFlickDoesNotAccidentallyCommitWa() {
        setKey("wa")
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 35f * density))
            up()
        }
        rule.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun returningToTheKeyCenterAfterAnOffCenterPressChoosesBaseKana() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center + Offset(-30f * density, 0f))
            moveTo(center + Offset(50f * density, 0f))
            moveTo(center)
            up()
        }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("a")), actions) }
    }

    @Test fun changingDirectionUsesOnlyTheReleasePosition() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(-40f * density, 0f))
            moveTo(center)
            moveTo(center + Offset(0f, -40f * density))
            up()
        }
        rule.runOnIdle { assertEquals(listOf(JapaneseKanaAction.Input("u")), actions) }
    }

    @Test fun wideHorizontalFlickDoesNotMoveTheParentEditorCursor() {
        setKey()
        rule.onNodeWithTag("kana").performTouchInput {
            down(center)
            moveTo(center + Offset(90f * density, 0f))
            up()
        }
        rule.runOnIdle {
            assertEquals(0, parentCursorMoves)
            assertEquals(listOf(JapaneseKanaAction.Input("e")), actions)
        }
    }
}
