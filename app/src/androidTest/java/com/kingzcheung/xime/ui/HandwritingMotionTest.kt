package com.kingzcheung.xime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HandwritingMotionTest {
    @get:Rule val rule = createComposeRule()
    @Test fun fullscreenMovesThroughIntermediateFramesAndCanReverseWithoutDroppingItsHost() {
        val expanded = mutableStateOf(false)
        var motion = HandwritingExpansion(0f, false)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
            motion = rememberHandwritingExpansion(true, expanded.value, 1L)
            Box(Modifier.requiredSize(360.dp, 550.dp).background(Color(0xFF181820)).testTag("motion-host")) {
                HandwritingKeyboardLayout(expanded = motion.active, expandedControlsWidthDp = 360,
                    bottomPaddingDp = 0, keyTextColor = Color.White, specialKeyTextColor = Color.White,
                    keyBackgroundColor = Color(0xFF404048), specialKeyBackgroundColor = Color(0xFF505058),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height((220 + 330 * motion.fraction).dp))
            }
        } }
        rule.mainClock.autoAdvance = false
        val footer = rule.onNodeWithTag("handwriting-bottom-row").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { expanded.value = true }
        rule.mainClock.advanceTimeBy(120)
        rule.runOnIdle { assertTrue(motion.active); assertTrue(motion.fraction in .01f.. .99f) }
        saveUiEvidence("handwriting-expand-middle", rule.onNodeWithTag("motion-host"))
        rule.mainClock.advanceTimeBy(180)
        rule.runOnIdle { assertEquals(1f, motion.fraction, .001f) }
        val fullFooter = rule.onNodeWithTag("handwriting-bottom-row").fetchSemanticsNode().boundsInRoot
        assertEquals(footer.height, fullFooter.height, 1f)
        assertEquals(footer.bottom, fullFooter.bottom, 1f)
        saveUiEvidence("handwriting-expanded", rule.onNodeWithTag("motion-host"))
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeBy(90)
        rule.runOnIdle { assertTrue(motion.active); assertTrue(motion.fraction in .01f.. .99f) }
        saveUiEvidence("handwriting-collapse-middle", rule.onNodeWithTag("motion-host"))
        val interrupted = motion.fraction
        rule.runOnIdle { expanded.value = true }
        rule.mainClock.advanceTimeByFrame() // publish the new target before measuring its direction
        val reverseStart = motion.fraction
        assertTrue("Reversal must not jump to either endpoint", kotlin.math.abs(reverseStart - interrupted) < .3f)
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle { assertTrue(motion.active); assertTrue(motion.fraction > reverseStart) }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { expanded.value = false }
        rule.mainClock.advanceTimeBy(250)
        rule.runOnIdle { assertFalse(motion.active); assertEquals(0f, motion.fraction, .001f) }
        assertEquals(footer, rule.onNodeWithTag("handwriting-bottom-row").fetchSemanticsNode().boundsInRoot)
    }
}
