package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.viewmodel.ShiftMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 一根手指按住 Shift、另一根手指打字：按住期间每个字母都是大写，直到 Shift 抬手（用户 10-25：CyLM 的 M 变小写）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w800dp-h1280dp-xhdpi")
class ShiftHoldTypingTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val typed = StringBuilder()
    private lateinit var vm: KeyboardViewModel

    private fun show(schema: String = "", ascii: Boolean = false, width: Int = 800, height: Int = 320,
                     prefs: KeyboardInputPreferences = KeyboardInputPreferences()) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema(schema)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        vm = KeyboardViewModel(app)
        // 与 KeyboardView 的 fullScreenOnKeyPress 相同：提交时读真实 Shift 状态，再记一次打字。
        val onKeyPress: (String) -> Unit = { key ->
            when (key) {
                "shift" -> vm.toggleShift()
                "shift_single" -> vm.singleTapShift()
                "shift_caps" -> vm.doubleTapShift()
                else -> {
                    typed.append(vm.shiftMode.value.applyToKey(key))
                    vm.onCharacterTyped()
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides prefs) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        KeyboardLayout(onKeyPress, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onGlideWord = { _, _ -> }),
                            KeyboardUiState(currentSchemaId = schema, isAsciiMode = ascii), ascii,
                            Modifier.requiredSize(width.dp, height.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun center(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot.center
    private fun keyTag(letter: Char): String {
        val single = "qwerty-key:$letter"
        if (runCatching { rule.onNodeWithTag(single, true).fetchSemanticsNode() }.isSuccess) return single
        // 14 键：字母在双字母键上。
        val pairs = listOf("qw", "er", "ty", "ui", "op", "as", "df", "gh", "jk", "zx", "cv", "bn")
        return "qwerty-key:" + pairs.first { letter in it }
    }

    private fun holdShift(drift: Offset = Offset.Zero) {
        val shift = center("shift-key")
        rule.onRoot().performTouchInput { down(0, shift) }
        rule.waitForIdle()
        if (drift != Offset.Zero) {
            rule.onRoot().performTouchInput { moveTo(0, shift + drift) }
            rule.waitForIdle()
        }
    }

    private fun releaseShift() {
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
    }

    private fun tap(letter: Char) {
        val tag = keyTag(letter)
        val bounds = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        // 14 键双字母键按左右半区；单字母键点中心。
        val pair = tag.substringAfter(':')
        val point = if (pair.length == 2) Offset(bounds.left + bounds.width * (pair.indexOf(letter) * 2 + 1) / 4f, bounds.center.y)
            else bounds.center
        rule.onRoot().performTouchInput { down(1, point); up(1) }
        rule.waitForIdle()
    }

    private fun point(letter: Char): Offset {
        val tag = keyTag(letter)
        val bounds = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        val pair = tag.substringAfter(':')
        return if (pair.length == 2) Offset(bounds.left + bounds.width * (pair.indexOf(letter) * 2 + 1) / 4f, bounds.center.y)
            else bounds.center
    }

    /** 用户原话的顺序：按住 Shift 打 C，松开打 y，再按住 Shift 打 L、M。14 键小写点双字母键会提交整键。 */
    private fun typeCyLM(drift: Offset = Offset.Zero, expected: String = "CyLM") {
        holdShift(drift)
        tap('c')
        releaseShift()
        tap('y')
        holdShift(drift)
        tap('l')
        assertEquals("打完 L 仍按着 Shift", ShiftMode.HELD, vm.shiftMode.value)
        tap('m')
        releaseShift()
        assertEquals(expected, typed.toString())
        assertEquals(ShiftMode.OFF, vm.shiftMode.value)
    }

    @Test fun pinyin26() { show(); typeCyLM() }

    @Test fun english26() { show(ascii = true); typeCyLM() }

    @Test fun english26WithGlide() { show(ascii = true, prefs = KeyboardInputPreferences(glideTyping = true)); typeCyLM() }

    @Test fun pinyin14() { show("pinyin_14jian"); typeCyLM(expected = "CtyLM") }

    @Test fun phoneWidth() { show(width = 360, height = 240); typeCyLM() }

    /** 按住的手指常会轻微移动：超过触摸容差也不能松开 Shift。 */
    @Test fun heldFingerDrifts26() { show(); typeCyLM(Offset(12f, -6f)) }

    @Test fun heldFingerDrifts14() { show("pinyin_14jian"); typeCyLM(Offset(12f, -6f), "CtyLM") }

    @Test fun heldFingerDriftsEnglishGlide() {
        show(ascii = true, prefs = KeyboardInputPreferences(glideTyping = true)); typeCyLM(Offset(12f, -6f))
    }

    /** 一直按住 Shift 连打多个字母。 */
    @Test fun manyLettersWhileHeld() {
        show()
        holdShift()
        "hello".forEach(::tap)
        releaseShift()
        tap('x')
        assertEquals("HELLOx", typed.toString())
    }

    /** 另一只手两根手指交叠着打 L、M（L 还没抬 M 已按下），Shift 一直按着。 */
    @Test fun overlappingLettersWhileHeld() {
        show()
        holdShift()
        val l = point('l'); val m = point('m')
        rule.onRoot().performTouchInput { down(1, l) }
        rule.onRoot().performTouchInput { down(2, m) }
        rule.onRoot().performTouchInput { up(1) }
        rule.onRoot().performTouchInput { up(2) }
        rule.waitForIdle()
        assertEquals(ShiftMode.HELD, vm.shiftMode.value)
        tap('n')
        releaseShift()
        assertEquals("LMN", typed.toString())
    }

    /** 先按下 y 还没抬手就按住 Shift，再打 L、M。 */
    @Test fun shiftPressedDuringLetter() {
        show()
        val y = point('y')
        rule.onRoot().performTouchInput { down(1, y) }
        rule.waitForIdle()
        holdShift()
        rule.onRoot().performTouchInput { up(1) }
        rule.waitForIdle()
        tap('l'); tap('m')
        releaseShift()
        assertEquals("LM", typed.toString().drop(1))
    }

    /** 按住 Shift 打字时引擎部署完、键盘被重置（装新版后第一次打字）：后面的字母仍大写，松手回小写。 */
    @Test fun keyboardResetWhileHeld() {
        show()
        holdShift()
        tap('c'); tap('y')
        rule.runOnIdle { vm.resetKeyboard(false, "") }
        rule.waitForIdle()
        tap('l'); tap('m')
        releaseShift()
        tap('x')
        assertEquals("CYLMx", typed.toString())
    }

    /** 英文键盘里每次中英状态同步都会重置 Shift：按着时同样不能松开。 */
    @Test fun englishAsciiSyncWhileHeld() {
        show(ascii = true)
        holdShift()
        tap('c')
        rule.runOnIdle { vm.dispatch(KeyboardDispatchAction.AsciiModeChanged(true, "")) }
        rule.waitForIdle()
        tap('l')
        releaseShift()
        tap('x')
        assertEquals("CLx", typed.toString())
    }
}
