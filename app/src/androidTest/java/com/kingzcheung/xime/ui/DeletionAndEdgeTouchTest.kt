package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeletionAndEdgeTouchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rowMarginsHitEdgeLettersAtPhoneAndTabletPanelWidths() {
        KeysConfigHelper.loadConfig(InstrumentationRegistry.getInstrumentation().targetContext)
        val panelWidth = mutableStateOf(280.dp)
        val letters = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.width(panelWidth.value).height(60.dp).testTag("row")) {
                    KeyboardRowWithConfig("asdfghjkl".map(Char::toString), { letters += it },
                        KeyboardRowConfig(Color.Gray, Color.White), false, edgeInset = 16.dp)
                }
            }
        }
        for (w in listOf(280.dp, 360.dp, 800.dp)) {
            rule.runOnIdle { panelWidth.value = w; letters.clear() }
            rule.onNodeWithTag("row").performTouchInput {
                click(Offset(1f, center.y))
                click(Offset(width - 1f, center.y))
            }
            rule.runOnIdle { assertEquals(listOf("a", "l"), letters) }
        }
    }

    private fun checkHold(tag: String, count: () -> Int) {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(tag).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        rule.runOnIdle { assertTrue("hold must delete repeatedly", count() >= 3) }
        rule.onNodeWithTag(tag).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(32)
        var stopped = 0
        rule.runOnIdle { stopped = count() }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { assertEquals("release must stop repeat", stopped, count()) }
        rule.onNodeWithTag(tag).performTouchInput { click() }
        rule.runOnIdle { assertEquals("next tap must not be swallowed", stopped + 1, count()) }
        rule.mainClock.autoAdvance = true
    }

    @Test fun expandedCandidateDeleteRepeatsAndStopsOnRelease() {
        var count = 0
        rule.setContent { MaterialTheme {
            CandidatePage(CandidatePageState(backgroundColor = Color.Black, textColor = Color.White),
                CandidatePageCallbacks(onCandidateSelect = {}, onDelete = { count++ }),
                Modifier.size(360.dp, 240.dp))
        } }
        checkHold("expanded-delete-key") { count }
    }

    @Test fun handwritingDeleteRepeatsInNormalAndFullScreenPanels() {
        var count = 0
        val expanded = mutableStateOf(false)
        rule.setContent { MaterialTheme {
            HandwritingKeyboardLayout(onKeyPress = { if (it == "delete") count++ }, expanded = expanded.value,
                bottomPaddingDp = 0, modifier = Modifier.size(360.dp, 300.dp))
        } }
        checkHold("handwriting-key:delete") { count }
        rule.runOnIdle { count = 0; expanded.value = true }
        rule.waitForIdle()
        checkHold("handwriting-key:delete") { count }
    }
}
