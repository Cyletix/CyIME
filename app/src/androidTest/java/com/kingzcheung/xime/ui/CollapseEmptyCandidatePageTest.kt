package com.kingzcheung.xime.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.ui.keyboard.CollapseEmptyCandidatePage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollapseEmptyCandidatePageTest {
    @get:Rule val rule = createComposeRule()

    @Test fun typingDoesNotRecomposeHostAndOnlyEmptyExpandedPageCollapses() {
        val candidates = mutableStateOf(CandidateState())
        val expanded = mutableStateOf(false)
        var compositions = 0
        var collapses = 0
        rule.setContent {
            SideEffect { compositions++ }
            CollapseEmptyCandidatePage(expanded.value, candidates) { collapses++ }
        }
        var initial = 0
        rule.runOnIdle { initial = compositions }
        listOf("t", "te", "tes", "test", "").forEach { prefix ->
            rule.runOnIdle { candidates.value = CandidateState(pendingEnglishText = prefix) }
            rule.waitForIdle()
        }
        rule.runOnIdle {
            assertEquals(initial, compositions)
            assertEquals(0, collapses)
            candidates.value = CandidateState(candidates = listOf("测试"))
            expanded.value = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            initial = compositions
            candidates.value = CandidateState(associationCandidates = listOf("一下"))
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, collapses)
            assertEquals(initial, compositions)
            candidates.value = CandidateState()
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, collapses)
            assertEquals(initial, compositions)
            expanded.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle { expanded.value = true }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, collapses) }
    }
}
