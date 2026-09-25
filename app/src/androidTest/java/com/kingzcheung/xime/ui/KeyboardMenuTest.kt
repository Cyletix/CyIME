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
                    SchemaSwitchUiState("ascii_punct", states = listOf("中文标点", "西文标点")),
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
        rule.onNodeWithTag("menu-item:全角／半角").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "半角"))
            .performClick()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "全角"))
        rule.onNodeWithTag("menu-item:全角／半角").performClick()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "半角"))
        rule.onNodeWithTag("menu-item:输入选项").performClick()
        rule.onNodeWithTag("input-option:full_shape").assertDoesNotExist()
        // 标点样式（ascii_punct）与全角/半角重复，已合并：输入选项里不应再出现
        rule.onNodeWithTag("input-option:ascii_punct").assertDoesNotExist()
        rule.onNodeWithTag("input-option:ascii_mode").assertDoesNotExist()
    }

    @Test fun englishMenuHasNoFullWidthEntryBecausePunctuationIsAlwaysHalfWidth() {
        var toggles = 0
        rule.setContent {
            MenuBar(MenuBarState(true, true, backgroundColor = Color(0xFF292929),
                keyBgColor = Color(0xFF525252), keyTextColor = Color.White,
                isAsciiMode = true,
                schemaSwitches = listOf(
                    SchemaSwitchUiState("full_shape", states = listOf("半角", "全角"), currentIndex = 0),
                    SchemaSwitchUiState("simplification", states = listOf("简", "繁")))),
                MenuBarCallbacks(onDismiss = {}, onClipboard = {}, onQuickSend = {},
                    onKeyboardResize = {}, onEmoji = {}, onReloadConfig = {},
                    onSettings = {}, onSchemaList = {}, onToggleDarkMode = {},
                    onToggleSchemaSwitch = { toggles++ }),
                Modifier.width(360.dp).height(260.dp))
        }
        // 全角/半角只作用于中文与日文：英文下入口不出现，也不可能被点掉状态
        rule.onNodeWithTag("menu-item:全角／半角").assertDoesNotExist()
        rule.onNodeWithTag("menu-item:键盘调节").assertIsDisplayed()
        assertEquals(0, toggles)
    }
}
