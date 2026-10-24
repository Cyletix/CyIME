package com.kingzcheung.xime.ui.keyboard

import android.app.Application
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 26 键按住 !@# 当 Ctrl：按下就换键面，拖到字母上松手发 Ctrl+字母；不拖动且 1 秒内松手打开符号，
 * 超过 1 秒或拖出去没落在字母上松手什么都不做。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
class CtrlSymbolKeyTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val keys = mutableListOf<String>()
    private val commands = mutableListOf<String>()

    private fun show(schema: String = "") {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema(schema)
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(symbolKeyHoldSeconds = 1f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 240.dp)) {
                        KeyboardLayout({ keys += it }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onGestureAction = { action, value -> if (action == GestureAction.COMMAND) commands += value }),
                            KeyboardUiState(currentSchemaId = schema), false, Modifier.requiredSize(360.dp, 240.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
    private fun labelShown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun holdShowsShortcutsAndDragSendsCtrlLetter() {
        show()
        assertTrue(!labelShown("复制"))
        val symbol = bounds("mode-slot-1")
        val c = bounds("qwerty-key:c")
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center) }
        rule.waitForIdle()
        assertTrue("按下就显示快捷键", labelShown("复制") && labelShown("粘贴") && labelShown("全选"))
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput {
            val target = c.center - symbol.topLeft
            for (step in 1..8) moveTo(center + (target - center) * (step / 8f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("ctrl:c"), commands)
        assertEquals("拖到字母不打开符号", emptyList<String>(), keys)
        assertTrue("松手恢复字母键面", !labelShown("复制"))
    }

    @Test fun tapOpensSymbols() {
        show()
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(listOf("symbol"), keys)
        assertEquals(emptyList<String>(), commands)
    }

    @Test fun releaseWithinTheLimitOpensSymbols() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(900)
        assertEquals("按着不打开", emptyList<String>(), keys)
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf("symbol"), keys)
    }

    @Test fun releaseAfterTheLimitDoesNothing() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1200)
        assertEquals("超过时限也不自己打开", emptyList<String>(), keys)
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals("超过时限松手不打开", emptyList<String>(), keys)
        assertEquals(emptyList<String>(), commands)
    }

    @Test fun releasingOffTheLettersDoesNothing() {
        show()
        val symbol = bounds("mode-slot-1")
        val space = bounds("qwerty-enter-key")
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput {
            down(center)
            val target = space.center - symbol.topLeft
            for (step in 1..8) moveTo(center + (target - center) * (step / 8f))
            up()
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), keys)
        assertEquals(emptyList<String>(), commands)
    }

    /** 一根手指按住 !@#，另一根手指点字母：和按住 Shift 再点一样，发 Ctrl+字母、不打字，松开 !@# 也不开符号。 */
    private fun tapEachLetterWhileHolding(letters: List<Char>) {
        val symbol = bounds("mode-slot-1").center
        rule.onRoot().performTouchInput { down(0, symbol) }
        rule.waitForIdle()
        for (letter in letters) {
            val key = bounds("qwerty-key:$letter").center
            rule.onRoot().performTouchInput { down(1, key); up(1) }
            rule.waitForIdle()
        }
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals(letters.map { "ctrl:$it" }, commands)
        assertEquals("不打字也不开符号", emptyList<String>(), keys)
    }

    private val shortcutLetters = "acvxzysfwtnorpbi".toList()

    @Test fun secondFingerTapSendsEveryShortcut() {
        show()
        tapEachLetterWhileHolding(shortcutLetters + listOf('q', 'k'))
    }

    @Test fun fourteenKeySecondFingerTapSendsEveryShortcut() {
        show("pinyin_14jian")
        tapEachLetterWhileHolding(shortcutLetters + listOf('q', 'k', 'm', 'l'))
    }

    @Test fun fourteenKeyDragSendsEveryShortcut() {
        show("pinyin_14jian")
        for (letter in shortcutLetters) {
            val symbol = bounds("mode-slot-1")
            rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center) }
            rule.waitForIdle()
            val key = bounds("qwerty-key:$letter")
            rule.onNodeWithTag("mode-slot-1", true).performTouchInput {
                val target = key.center - symbol.topLeft
                for (step in 1..8) moveTo(center + (target - center) * (step / 8f))
                up()
            }
            rule.waitForIdle()
        }
        assertEquals(shortcutLetters.map { "ctrl:$it" }, commands)
        assertEquals(emptyList<String>(), keys)
    }

    @Test fun lettersTypeNormallyOnceTheSymbolKeyIsReleased() {
        show()
        rule.onNodeWithTag("qwerty-key:v", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(listOf("v"), keys)
        assertEquals(emptyList<String>(), commands)
    }

    @Test fun ctrlEventsMatchAPhysicalKeyboard() {
        assertEquals('c', CtrlShortcuts.letterOf(CtrlShortcuts.command('C')))
        assertEquals(null, CtrlShortcuts.letterOf("ctrl:1"))
        val events = CtrlShortcuts.keyEvents('v', 5L)
        assertEquals(listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_CTRL_LEFT),
            events.map { it.keyCode })
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP, KeyEvent.ACTION_UP), events.map { it.action })
        assertTrue("字母带 Ctrl", events[1].isCtrlPressed && events[2].isCtrlPressed)
        assertEquals(KeyEvent.KEYCODE_Z, CtrlShortcuts.keyEvents('z', 0L)[1].keyCode)
    }
}
