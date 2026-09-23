package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.SchemaSwitchUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardMenuTest {
    @get:Rule val rule = createComposeRule()
    @Test fun priorityActionsAndVisibleOptionStatesStayOnScreen() {
        var index by mutableStateOf(0)
        var resized = 0
        var settings = 0
        rule.setContent {
            MenuBar(MenuBarState(true, true, backgroundColor = Color(0xFF292929),
                keyBgColor = Color(0xFF525252), keyTextColor = Color.White,
                schemaSwitches = listOf(SchemaSwitchUiState("full_shape", states = listOf("半角", "全角"), currentIndex = index),
                    SchemaSwitchUiState("ascii_mode", states = listOf("中文", "西文")))),
                MenuBarCallbacks(onDismiss = {}, onClipboard = {}, onQuickSend = {},
                    onKeyboardResize = { resized++ }, onEmoji = {}, onReloadConfig = {},
                    onSettings = { settings++ }, onSchemaList = {}, onToggleDarkMode = {},
                    onToggleSchemaSwitch = { index = 1 - index }),
                Modifier.width(360.dp).height(260.dp))
        }
        val first = rule.onNodeWithTag("menu-item:键盘调节").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("menu-item:设置").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f); assertTrue(first.left < second.left)
        rule.onNodeWithTag("menu-item:键盘调节").performClick()
        rule.onNodeWithTag("menu-item:设置").performClick()
        assertEquals(1, resized); assertEquals(1, settings)
        listOf("剪贴板", "表情", "输入方案").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithTag("menu-item:输入选项").performClick()
        rule.onNodeWithTag("input-option:full_shape").assertIsDisplayed().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "半角"))
        rule.onNodeWithTag("input-option:full_shape").performClick().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "全角"))
        rule.onNodeWithText("中文字符宽度").assertIsDisplayed()
        rule.onNodeWithTag("input-option:ascii_mode").assertDoesNotExist()
    }
}
