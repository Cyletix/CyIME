package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

/**
 * 九键键帽字母跟「键帽字母大小写」（用户 11-05：选小写九键仍是 ABC）：
 * 小写 → abc，大写 → ABC，中文大写 → 中文九键是 ABC。只改显示，拂动送出的字母不变。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w400dp-h800dp-xhdpi")
class T9KeyLabelCaseTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val keys = mutableListOf<String>()
    private val controller = mock<T9InputController> {
        on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
    }
    private val upperFaces = listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")

    private fun show(prefs: KeyboardInputPreferences) {
        AppFonts.initialize(rule.activity.application)
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides prefs) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 260.dp)) {
                        T9KeyboardLayout({ keys += it }, callbacks, KeyboardUiState(currentSchemaId = "t9_pinyin"),
                            controller, Color.DarkGray, Color.White, Color.Gray,
                            modifier = Modifier.requiredSize(360.dp, 260.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun assertFaces(upper: Boolean) {
        for (face in upperFaces) {
            val expected = if (upper) face else face.lowercase()
            val other = if (upper) face.lowercase() else face
            assertTrue("显示 $expected", shown(expected))
            assertTrue("不显示 $other", !shown(other))
        }
        assertTrue("1 键仍是分词", shown("分词"))
    }

    @Test fun defaultIsLowercase() {
        show(KeyboardInputPreferences())
        assertFaces(upper = false)
    }

    @Test fun lowerChoiceShowsLowercase() {
        show(KeyLabelCaseChoice.LOWER.applyTo(KeyboardInputPreferences()))
        assertFaces(upper = false)
    }

    @Test fun upperChoiceShowsUppercase() {
        show(KeyLabelCaseChoice.UPPER.applyTo(KeyboardInputPreferences()))
        assertFaces(upper = true)
    }

    @Test fun chineseUpperChoiceShowsUppercaseOnChineseT9() {
        show(KeyLabelCaseChoice.CHINESE_UPPER.applyTo(KeyboardInputPreferences()))
        assertFaces(upper = true)
    }

    @Test fun flickStillSendsTheSameLettersWhenFacesAreLowercase() {
        show(KeyboardInputPreferences(t9FlickLetters = true))
        val abc = rule.onNodeWithText("abc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        // 拂动门槛是 24dp（xhdpi 下 48px）：按 dp 拂 28dp（同 T9GlideInputTest），确实越过门槛，上拂也不出键盘。
        val d = with(rule.density) { 28.dp.toPx() }
        val flicks = listOf(Offset(-d, 0f) to "A", Offset(0f, -d) to "B", Offset(d, 0f) to "C")
        for ((delta, expected) in flicks) {
            rule.runOnIdle { keys.clear() }
            rule.onRoot().performTouchInput { down(abc); moveTo(abc + delta * 0.5f); moveTo(abc + delta); up() }
            rule.waitForIdle()
            // 送进九键的仍是大写原值，和键面小写无关。
            verify(controller).onLetterFlicked(expected)
            rule.runOnIdle { assertEquals(listOf(expected), keys.toList()) }
        }
    }

    @Test fun labelHelperOnlyTouchesLetters() {
        assertEquals("abc", t9LetterLabel("ABC", false))
        assertEquals("PQRS", t9LetterLabel("pqrs", true))
        assertEquals("分词", t9LetterLabel("分词", false))
        assertEquals("", t9LetterLabel("", true))
        assertEquals(listOf("w", "x", "y", "z"), t9LetterLabels(listOf("W", "X", "Y", "Z"), false))
    }
}
