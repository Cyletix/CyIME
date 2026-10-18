package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardMenuTest {
    @get:Rule val rule = createComposeRule()

    @Test fun defaultMenuFitsOnePageAndTogglesShowTheirState() {
        var sound by mutableStateOf(true)
        var resized = 0
        var settings = 0
        rule.setContent {
            val actions = ToolbarButton.DEFAULT_MENU.map { button ->
                ToolbarAction(ToolbarButtonItem.Builtin(button), active = button == ToolbarButton.SOUND && sound) {
                    when (button) {
                        ToolbarButton.SOUND -> sound = !sound
                        ToolbarButton.FLOAT -> resized++
                        ToolbarButton.SETTINGS -> settings++
                        else -> Unit
                    }
                }
            }
            MenuBar(MenuBarState(true, true, backgroundColor = Color(0xFF292929),
                keyBgColor = Color(0xFF525252), keyTextColor = Color.White),
                actions, Modifier.width(360.dp).height(260.dp))
        }
        ToolbarButton.DEFAULT_MENU.forEach { rule.onNodeWithTag("menu-item:${it.id}").assertIsDisplayed() }
        rule.onNodeWithTag("menu-item:float").performClick()
        rule.onNodeWithTag("menu-item:settings").performClick()
        assertEquals(1, resized); assertEquals(1, settings)
        rule.onNodeWithTag("menu-item:sound").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已开启"))
            .performClick()
        rule.onNodeWithTag("menu-item:sound").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已关闭"))
        // Plain actions are not switches and carry no on/off state.
        rule.onNodeWithTag("menu-item:settings").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        listOf("方案选项", "智能联想", "语音转文本").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        saveUiEvidence("menu-quick-actions", rule.onNodeWithTag("menu-pages"))
    }
}
