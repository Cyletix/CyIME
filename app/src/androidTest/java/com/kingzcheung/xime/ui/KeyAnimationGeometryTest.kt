package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyAnimationGeometryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun capAnimationDoesNotRepublishTouchGeometry() {
        var geometryChanges = 0
        val geometry = LetterGeometry { geometryChanges++ }
        rule.setContent {
            CompositionLocalProvider(
                LocalLetterGeometry provides geometry,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    keyGlowEnabled = true, keyAnimationEnabled = true),
            ) {
                Box(Modifier.size(80.dp, 64.dp)) {
                    SwipeableKeyButton(text = "q", onClick = {}, backgroundColor = Color.DarkGray,
                        textColor = Color.White, modifier = Modifier.testTag("key"))
                }
            }
        }
        var initial = 0
        rule.runOnIdle { initial = geometryChanges }
        rule.onNodeWithTag("key").performTouchInput { down(center); advanceEventTime(30); up() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("Animation must not move touch geometry", initial, geometryChanges) }
    }
}
