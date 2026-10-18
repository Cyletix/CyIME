package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClipboardMotionRenderingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cardsKeepTheirSizeAndGridPositionsThroughoutDragSnapAndCancellation() {
        var panel: ClipboardPanelExpansion? = null
        val expanded = mutableStateOf(false)
        val items = listOf(ClipboardItem(1, "固定内容", timestamp = 100),
            ClipboardItem(30, "很长的普通记录不会在展开后从四行变成八行。".repeat(16), timestamp = 90)) +
            (2L..25L).map { ClipboardItem(it, "历史内容 $it", timestamp = it) }
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    val expansion = rememberClipboardPanelExpansion(true, 0L, ClipboardPanelBounds(220, 440),
                        expanded.value) { expanded.value = it }!!
                    panel = expansion
                    CompositionLocalProvider(LocalClipboardPanelExpansion provides expansion) {
                        Box(Modifier.requiredSize(360.dp, 600.dp), contentAlignment = Alignment.BottomCenter) {
                            Column(Modifier.fillMaxWidth().height(expansion.height.dp)) {
                                Spacer(Modifier.height(44.dp))
                                ClipboardBoardView(items, emptyList(), false, expanded.value, setOf("text:1"),
                                    photoAccess = false, failure = null, onBack = {}, onQuickSend = {},
                                    onSelectText = {}, onSelectImage = {}, onSplit = { _, _ -> }, onAddQuick = {},
                                    onPinsChange = {}, onRemove = {}, onPhotoAccess = {}, onPick = {}, onSystemPaste = {},
                                    onShare = { _, _ -> }, onPullRemote = null, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        fun height(tag: String) = rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        fun relativeTop(tag: String) = rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot().top.value -
            rule.onNodeWithTag("clipboard-records-region", true).getUnclippedBoundsInRoot().top.value
        val originalCard = height("clipboard-card:text:1")
        val originalText = height("clipboard-card:text:30")
        val originalTextTop = relativeTop("clipboard-card:text:30")
        fun assertStableCards() {
            assertEquals("fixed preview height must not depend on panel height", originalCard, height("clipboard-card:text:1"), 1f)
            assertEquals("expanding must not increase ordinary text's line count", originalText, height("clipboard-card:text:30"), 1f)
            assertEquals("history must keep the same grid position", originalTextTop, relativeTop("clipboard-card:text:30"), 1f)
        }
        val originalViewport = height("clipboard-records-region")
        rule.runOnIdle { panel!!.beginDrag(500f) }
        for (travel in 10..200 step 10) {
            rule.runOnIdle { panel!!.dragTo(500f - travel) }
            assertStableCards()
            assertEquals(originalViewport + travel, height("clipboard-records-region"), 1f)
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { panel!!.finishDrag() }
        rule.mainClock.advanceTimeBy(80L)
        assertStableCards()
        val intermediateViewport = height("clipboard-records-region")
        assertTrue("only the viewport continues its expansion animation",
            intermediateViewport > originalViewport + 200f && intermediateViewport < originalViewport + 220f)
        rule.mainClock.advanceTimeBy(500L)
        assertStableCards()
        assertEquals(originalViewport + 220f, height("clipboard-records-region"), 1f)
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { panel!!.beginDrag(500f); panel!!.dragTo(620f) }
        assertStableCards()
        rule.runOnIdle { panel!!.finishDrag(cancelled = true) }
        rule.waitForIdle()
        assertStableCards()
        rule.runOnIdle { assertFalse(panel!!.isInMotion) }
        rule.runOnIdle { panel!!.beginDrag(500f) }
        for (travel in 10..200 step 10) {
            rule.runOnIdle { panel!!.dragTo(500f + travel) }
            assertStableCards()
        }
        rule.runOnIdle { panel!!.finishDrag() }
        rule.waitForIdle()
        assertStableCards()
        assertEquals(originalViewport, height("clipboard-records-region"), 1f)
        rule.runOnIdle { assertFalse(panel!!.isInMotion); panel!!.dispose() }
    }

    @Test fun coveredKeyboardIsNotRemeasuredForEveryClipboardHeightAndRestoresWhenClosed() {
        var panel: ClipboardPanelExpansion? = null
        var measurements = 0
        val active = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val scope = rememberCoroutineScope()
                val expansion = remember { ClipboardPanelExpansion(ClipboardPanelBounds(220, 440), scope) {} }
                panel = expansion
                val spyPolicy = remember { MeasurePolicy { _, constraints ->
                    measurements++
                    layout(constraints.maxWidth, constraints.maxHeight) {}
                } }
                val total = if (active.value) expansion.height.toInt() else 220
                Box(Modifier.requiredSize(360.dp, 600.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().height(total.dp)) {
                        Spacer(Modifier.height(44.dp))
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("underlay-slot")) {
                            Box(Modifier.clipboardUnderlaySize(expansion.takeIf { active.value }, total).fillMaxSize()) {
                                Layout(content = {}, measurePolicy = spyPolicy,
                                    modifier = Modifier.fillMaxSize().testTag("measured-keys"))
                            }
                        }
                    }
                }
            }
        }
        val initialMeasures = rule.runOnIdle { measurements }
        rule.runOnIdle { panel!!.beginDrag(500f) }
        for (travel in 10..200 step 10) {
            rule.runOnIdle { panel!!.dragTo(500f - travel) }
            rule.waitForIdle()
        }
        rule.runOnIdle { assertEquals("covered keys must retain their measurement through the drag", initialMeasures, measurements) }
        rule.runOnIdle { active.value = false; panel!!.dispose() }
        assertEquals(176f, rule.onNodeWithTag("underlay-slot").fetchSemanticsNode().boundsInRoot.height, 1f)
        rule.runOnIdle { assertFalse(panel!!.isInMotion) }
    }
}
