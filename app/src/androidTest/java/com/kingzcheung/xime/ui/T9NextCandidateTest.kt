package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.service.*
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class T9NextCandidateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun zeroChangesToArrowOnlyWhileComposingAndDoesNotCommitUntilSpace() {
        val initial = CandidateState(candidates = listOf("促使一样吗", "不是一样吗", "不是一样嘛"),
            inputText = "28744926462", isComposing = true, engineRevision = 9)
        val candidates = mutableStateOf(CandidateState())
        val ui = KeyboardUiState(currentSchemaId = "t9_pinyin")
        val committed = mutableListOf<String>()
        var scale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                MaterialTheme {
                    Column(Modifier.width(360.dp)) {
                        FixedCandidateStrip(candidates.value.candidates, comments = emptyList(),
                            highlightIndex = candidates.value.highlightedCandidateIndex,
                            visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}), fontSize = 19.sp,
                            modifier = Modifier.fillMaxWidth())
                        val controller = remember { T9InputController() }
                        DisposableEffect(controller) { onDispose { controller.close() } }
                        T9KeyboardLayout(onKeyPress = { key ->
                            if (key == "space") {
                                val state = candidates.value
                                committed += state.candidates[state.spaceCandidateIndex(ui.inputProfile)]
                                candidates.value = CandidateState()
                            }
                        }, callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onCommitText = { committed += it },
                            onNextCandidate = { candidates.value = candidates.value.advanceT9Candidate(ui.inputProfile) },
                            onPreviousCandidate = { candidates.value = candidates.value.retreatT9Candidate(ui.inputProfile) }),
                            uiState = ui, t9Controller = controller,
                            keyBackgroundColor = Color.DarkGray, keyTextColor = Color.White,
                            specialKeyBackgroundColor = Color.Gray, candidateState = candidates,
                            modifier = Modifier.height(260.dp))
                    }
                }
            }
        }
        val zero = rule.onNodeWithTag("t9-zero-key")
        val idleBounds = zero.fetchSemanticsNode().boundsInRoot
        zero.performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("0"), committed); committed.clear(); candidates.value = initial }
        rule.onNodeWithContentDescription("下一项", useUnmergedTree = true).assertExists()
        val composingBounds = zero.fetchSemanticsNode().boundsInRoot
        assertEquals(idleBounds.width, composingBounds.width, .1f)
        assertEquals(idleBounds.height, composingBounds.height, .1f)
        zero.performTouchInput { click() }
        rule.onNodeWithTag("bar-candidate:1").assertIsSelected()
        rule.runOnIdle { assertTrue(committed.isEmpty()); assertEquals(initial.inputText, candidates.value.inputText) }
        // SpaceKeyButton exposes the stable space-key tag used by the existing gesture tests.
        rule.onNodeWithTag("space-key").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("不是一样吗"), committed); candidates.value = initial; scale = 1.5f }
        repeat(2) { zero.performTouchInput { click() } }
        rule.onNodeWithTag("bar-candidate:2").assertIsSelected().assertIsDisplayed()
        val large = zero.fetchSemanticsNode().boundsInRoot
        assertEquals(idleBounds.width, large.width, .1f)
        assertEquals(idleBounds.height, large.height, .1f)
        zero.performTouchInput { down(center); moveTo(center - Offset(75f, 0f)); up() }
        rule.onNodeWithTag("bar-candidate:1").assertIsSelected().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(listOf("不是一样吗"), committed)
            assertEquals(initial.inputText, candidates.value.inputText)
        }
        rule.onNodeWithTag("space-key").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("不是一样吗", "不是一样吗"), committed) }
        zero.performTouchInput { down(center); moveTo(center - Offset(75f, 0f)); up() }
        rule.runOnIdle { assertEquals(2, committed.size) }
    }

    @Test fun candidateWindowSlidesAtItsEdgesInsteadOfPuttingEveryNewSelectionFirst() {
        val words = List(12) { "候选词${it}" }
        val focused = mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme {
                FixedCandidateStrip(words, comments = emptyList(), highlightIndex = focused.intValue,
                    visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray),
                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}), fontSize = 19.sp,
                    modifier = Modifier.width(230.dp))
            }
        }
        fun visible() = words.indices.filter {
            rule.onAllNodesWithTag("bar-candidate:$it").fetchSemanticsNodes().isNotEmpty()
        }
        val initial = visible()
        assertTrue(initial.size >= 2)
        val next = initial.last() + 1
        rule.runOnIdle { focused.intValue = next }
        val afterNext = visible()
        assertEquals(next, afterNext.last())
        assertTrue(afterNext.first() < next)
        rule.onNodeWithTag("bar-candidate:$next").assertIsSelected().assertIsDisplayed()
        rule.runOnIdle { focused.intValue = next - 1 }
        assertEquals(afterNext, visible())
        val previous = afterNext.first() - 1
        rule.runOnIdle { focused.intValue = previous }
        assertEquals(previous, visible().first())
        rule.onNodeWithTag("bar-candidate:$previous").assertIsSelected().assertIsDisplayed()
    }
}
