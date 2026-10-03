package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClipboardKeyboardNavigationUiTest {
    @get:Rule val rule = createComposeRule()
    private var pasted = ""
    private var closed = 0
    private val keyboard = ClipboardKeyboardNavigation { closed++ }
    private fun show() {
        keyboard.open()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalClipboardKeyboardNavigation provides keyboard) {
                MaterialTheme { Box(Modifier.requiredSize(360.dp, 360.dp)) {
                    ClipboardBoardView(List(12) { ClipboardItem(it.toLong(), "记录$it", timestamp = 20L - it) },
                        emptyList(), initialImages = false, expanded = false, pins = emptySet(), photoAccess = false,
                        failure = null, onBack = {}, onQuickSend = {}, onSelectText = { pasted = it },
                        onSelectImage = {}, onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null)
                } }
            }
        }
    }
    @Test fun firstItemIsFocusedAndRapidMoveThenConfirmPastesOnlySelectedItem() {
        show()
        rule.onNodeWithTag("clipboard-card:text:0").assertIsFocused()
        rule.runOnIdle {
            keyboard.dispatch(ClipboardNavigation.RIGHT)
            keyboard.dispatch(ClipboardNavigation.CONFIRM)
            keyboard.dispatch(ClipboardNavigation.CONFIRM)
        }
        rule.runOnIdle { assertEquals("记录1", pasted); assertEquals(1, closed); assertFalse(keyboard.active) }
    }
    @Test fun navigationScrollsPastHeadersAndEscapeDoesNotPaste() {
        show()
        repeat(4) {
            rule.runOnIdle { keyboard.dispatch(ClipboardNavigation.DOWN) }
            rule.waitForIdle()
        }
        rule.onAllNodes(isFocused()).assertCountEquals(1)
        rule.onAllNodes(isFocused())[0].assertIsDisplayed()
        rule.runOnIdle { keyboard.dispatch(ClipboardNavigation.CANCEL) }
        rule.runOnIdle { assertEquals("", pasted); assertEquals(1, closed) }
    }
}
