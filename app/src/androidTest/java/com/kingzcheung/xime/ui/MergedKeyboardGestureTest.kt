package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.CompactKeyboardRowWithConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardRowConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardRowWithConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the shipped 14-key YAML, real row wiring and pointer stream together. */
@RunWith(AndroidJUnit4::class)
class MergedKeyboardGestureTest {
    @get:Rule val rule = createComposeRule()

    @After fun restoreStandardRows() {
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    private fun verifyFourteenKeyRow(compact: Boolean) {
        KeysConfigHelper.loadConfig(ApplicationProvider.getApplicationContext())
        KeysConfigHelper.setActiveKeyboardSchema("pinyin_14jian")
        assertEquals(listOf("qw", "er", "ty", "ui", "op"), KeysConfigHelper.getKeyRows(false).first())
        val commits = mutableListOf<String>()
        var density = 1f
        var presses = 0
        var releases = 0
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val rowModifier = Modifier.width(350.dp).height(100.dp)
                val config = KeyboardRowConfig(Color.White, Color.Black, shadowEnabled = false)
                if (compact) {
                    CompactKeyboardRowWithConfig(
                        keys = KeysConfigHelper.getKeyRows(false).first(),
                        onKeyPress = { commits += "tap:$it" }, config = config, isShifted = false,
                        modifier = rowModifier, onCommitText = { commits += "literal:$it" },
                        onKeyPressDown = { presses++ }, onKeyRelease = { releases++ },
                    )
                } else {
                    KeyboardRowWithConfig(
                        keys = KeysConfigHelper.getKeyRows(false).first(),
                        onKeyPress = { commits += "tap:$it" }, config = config, isShifted = false,
                        modifier = rowModifier, onCommitText = { commits += "literal:$it" },
                        onKeyPressDown = { presses++ }, onKeyRelease = { releases++ },
                    )
                }
            }
        }
        val key = rule.onNodeWithText("qw")
        key.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * density))
        }
        rule.runOnIdle { assertTrue("preview must not submit either q or 1", commits.isEmpty()) }
        key.performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf("literal:1"), commits) }
        key.performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(listOf("literal:1", "tap:q"), commits) }
        key.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * density))
            moveTo(center)
            up()
        }
        key.performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * density))
            cancel()
        }
        rule.runOnIdle {
            assertEquals("cancel/backtracking must not append q", listOf("literal:1", "tap:q"), commits)
            assertEquals(4, presses)
            assertEquals(4, releases)
        }
        rule.onNodeWithText("er").performTouchInput {
            down(center)
            moveTo(center - Offset(0f, 75f * density))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("literal:1", "tap:q", "literal:2"), commits) }
    }

    @Test fun regularFourteenKeyRowCommitsOnlyTheChosenGesture() = verifyFourteenKeyRow(compact = false)
    @Test fun compactFourteenKeyRowCommitsOnlyTheChosenGesture() = verifyFourteenKeyRow(compact = true)
}
