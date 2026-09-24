package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.SwipeableIconKeyButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeableIconKeyButtonTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tapUsesCurrentInputSessionAfterRecomposition() {
        var session by mutableIntStateOf(0)
        val events = mutableListOf<String>()
        rule.setContent {
            val capturedSession = session
            Box(Modifier.size(64.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black),
                    onClick = { events += "tap:$capturedSession" },
                    onPress = { events += "press:$capturedSession" },
                    onRelease = { events += "release:$capturedSession" },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle { session = 1; events.clear() }
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(setOf("press:1", "release:1", "tap:1"), events.toSet())
            assertEquals(3, events.size)
        }
    }
    @Test fun repeatedDeleteStopsOnReleaseAndTheNextTapStillWorks() {
        var deletes = 0
        var feedback = 0
        rule.setContent {
            Box(Modifier.size(64.dp)) {
                SwipeableIconKeyButton(ColorPainter(Color.Black),
                    onClick = { deletes++ }, onLongClick = { deletes++ }, onPress = { feedback++ },
                    backgroundColor = Color.White, iconColor = Color.Black,
                    modifier = Modifier.testTag("delete"), shadowEnabled = false)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("delete").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithTag("delete").performTouchInput { up() }
        val afterRelease = deletes
        val feedbackAfterRelease = feedback
        assertEquals("initial press plus every repeated deletion", deletes + 1, feedback)
        org.junit.Assert.assertTrue(afterRelease > 1)
        rule.mainClock.advanceTimeBy(150)
        assertEquals(afterRelease, deletes)
        assertEquals(feedbackAfterRelease, feedback)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("delete").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(afterRelease + 1, deletes)
            assertEquals(feedbackAfterRelease + 1, feedback)
        }
    }

}
