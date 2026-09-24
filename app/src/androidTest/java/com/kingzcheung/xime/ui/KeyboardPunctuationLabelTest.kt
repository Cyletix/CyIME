package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Rule
import org.junit.Test

class KeyboardPunctuationLabelTest {
    @get:Rule val rule = createComposeRule()
    @Test fun switchingWidthUpdatesLabelsAndSwipeHintsButKeepsModeEntry() {
        val full = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardPunctuation provides KeyboardPunctuation(full.value, false)) {
                Row(Modifier.size(300.dp, 60.dp)) {
                    KeyButton(text = "，", onClick = {}, backgroundColor = Color.DarkGray,
                        textColor = Color.White, modifier = Modifier.weight(1f))
                    SwipeableKeyButton(text = "a", swipeText = "？", onClick = {},
                        backgroundColor = Color.DarkGray, textColor = Color.White, modifier = Modifier.weight(1f))
                    KeyboardModeKey(slot = 1, onKeyPress = {}, backgroundColor = Color.DarkGray,
                        textColor = Color.White, modifier = Modifier.weight(1f))
                }
            }
        }
        rule.onNodeWithText(",").assertIsDisplayed()
        rule.onNodeWithText("?").assertIsDisplayed()
        rule.onNodeWithText("!@#").assertIsDisplayed()
        rule.runOnIdle { full.value = true }
        rule.onNodeWithText("，").assertIsDisplayed()
        rule.onNodeWithText("？").assertIsDisplayed()
        rule.onNodeWithText("a").assertIsDisplayed()
        rule.onNodeWithText("!@#").assertIsDisplayed()
    }
}
