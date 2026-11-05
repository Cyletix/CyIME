package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.CtrlShortcuts
import com.kingzcheung.xime.keyboard.GestureAction
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
 * 屏幕键盘的 Shift+回车（用户 11-04）：手指按住 Shift 点回车、或从 Shift 拖到回车上松手，发 Shift+回车命令（换行）；
 * 单次大写、大写锁定时点回车还是普通回车。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w800dp-h1280dp-xhdpi")
class ShiftEnterKeyTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val keys = mutableListOf<String>()
    private val commands = mutableListOf<String>()
    private lateinit var vm: KeyboardViewModel

    private fun show(schema: String = "") {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema(schema)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        vm = KeyboardViewModel(app)
        val onKeyPress: (String) -> Unit = { key ->
            when (key) {
                "shift" -> vm.toggleShift()
                "shift_single" -> vm.singleTapShift()
                "shift_caps" -> vm.doubleTapShift()
                else -> {
                    keys += key
                    vm.onCharacterTyped()
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true) {
                MaterialTheme {
                    Box(Modifier.requiredSize(800.dp, 320.dp)) {
                        KeyboardLayout(onKeyPress, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onGestureAction = { action, value -> if (action == GestureAction.COMMAND) commands += value }),
                            KeyboardUiState(currentSchemaId = schema), false,
                            Modifier.requiredSize(800.dp, 320.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun center(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot.center

    private fun holdShiftAndTapEnter() {
        val shift = center("shift-key")
        val enter = center("qwerty-enter-key")
        rule.onRoot().performTouchInput { down(0, shift) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { down(1, enter); up(1) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
    }

    @Test fun heldShiftPlusEnterIsNewline() {
        show()
        holdShiftAndTapEnter()
        assertEquals(listOf(CtrlShortcuts.SHIFT_ENTER_COMMAND), commands)
        assertEquals(emptyList<String>(), keys)
        // 用过的 Shift 松手不切成单次大写。
        assertEquals(ShiftMode.OFF, vm.shiftMode.value)
    }

    @Test fun heldShiftPlusEnterOn14Keys() {
        show("pinyin_14jian")
        holdShiftAndTapEnter()
        assertEquals(listOf(CtrlShortcuts.SHIFT_ENTER_COMMAND), commands)
    }

    @Test fun dragFromShiftOntoEnterIsNewline() {
        show()
        val shift = center("shift-key")
        val enter = center("qwerty-enter-key")
        rule.onRoot().performTouchInput {
            down(shift)
            for (step in 1..8) moveTo(shift + (enter - shift) * (step / 8f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(CtrlShortcuts.SHIFT_ENTER_COMMAND), commands)
        assertEquals(emptyList<String>(), keys)
    }

    /** 反例：单次大写（句首自动大写也是它）、大写锁定、不按 Shift 时，回车就是回车。 */
    @Test fun latchedShiftKeepsPlainEnter() {
        show()
        rule.onNodeWithTag("qwerty-enter-key", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        rule.runOnIdle { vm.singleTapShift() }
        rule.onNodeWithTag("qwerty-enter-key", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        rule.runOnIdle { vm.doubleTapShift() }
        rule.onNodeWithTag("qwerty-enter-key", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(listOf("enter", "enter", "enter"), keys)
        assertEquals(emptyList<String>(), commands)
    }
}
