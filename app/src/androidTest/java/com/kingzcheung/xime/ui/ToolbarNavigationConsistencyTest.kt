package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.*
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ToolbarNavigationConsistencyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun handwritingAndPanelsKeepTheToolbarSlotsAndHideButtonFrame() {
        val page = mutableStateOf<KeyboardPage>(KeyboardPage.Main(MainType.FULL))
        val buttons = listOf(ToolbarButton.SCHEMA, ToolbarButton.EMOJI, ToolbarButton.EDIT, ToolbarButton.HANDWRITING_LOOKUP)
        rule.setContent { MaterialTheme {
            CandidateBar(state = CandidateBarState.Idle, page = page.value,
                toolbarActions = buttons.map { ToolbarAction(ToolbarButtonItem.Builtin(it)) {} },
                visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray),
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onBack = {}, onHideKeyboard = {}),
                modifier = Modifier.width(360.dp))
        } }
        fun positions() = buttons.map { rule.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot }
        val initial = positions()
        val leading = rule.onNodeWithTag("toolbar-leading").fetchSemanticsNode().boundsInRoot
        val hide = rule.onNodeWithTag("toolbar-hide").fetchSemanticsNode().boundsInRoot
        assertEquals(leading.width, hide.width, 0f)
        assertEquals(leading.height, hide.height, 0f)
        val pixels = rule.onNodeWithTag("toolbar-hide").captureToImage().toPixelMap()
        assertNotEquals("收起按钮的圆形底板在未按下时也必须可见", pixels[0, 0], pixels[pixels.width / 2, pixels.height / 4])
        for (next in listOf(KeyboardPage.Main(MainType.HANDWRITING),
            KeyboardPage.Overlay(OverlayRoute.SchemaList, emptyList(), KeyboardPage.Main(MainType.FULL)),
            KeyboardPage.Overlay(OverlayRoute.Emoji, emptyList(), KeyboardPage.Main(MainType.FULL)))) {
            rule.runOnIdle { page.value = next }
            assertEquals(initial, positions())
            rule.onAllNodesWithContentDescription("返回").assertCountEquals(1)
            assertEquals(leading, rule.onNodeWithTag("toolbar-leading").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun resizeFromEmojiClosesThePanelBeforeOpeningControls() {
        val vm = KeyboardViewModel(ApplicationProvider.getApplicationContext<Application>())
        var resized = false
        rule.setContent { MaterialTheme {
            KeyboardView(vm, KeyboardUiState(toolbarButtons = listOf("emoji", "float")),
                KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}, onKeyboardResize = {
                    assertFalse(vm.page.value is KeyboardPage.Overlay); resized = true
                }), modifier = Modifier.fillMaxWidth().height(320.dp))
        } }
        rule.onNodeWithContentDescription("表情").performClick()
        rule.onNodeWithTag("keyboard-overlay").assertIsDisplayed()
        rule.onNodeWithContentDescription("键盘调节").performClick()
        rule.onNodeWithTag("keyboard-overlay").assertDoesNotExist()
        rule.runOnIdle { assertTrue(resized) }
    }
}
