package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.HandwritingLookupKeyboard
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HandwritingLanguageBoundaryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unsupportedLanguageHasNoChineseCanvasAndReturnActionStaysReachable() {
        var language by mutableStateOf(InputLanguage.JAPANESE)
        var exits = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    HandwritingKeyboardLayout(language = language, modifier = Modifier.size(280.dp, 140.dp),
                        onUnsupportedExit = { exits++ }, unsupportedExitLabel = "选择输入方案")
                }
            }
        }
        for (lang in listOf(InputLanguage.JAPANESE, InputLanguage.UNSPECIFIED)) {
            rule.runOnIdle { language = lang }
            rule.onNodeWithTag("handwriting-canvas").assertDoesNotExist()
            rule.onNodeWithTag("handwriting-unavailable").assertExists()
            rule.onNodeWithText("选择输入方案").performScrollTo().assertIsDisplayed().performClick()
        }
        rule.runOnIdle { assertEquals(2, exits) }
    }

    @Test fun englishTypingShowsChineseHandwritingAndLanguageChangesKeepTheBoundary() {
        var language by mutableStateOf(InputLanguage.ENGLISH)
        rule.setContent {
            MaterialTheme {
                HandwritingKeyboardLayout(language = language, modifier = Modifier.size(320.dp, 260.dp))
            }
        }
        rule.onNodeWithTag("handwriting-canvas").assertExists()
        rule.onNodeWithTag("handwriting-language").assertTextEquals("中文手写")
        rule.onNodeWithTag("handwriting-unavailable").assertDoesNotExist()

        rule.runOnIdle { language = InputLanguage.JAPANESE }
        rule.onNodeWithTag("handwriting-canvas").assertDoesNotExist()
        rule.onNodeWithTag("handwriting-unavailable").assertExists()

        rule.runOnIdle { language = InputLanguage.CHINESE }
        rule.onNodeWithTag("handwriting-canvas").assertExists()
        rule.onNodeWithTag("handwriting-unavailable").assertDoesNotExist()
    }

    @Test fun lookupFromEnglishShowsChineseHandwritingAndJapaneseRemainsUnavailable() {
        var state by mutableStateOf(KeyboardUiState(currentSchemaId = "japanese", isAsciiMode = true))
        rule.setContent {
            MaterialTheme {
                HandwritingLookupKeyboard(
                    keyTextColor = Color.Black, specialKeyBgColor = Color.LightGray, keyboardBgColor = Color.White,
                    shadowEnabled = false, shadowElevation = 0.dp, shadowShapeRadius = 8.dp,
                    uiState = state, onKeyPress = {}, onButtonFeedback = null, onCandidates = null,
                    onExit = {}, clearSignal = 0, modifier = Modifier.size(320.dp, 260.dp),
                )
            }
        }
        rule.onNodeWithTag("handwriting-lookup-canvas").assertExists()
        rule.onNodeWithTag("handwriting-language").assertTextEquals("中文手写")
        rule.runOnIdle { state = state.copy(isAsciiMode = false) }
        rule.onNodeWithTag("handwriting-lookup-canvas").assertDoesNotExist()
        rule.onNodeWithTag("handwriting-unavailable").assertExists()
    }
}
