package com.kingzcheung.xime.ui

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.service.HardwareCursorAnchor
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardCandidateBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

/** Measures the real Compose card; does not assume the estimated width equals its size. */
class HardwareCandidatePositionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun followsMovingCaretAndFallsBackWithinOffsetHost() {
        val anchor = mutableStateOf<HardwareCursorAnchor?>(null)
        val viewLocation = IntArray(2)
        rule.setContent {
            val view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(600.dp, 420.dp).padding(start = 40.dp, top = 30.dp)
                        .onGloballyPositioned { view.getLocationOnScreen(viewLocation) }) {
                        HardwareKeyboardCandidateBar(
                            inputText = "nihao", preeditText = "ni hao", candidates = listOf("你好", "拟好"),
                            hasNextPage = false, hasPrevPage = false, cursorAnchor = anchor.value,
                            highlightIndex = 0, cardBackgroundColor = Color.Black,
                            candidateTextColor = Color.White, activeColor = Color.Blue,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val host = rule.onNodeWithTag("hardware-candidate-host", true).fetchSemanticsNode().boundsInRoot
        fun card() = rule.onNodeWithTag("hardware-candidate-card", true).fetchSemanticsNode().boundsInRoot
        fun move(x: Float, top: Float, bottom: Float) {
            rule.runOnIdle {
                anchor.value = HardwareCursorAnchor(
                    viewLocation[0] + host.left + x, viewLocation[1] + host.top + top,
                    viewLocation[0] + host.left + x, viewLocation[1] + host.top + bottom,
                )
            }
            rule.waitForIdle()
        }
        move(50f, 60f, 80f)
        assertEquals(host.left + 50f, card().left, 1f)
        assertEquals(host.top + 88f, card().top, 1f)
        move(90f, 120f, 140f)
        assertEquals(host.left + 90f, card().left, 1f)
        assertEquals(host.top + 148f, card().top, 1f)
        move(host.width - 5f, host.height - 30f, host.height - 10f)
        assertEquals(host.right - 8f, card().right, 1f)
        assertTrue(card().bottom <= host.bottom - 38f + 1f)
        rule.runOnIdle { anchor.value = null }
        rule.waitForIdle()
        assertEquals(host.center.x, card().center.x, 1f)
        assertEquals(host.bottom - 8f, card().bottom, 1f)
    }

    @Test fun reportsMeasuredBoundsSelectsIndexAndClearsWhenContentDisappears() {
        val visible = mutableStateOf(true)
        val hasContent = mutableStateOf(true)
        var reportedBounds: Rect? = null
        var selectedIndex: Int? = null
        var underlyingClicks = 0
        var hostWindowBounds = androidx.compose.ui.geometry.Rect.Zero
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(500.dp, 400.dp).padding(start = 30.dp, top = 20.dp)) {
                        Box(Modifier.fillMaxSize().testTag("underlying-editor")
                            .clickable { underlyingClicks++ }
                            .onGloballyPositioned { hostWindowBounds = it.boundsInWindow() })
                        if (visible.value) {
                            HardwareKeyboardCandidateBar(
                                inputText = if (hasContent.value) "nihao" else "", preeditText = "",
                                candidates = if (hasContent.value) listOf("你好", "拟好") else emptyList(),
                                hasNextPage = false, hasPrevPage = false, cursorAnchor = null,
                                highlightIndex = 0, cardBackgroundColor = Color.Black,
                                candidateTextColor = Color.White, activeColor = Color.Blue,
                                onCandidateSelect = { selectedIndex = it },
                                onBoundsChanged = { reportedBounds = it },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val host = rule.onNodeWithTag("hardware-candidate-host", true).fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("hardware-candidate-card", true).fetchSemanticsNode().boundsInRoot
        val offsetX = hostWindowBounds.left - host.left
        val offsetY = hostWindowBounds.top - host.top
        assertEquals(Rect(floor(card.left + offsetX).toInt(), floor(card.top + offsetY).toInt(),
            ceil(card.right + offsetX).toInt(), ceil(card.bottom + offsetY).toInt()), reportedBounds)
        rule.onNodeWithTag("hardware-candidate-1", true).performClick()
        rule.runOnIdle { assertEquals(1, selectedIndex) }
        // The full-screen placement host must not absorb touches outside its card.
        rule.onNodeWithTag("underlying-editor", true).performTouchInput { click(Offset(10f, 10f)) }
        rule.runOnIdle {
            assertEquals(1, underlyingClicks)
            hasContent.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(reportedBounds); hasContent.value = true }
        rule.waitForIdle()
        rule.runOnIdle { assertNotNull(reportedBounds); visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(reportedBounds) }
    }

    @Test fun fallbackAvoidsWindowSpaceToolbarInsideOffsetHost() {
        val toolbarBounds = mutableStateOf<Rect?>(null)
        var candidateBounds: Rect? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(500.dp, 400.dp).padding(start = 40.dp, top = 30.dp)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            toolbarBounds.value = Rect(bounds.center.x.toInt() - 110,
                                bounds.bottom.toInt() - 60, bounds.center.x.toInt() + 110,
                                bounds.bottom.toInt() - 8)
                        }) {
                        HardwareKeyboardCandidateBar(
                            inputText = "nihao", preeditText = "ni hao", candidates = listOf("你好", "拟好"),
                            hasNextPage = false, hasPrevPage = false, cursorAnchor = null,
                            highlightIndex = 0, cardBackgroundColor = Color.Black,
                            candidateTextColor = Color.White, activeColor = Color.Blue,
                            onBoundsChanged = { candidateBounds = it }, avoidBoundsInWindow = toolbarBounds.value,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            val toolbar = requireNotNull(toolbarBounds.value)
            val candidate = requireNotNull(candidateBounds)
            assertTrue(!Rect.intersects(toolbar, candidate))
            assertTrue(candidate.bottom <= toolbar.top - 8)
        }
    }
}
