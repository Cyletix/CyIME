package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the actual board; no database, native input engine or launcher is involved. */
class ClipboardSelectionPinsUiTest {
    @get:Rule val rule = createComposeRule()

    private var pins by mutableStateOf(emptySet<String>())
    private var pasted = ""
    private var removed = emptyList<ClipboardCard>()
    private val keyboard = ClipboardKeyboardNavigation {}

    private fun show(
        items: List<ClipboardItem>,
        width: Int = 360,
        height: Int = 360,
        dark: Boolean = false,
        keyboardActive: Boolean = false,
        fontScale: Float = 1f,
    ) {
        pins = items.filter { it.isPinned }.mapTo(mutableSetOf()) { "text:${it.id}" }
        if (keyboardActive) keyboard.open()
        rule.setContent {
            // Test tablet-width layout on a phone runner without extending off its display.
            CompositionLocalProvider(
                LocalDensity provides Density(.4f, fontScale),
                LocalClipboardKeyboardNavigation provides keyboard,
            ) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        ClipboardBoardView(
                            textItems = items,
                            images = emptyList(),
                            initialImages = false,
                            expanded = false,
                            pins = pins,
                            photoAccess = false,
                            failure = null,
                            onBack = {},
                            onQuickSend = {},
                            onSelectText = { pasted = it },
                            onSelectImage = {},
                            onSplit = { _, _ -> },
                            onAddQuick = {},
                            onPinsChange = { pins = it },
                            onRemove = { removed = it },
                            onPhotoAccess = {},
                            onPick = {},
                            onSystemPaste = {},
                            onShare = { _, _ -> },
                            onPullRemote = null,
                        )
                    }
                }
            }
        }
    }

    private fun selectByLongPress(id: Long) {
        rule.onNodeWithTag("clipboard-card:text:$id").performTouchInput { longClick() }
        rule.onNodeWithText("多选").performScrollTo().performClick()
    }

    @Test fun longPressSelectionShowsBothCheckedAndUncheckedCardsWithoutPasting() {
        show(listOf(ClipboardItem(1, "第一条", timestamp = 2), ClipboardItem(2, "第二条", timestamp = 1)))
        selectByLongPress(1)
        rule.onNodeWithTag("clipboard-exit-selection").assertIsDisplayed()
        rule.onNodeWithTag("clipboard-selection:text:1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("clipboard-card:text:1")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag("clipboard-selection:text:2", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("clipboard-card:text:2").assertIsNotSelected()
        rule.onNodeWithTag("clipboard-card:text:2").performClick()
        rule.onNodeWithTag("clipboard-card:text:2").assertIsSelected()
        rule.runOnIdle { assertEquals("", pasted) }
        rule.onNodeWithTag("clipboard-exit-selection").performClick()
        rule.onNodeWithTag("clipboard-selection:text:1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun selectedPinnedCardCanBeUnpinnedWithoutAffectingOtherPins() {
        show(listOf(
            ClipboardItem(1, "固定一", timestamp = 2, isPinned = true),
            ClipboardItem(2, "固定二", timestamp = 1, isPinned = true),
            ClipboardItem(3, "最近", timestamp = 3),
        ))
        selectByLongPress(1)
        rule.onNodeWithTag("clipboard-pin").assertContentDescriptionEquals("取消固定所选").performClick()
        rule.runOnIdle {
            assertEquals(setOf("text:2"), pins)
            assertEquals("", pasted)
            assertTrue(removed.isEmpty())
        }
    }

    @Test fun selectAllRevealsFoldedRecentCopiesAndCanBeClearedWithoutDeleting() {
        show(pinnedAndRecent(pinCount = 8, recentCount = 10))
        rule.onNodeWithTag("clipboard-card:text:1009").assertDoesNotExist()
        selectByLongPress(1000)
        rule.onNodeWithTag("clipboard-select-all").performClick()
        rule.onNodeWithText("已选 18").assertExists()
        rule.onNodeWithTag("clipboard-records").performScrollToNode(hasTestTag("clipboard-card:text:1009"))
        rule.onNodeWithTag("clipboard-card:text:1009").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithTag("clipboard-select-all").performClick()
        rule.onNodeWithText("已选 0").assertExists()
        rule.onNodeWithTag("clipboard-pin").assertIsNotEnabled()
        rule.onNodeWithTag("clipboard-delete").assertIsNotEnabled()
        rule.runOnIdle { assertTrue(removed.isEmpty()) }
    }

    @Test fun multiDeleteOnlyRemovesCheckedRecordsAfterConfirmation() {
        show(listOf(
            ClipboardItem(1, "第一条", timestamp = 3),
            ClipboardItem(2, "第二条", timestamp = 2),
            ClipboardItem(3, "保留这一条", timestamp = 1),
        ))
        selectByLongPress(1)
        rule.onNodeWithTag("clipboard-card:text:2").performClick()
        rule.onNodeWithTag("clipboard-delete").performClick()
        rule.runOnIdle { assertTrue(removed.isEmpty()) }
        rule.onNodeWithTag("clipboard-confirm-delete").performClick()
        rule.runOnIdle {
            assertEquals(listOf("text:1", "text:2"), removed.map { it.key })
            assertEquals("", pasted)
        }
    }

    @Test fun phoneWidthFoldsExtraRecentCopiesAbovePins() = assertCollapsedLayout(360, 320)

    @Test fun narrowShortPanelFoldsExtraRecentCopiesAbovePins() = assertCollapsedLayout(240, 180)

    @Test fun tabletWidthFoldsExtraRecentCopiesAbovePins() = assertCollapsedLayout(1000, 320, dark = true)

    @Test fun narrowPanelWithLargeFontsKeepsAllSelectionControlsAndCountInsideTheRow() {
        show(pinnedAndRecent(pinCount = 3), width = 240, height = 180, fontScale = 2f)
        selectByLongPress(1000)
        val controls = rule.onNodeWithTag("clipboard-controls").fetchSemanticsNode().boundsInRoot
        val board = rule.onNodeWithTag("clipboard-board").fetchSemanticsNode().boundsInRoot
        val tags = listOf("clipboard-exit-selection", "clipboard-select-all", "clipboard-pin", "clipboard-delete")
        for (tag in tags) {
            val item = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag starts within controls", item.left >= controls.left - 1f && item.top >= controls.top - 1f)
            assertTrue("$tag ends within controls", item.right <= controls.right + 1f && item.bottom <= controls.bottom + 1f)
        }
        val count = rule.onNodeWithText("已选 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Selection count remains visible", count.width > 0f && count.left >= controls.left && count.right <= controls.right)
        assertTrue("Control row cannot grow wider than the panel", controls.left >= board.left && controls.right <= board.right)
    }

    @Test fun selectingAllWithOnlyOnePinDoesNotLeaveAnUnboundedPinnedPreview() {
        show(pinnedAndRecent(pinCount = 1), width = 360, height = 240)
        val before = rule.onNodeWithTag("clipboard-card:text:1").fetchSemanticsNode().boundsInRoot.height
        selectByLongPress(1)
        rule.onNodeWithTag("clipboard-select-all").performClick()
        rule.onNodeWithTag("clipboard-exit-selection").performClick()
        val after = rule.onNodeWithTag("clipboard-card:text:1").fetchSemanticsNode().boundsInRoot.height
        val region = rule.onNodeWithTag("clipboard-records-region").fetchSemanticsNode().boundsInRoot
        assertEquals("Selection state must not resize a pinned preview", before, after, 1f)
        assertTrue(after <= region.height * .35f + 1f)
        rule.onNodeWithTag("clipboard-card:text:1000").assertIsDisplayed()
        rule.onNodeWithTag("clipboard-recent-toggle").assertDoesNotExist()
    }

    private fun assertCollapsedLayout(width: Int, height: Int, dark: Boolean = false) {
        show(pinnedAndRecent(pinCount = 3, recentCount = 40), width = width, height = height, dark = dark)
        val shown = clipboardColumnCount(width.toFloat()) * ClipboardRecentRows
        val region = rule.onNodeWithTag("clipboard-records-region").fetchSemanticsNode().boundsInRoot
        val recent = rule.onNodeWithTag("clipboard-card:text:1000").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Recent copy leads the board", recent.top - region.top < 12f)
        rule.onNodeWithTag("clipboard-card:text:${1000 + shown}").assertDoesNotExist()
        rule.onNodeWithText(if (width >= 360) "更多 ${40 - shown} 条" else "+${40 - shown}", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
        val divider = rule.onNodeWithTag("clipboard-pins-divider").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("clipboard-records").performScrollToKey("text:1")
        val pin = rule.onNodeWithTag("clipboard-card:text:1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Pins follow the divider", pin.top >= divider.top)
        rule.onNodeWithTag("clipboard-records").performScrollToKey("text:3")
        rule.onNodeWithTag("clipboard-card:text:3").assertIsDisplayed()
    }

    @Test fun collapsingRecentRemovesHiddenRecordsFromKeyboardNavigation() {
        show(pinnedAndRecent(pinCount = 2, recentCount = 10), keyboardActive = true)
        rule.onNodeWithTag("clipboard-card:text:1009").assertDoesNotExist()
        rule.onNodeWithTag("clipboard-recent-toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("clipboard-older-divider").assertIsDisplayed()
        rule.onNodeWithTag("clipboard-records").performScrollToKey("text:1009")
        rule.onNodeWithTag("clipboard-card:text:1009").assertIsDisplayed()
        // The toggle stays in the toolbar: collapsing needs no scrolling back from the oldest copy.
        rule.onNodeWithTag("clipboard-recent-toggle").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("clipboard-card:text:1009").assertDoesNotExist()
        rule.onNodeWithTag("clipboard-records").performScrollToIndex(0)
        rule.onNodeWithTag("clipboard-card:text:1000").assertIsFocused()
        rule.runOnIdle { keyboard.dispatch(ClipboardNavigation.CONFIRM) }
        rule.runOnIdle { assertEquals("刚复制的内容", pasted) }
    }

    private fun pinnedAndRecent(pinCount: Int, recentCount: Int = 1): List<ClipboardItem> =
        List(pinCount) { index ->
            ClipboardItem(
                (index + 1).toLong(),
                "固定内容 ${index + 1}\n很长的第二行\n第三行\n第四行\n第五行",
                timestamp = (pinCount - index).toLong(),
                isPinned = true,
            )
        } + List(recentCount) { index ->
            ClipboardItem(1000L + index, if (index == 0) "刚复制的内容" else "最近复制 $index",
                timestamp = 10_000L - index)
        }
}
