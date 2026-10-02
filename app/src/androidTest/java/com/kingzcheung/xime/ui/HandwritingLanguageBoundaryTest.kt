package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
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
        for (lang in listOf(InputLanguage.JAPANESE, InputLanguage.ENGLISH, InputLanguage.UNSPECIFIED)) {
            rule.runOnIdle { language = lang }
            rule.onNodeWithTag("handwriting-canvas").assertDoesNotExist()
            rule.onNodeWithTag("handwriting-unavailable").assertExists()
            rule.onNodeWithText("选择输入方案").performScrollTo().assertIsDisplayed().performClick()
        }
        rule.runOnIdle { assertEquals(3, exits) }
    }
}
