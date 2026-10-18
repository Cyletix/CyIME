package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardControlsVisualTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shiftStatesAreDistinctInOrdinaryAndGlassThemes() {
        val vm = KeyboardViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Column(Modifier.width(360.dp).testTag("shift-states")) {
                        for (dark in listOf(true, false)) for (glass in listOf(false, true)) {
                            val panel = if (dark) Color(0xFF202027) else Color(0xFFF5F5F8)
                            val key = if (dark) Color(0xFF555461) else Color(0xFFDDDDE4)
                            val text = if (dark) Color.White else Color.Black
                            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                                frostedGlass = FrostedGlassConfig(enabled = glass))) {
                                Column(Modifier.background(panel)) {
                                    Text("${if (dark) "深色" else "浅色"} · ${if (glass) "毛玻璃" else "普通"}", color = text)
                                    Row(Modifier.height(70.dp)) {
                                        for (mode in ShiftMode.entries) ShiftCapsKeyButton(vm, mode, {}, null,
                                            key, text, Color(0xFFBBA3F4), panel,
                                            Modifier.weight(1f).testTag("shift:$dark:$glass:$mode"), shadowEnabled = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(true, false)) for (glass in listOf(false, true)) {
            val colors = ShiftMode.entries.map {
                val pixels = rule.onNodeWithTag("shift:$dark:$glass:$it", true).captureToImage().toPixelMap()
                pixels[pixels.width / 4, pixels.height / 2]
            }
            assertEquals("OFF / SINGLE / CAPS must have different fills", 3, colors.toSet().size)
        }
        saveUiEvidence("shift-states", rule.onNodeWithTag("shift-states"))
    }

    @Test fun clearDirectionAndRedBlueFeedbackFollowLivePreference() {
        var reverse by mutableStateOf(false)
        val events = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(reverseSymbolSwipe = reverse)) {
                Box(Modifier.size(240.dp)) {
                    SwipeableIconKeyButton(ColorPainter(Color.Transparent), onClick = { events += "tap" },
                        backgroundColor = Color.Gray, iconColor = Color.White,
                        onSwipeUp = { events += "clear" }, onSwipeDown = { events += "undo" },
                        swipeUpLabel = "上滑清空", swipeDownLabel = "下滑撤回",
                        upFeedback = KeyFlickFeedback.DELETE, downFeedback = KeyFlickFeedback.UNDO,
                        followSymbolSwipeDirection = true, shadowEnabled = false, modifier = Modifier.testTag("delete"))
                }
            }
        }
        val key = rule.onNodeWithTag("delete")
        for (setting in listOf(false, true)) {
            rule.runOnIdle { reverse = setting }
            for (clear in listOf(true, false)) {
                val dy = if (clear == setting) -95f else 95f
                key.performTouchInput { down(center); moveTo(center + Offset(0f, dy)) }
                rule.onNodeWithTag("key-flick-preview").assertTextEquals(if (clear) "清空" else "撤回")
                val pixels = key.captureToImage().toPixelMap()
                val color = pixels[pixels.width / 4, pixels.height / 4]
                assertTrue(if (clear) color.red > color.blue + .1f else color.blue > color.red + .1f)
                key.performTouchInput { up() }
                rule.runOnIdle { assertEquals(if (clear) "clear" else "undo", events.last()) }
            }
        }
    }
}
