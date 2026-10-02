package com.kingzcheung.xime.ui

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardToolbar
import com.kingzcheung.xime.ui.keyboard.HardwareToolbarPosition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HardwareKeyboardToolbarTest {
    @get:Rule val rule = createComposeRule()
    private val visible = mutableStateOf(true)
    private val position = mutableStateOf(HardwareToolbarPosition())
    private var bounds: Rect? = null
    private var keyboard = 0
    private var emoji = 0
    private var clipboard = 0
    private var language = 0
    private var background = 0

    private fun show(width: Int = 360, height: Int = 400) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("toolbar-host")) {
                    Box(Modifier.fillMaxSize().clickable { background++ }.testTag("toolbar-underlay"))
                    if (visible.value) HardwareKeyboardToolbar(
                        onShowKeyboard = { keyboard++ }, onEmoji = { emoji++ }, onClipboard = { clipboard++ },
                        onSwitchInputMethod = { language++ }, backgroundColor = Color.DarkGray, contentColor = Color.White,
                        onBoundsChanged = { bounds = it }, position = position.value, onPositionChange = { position.value = it },
                    )
                }
            }
        }
    }

    @Test fun actionsRemainSeparateFromDragAndEmptyHostDoesNotConsumeTouches() {
        show()
        rule.onNodeWithTag("hardware-toolbar-keyboard").performClick()
        rule.onNodeWithTag("hardware-toolbar-emoji").performClick()
        rule.onNodeWithTag("hardware-toolbar-clipboard").performClick()
        rule.onNodeWithTag("hardware-toolbar-language").performClick()
        rule.onNodeWithTag("toolbar-host").performTouchInput { click(Offset(8f, 8f)) }
        rule.runOnIdle {
            assertEquals(listOf(1, 1, 1, 1), listOf(keyboard, emoji, clipboard, language))
            assertEquals(1, background)
            assertEquals(232, bounds!!.width())
            assertEquals(56, bounds!!.height())
            visible.value = false
        }
        rule.runOnIdle { assertNull(bounds) }
    }

    @Test fun narrowOverflowStaysInlineAndDoesNotHideRecovery() {
        show(width = 208)
        rule.onNodeWithTag("hardware-toolbar-clipboard").assertDoesNotExist()
        rule.onNodeWithTag("hardware-toolbar-more").performClick()
        rule.onNodeWithTag("hardware-toolbar-keyboard").assertIsDisplayed()
        rule.onNodeWithTag("hardware-toolbar-clipboard").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, clipboard) }
        rule.onNodeWithTag("hardware-toolbar-clipboard").assertDoesNotExist()
        rule.onNodeWithTag("hardware-toolbar-keyboard").performClick()
        rule.runOnIdle { assertEquals(1, keyboard) }
    }

    @Test fun handleDragsVerticallyAndSnapsRightWithoutFiringAction() {
        show()
        val hostBounds = rule.onNodeWithTag("toolbar-host").fetchSemanticsNode().boundsInRoot
        val handleBounds = rule.onNodeWithTag("hardware-toolbar-drag").fetchSemanticsNode().boundsInRoot
        val start = handleBounds.center - hostBounds.topLeft
        rule.onNodeWithTag("toolbar-host").performTouchInput {
            down(start)
            moveTo(start + Offset(500f, -100f))
            up()
        }
        rule.runOnIdle {
            assertEquals(1f, position.value.xFraction, 0.001f)
            assertTrue(position.value.yFraction in 0.1f..0.9f)
            assertEquals(0, keyboard + emoji + clipboard + language)
        }
    }

    @Test fun minimumWindowKeeps48DpRecoveryButton() {
        show(width = 48)
        rule.onNodeWithTag("hardware-toolbar-keyboard").assertWidthIsEqualTo(48.dp).assertIsDisplayed().performClick()
        rule.onNodeWithTag("hardware-toolbar-drag").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(48, bounds!!.width())
            assertEquals(1, keyboard)
        }
    }
}
