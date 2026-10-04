package com.kingzcheung.xime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kingzcheung.xime.keyboard.*
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.ui.keyboard.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ToolbarPressFeedbackTest {
    @get:Rule val rule = createComposeRule()

    @Test fun releaseFollowedByHoverAndFocusCannotLeaveClipboardGlowing() {
        val source = MutableInteractionSource()
        val active = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.background(Color.Black).testTag("button-surface")) {
                    ToolbarActionButton(ToolbarAction(ToolbarButtonItem.Builtin(ToolbarButton.CLIPBOARD), active.value) {},
                        CandidateBarVisuals(Color.Black, Color.White, Color.Gray, isDarkTheme = true),
                        Color.White, 0f, RecognitionState.IDLE, source)
                }
            }
        }
        val resting = rule.onNodeWithTag("button-surface").captureToImage().toPixelMap()
        rule.mainClock.autoAdvance = false
        for (cancel in listOf(false, true)) {
            val press = PressInteraction.Press(Offset.Zero)
            rule.runOnIdle { runBlocking { source.emit(press) }; active.value = true }
            rule.mainClock.advanceTimeBy(32)
            rule.runOnIdle { runBlocking {
                source.emit(if (cancel) PressInteraction.Cancel(press) else PressInteraction.Release(press))
            }; active.value = false }
            rule.mainClock.advanceTimeBy(16)
            rule.runOnIdle { runBlocking {
                val hover = HoverInteraction.Enter()
                source.emit(hover)
                source.emit(HoverInteraction.Exit(hover))
                val focus = FocusInteraction.Focus()
                source.emit(focus)
                source.emit(FocusInteraction.Unfocus(focus))
            } }
            rule.mainClock.advanceTimeBy(350)
            val released = rule.onNodeWithTag("button-surface").captureToImage().toPixelMap()
            assertEquals(resting[resting.width / 2, 4], released[released.width / 2, 4])
            rule.onNodeWithContentDescription("剪贴板").assertIsNotSelected()
        }
    }
}
