package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.gloss.CandidateGlossary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 九键长按“下一项”= 按住 Shift：按住时候选中英对调，松手恢复（用户 10-30：长按 0 变成英文候选 I，松手后再也变不回去）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class T9NextShiftHoldTest {
    @get:Rule val rule = createComposeRule()

    private var held = 0
    private val enabled = mutableStateOf(true)

    @After fun reset() { CandidateGlossary.t9NextHeld.value = false }

    private fun setContent() {
        rule.setContent {
            Box(Modifier.size(80.dp, 60.dp).testTag("next").t9NextShiftHold(enabled.value, onDown = {}, onHeld = { held++ }))
        }
    }

    @Test
    fun `swap lasts only while the key is held`() {
        setContent()
        rule.onNodeWithTag("next").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        assertTrue(CandidateGlossary.t9NextHeld.value)
        assertEquals(1, held)
        rule.onNodeWithTag("next").performTouchInput { up() }
        rule.waitForIdle()
        assertFalse(CandidateGlossary.t9NextHeld.value)
        assertFalse(CandidateGlossary.swapHeld(screenShiftHeld = false))
    }

    @Test
    fun `a second long press swaps again instead of toggling it off`() {
        setContent()
        repeat(2) {
            rule.onNodeWithTag("next").performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(500)
            assertTrue(CandidateGlossary.t9NextHeld.value)
            rule.onNodeWithTag("next").performTouchInput { up() }
            rule.waitForIdle()
            assertFalse(CandidateGlossary.t9NextHeld.value)
        }
        assertEquals(2, held)
    }

    @Test
    fun `a tap never swaps`() {
        setContent()
        rule.onNodeWithTag("next").performTouchInput { down(center); up() }
        rule.mainClock.advanceTimeBy(500)
        assertFalse(CandidateGlossary.t9NextHeld.value)
        assertEquals(0, held)
    }

    @Test
    fun `swap ends when the key stops being the next key while still held`() {
        setContent()
        rule.onNodeWithTag("next").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        assertTrue(CandidateGlossary.t9NextHeld.value)
        enabled.value = false
        rule.waitForIdle()
        assertFalse(CandidateGlossary.t9NextHeld.value)
    }
}
