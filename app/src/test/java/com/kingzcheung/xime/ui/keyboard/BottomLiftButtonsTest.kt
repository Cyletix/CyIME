package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h400dp-mdpi")
class BottomLiftButtonsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hidesActionButtonsBelowThresholdHeight() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 80.dp)) {
                    BottomLiftSpacer(
                        heightDp = BOTTOM_LIFT_ACTION_BUTTON_MIN_HEIGHT_DP - 1,
                        cursorEnabled = false,
                        onCursorMove = null,
                        onSwitchLanguage = {},
                        onOpenClipboard = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("bottom-lift-language-switch").assertDoesNotExist()
        rule.onNodeWithTag("bottom-lift-clipboard").assertDoesNotExist()
    }

    @Test
    fun showsSymmetricButtonsAtOrAboveThresholdAndDispatchesClicks() {
        var languageSwitches = 0
        var clipboardOpens = 0
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 80.dp)) {
                    BottomLiftSpacer(
                        heightDp = 40,
                        cursorEnabled = false,
                        onCursorMove = null,
                        iconColor = Color.DarkGray,
                        onSwitchLanguage = { languageSwitches++ },
                        onOpenClipboard = { clipboardOpens++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        val languageNode = rule.onNodeWithTag("bottom-lift-language-switch")
        val clipboardNode = rule.onNodeWithTag("bottom-lift-clipboard")
        languageNode.assertIsDisplayed()
        clipboardNode.assertIsDisplayed()

        val languageBounds = languageNode.fetchSemanticsNode().boundsInRoot
        val clipboardBounds = clipboardNode.fetchSemanticsNode().boundsInRoot
        assertTrue(languageBounds.right < clipboardBounds.left)

        languageNode.performClick()
        clipboardNode.performClick()
        assertEquals(1, languageSwitches)
        assertEquals(1, clipboardOpens)
    }

    @Test
    fun middleCursorSwipeWorksAlongsideActionButtonsAndDisabledActionsDoNotFire() {
        var cursorDelta = 0
        var languageSwitches = 0
        var clipboardOpens = 0
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(bottomLiftCursor = true)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 80.dp)) {
                        BottomLiftSpacer(
                            heightDp = 40,
                            cursorEnabled = true,
                            onCursorMove = { cursorDelta += it },
                            actionsEnabled = false,
                            onSwitchLanguage = { languageSwitches++ },
                            onOpenClipboard = { clipboardOpens++ },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("bottom-lift-language-switch").performClick()
        rule.onNodeWithTag("bottom-lift-clipboard").performClick()
        assertEquals(0, languageSwitches)
        assertEquals(0, clipboardOpens)

        val stripNode = rule.onNodeWithTag("bottom-lift-cursor")
        stripNode.performTouchInput {
            swipeRight(startX = center.x - 80f, endX = center.x + 80f)
        }
        rule.waitForIdle()
        assertTrue(cursorDelta > 0)
    }
}
