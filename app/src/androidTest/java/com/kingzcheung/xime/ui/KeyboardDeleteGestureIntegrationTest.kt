package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Real layout wiring, including the competing whole-keyboard cursor gesture.
 * The T9 editor callback records deletion at the host boundary without starting native Rime.
 */
class KeyboardDeleteGestureIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = SettingsPreferences.getPrefsPublic(app)
    private val preferenceKeys = listOf("cursor_gesture_mode", "symbol_input_mode", "reverse_symbol_swipe",
        SettingsPreferences.KEY_SPLIT_KEYBOARD, SettingsPreferences.KEY_SHOW_PRESS_BUBBLE)
    private var originalPreferences: Map<String, Any?> = emptyMap()
    private val schema = mutableStateOf("rime_ice")
    private val actions = mutableListOf<String>()
    private val cursorMoves = mutableListOf<Int>()
    private var activeCallbacks: KeyboardCallbacks? = null
    private val layouts = listOf("rime_ice", "pinyin_14jian", "t9_pinyin")

    @Before fun before() {
        originalPreferences = preferenceKeys.associateWith { prefs.all[it] }
        prefs.edit().putString("cursor_gesture_mode", "KEYBOARD")
            .putString("symbol_input_mode", SymbolInputMode.SWIPE_UP.name)
            .putBoolean("reverse_symbol_swipe", false)
            .putBoolean(SettingsPreferences.KEY_SPLIT_KEYBOARD, false)
            .putBoolean(SettingsPreferences.KEY_SHOW_PRESS_BUBBLE, false).commit()
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    @After fun after() {
        rule.mainClock.autoAdvance = true
        prefs.edit().also { edit -> originalPreferences.forEach { (name, value) ->
            when (value) {
                is String -> edit.putString(name, value)
                is Boolean -> edit.putBoolean(name, value)
                else -> edit.remove(name)
            }
        } }.commit()
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    private fun show() {
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 600; screenHeightDp = 800
                orientation = Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.requiredSize(600.dp, 700.dp).testTag("delete-host"), contentAlignment = Alignment.Center) {
                        key(schema.value) {
                            val vm = remember { KeyboardViewModel(app) }
                            val callbacks = remember {
                                KeyboardCallbacks(onKeyPress = { text, _ -> actions += text },
                                    onCandidateSelect = {}, onCommitText = { actions += "literal:$it" },
                                    onCursorMove = { cursorMoves += it })
                            }
                            activeCallbacks = callbacks
                            KeyboardView(vm,
                                KeyboardUiState(currentSchemaId = schema.value, keyboardHeightDp = 320,
                                    keyboardWidthDp = 360, fixedWidthDp = 360, isDarkTheme = true),
                                callbacks, modifier = Modifier.size(360.dp, 320.dp))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun activate(layout: String, reverse: Boolean, mode: SymbolInputMode = SymbolInputMode.SWIPE_UP) {
        rule.runOnIdle {
            prefs.edit().putBoolean("reverse_symbol_swipe", reverse).putString("symbol_input_mode", mode.name).commit()
            KeysConfigHelper.setActiveKeyboardSchema(layout)
            schema.value = layout
        }
        rule.waitForIdle()
        deleteKey().assertIsDisplayed()
        rule.runOnIdle {
            // KeyboardView owns this callback during composition. Install the test editor after it
            // is settled, so a real T9 delete is observed before its native-engine fallback.
            activeCallbacks!!.onPreeditKeyInput = { key -> actions += key; true }
            actions.clear(); cursorMoves.clear()
        }
    }

    private fun deleteKey() = rule.onNodeWithTag(if (schema.value == "t9_pinyin") "t9-delete-key" else "qwerty-delete-key")

    private fun start(): Offset {
        val host = rule.onNodeWithTag("delete-host").fetchSemanticsNode().boundsInRoot
        return deleteKey().fetchSemanticsNode().boundsInRoot.center - host.topLeft
    }

    @Test fun actualClearUndoDirectionLabelsAndColorsFollowPreferenceAndCommitOnlyOnRelease() {
        show()
        for (layout in layouts) for (reverse in listOf(false, true)) for (clear in listOf(true, false)) {
            activate(layout, reverse)
            val origin = start()
            // Default: pull the top clear label down; reverse: push up. Undo uses the other direction.
            val dy = if (clear == reverse) -95f else 95f
            rule.onNodeWithTag("delete-host").performTouchInput { down(origin); moveTo(origin + Offset(0f, dy)) }
            rule.onNodeWithTag("key-flick-preview").assertTextEquals(if (clear) "清空" else "撤回")
            val pixels = deleteKey().captureToImage().toPixelMap()
            val color = pixels[pixels.width / 4, pixels.height / 4]
            val case = "$layout reverse=$reverse clear=$clear"
            assertTrue("$case must show the action's semantic color",
                if (clear) color.red > color.blue + .1f else color.blue > color.red + .1f)
            rule.runOnIdle { assertTrue("$case must wait for release", actions.isEmpty()); assertTrue(cursorMoves.isEmpty()) }
            rule.onNodeWithTag("delete-host").performTouchInput { up() }
            rule.runOnIdle {
                assertEquals(case, listOf(if (clear) "clear_all" else "undo_clear"), actions)
                assertTrue("$case must not move the application cursor", cursorMoves.isEmpty())
            }
            rule.onNodeWithTag("key-flick-preview").assertDoesNotExist()
        }
    }

    @Test fun backtrackingAndSystemCancellationNeverClearUndoOrDelete() {
        show()
        for (layout in layouts) for (reverse in listOf(false, true)) for (dy in listOf(-95f, 95f)) {
            for (systemCancel in listOf(false, true)) {
                activate(layout, reverse)
                val origin = start()
                rule.onNodeWithTag("delete-host").performTouchInput {
                    down(origin); moveTo(origin + Offset(0f, dy))
                    if (systemCancel) cancel() else { moveTo(origin); up() }
                }
                rule.runOnIdle {
                    assertTrue("$layout reverse=$reverse dy=$dy cancel=$systemCancel", actions.isEmpty())
                    assertTrue(cursorMoves.isEmpty())
                }
                rule.onNodeWithTag("key-flick-preview").assertDoesNotExist()
                rule.onNodeWithTag("delete-host").performTouchInput { down(origin); up() }
                rule.runOnIdle { assertEquals("next tap must still delete exactly once", listOf("delete"), actions) }
            }
        }
    }

    @Test fun tapAndHoldStillDeleteWithJitterAndReleaseStopsRepeatingInBothSymbolModes() {
        show()
        for (layout in layouts) for (reverse in listOf(false, true)) for (mode in SymbolInputMode.entries) {
            activate(layout, reverse, mode)
            val origin = start()
            rule.onNodeWithTag("delete-host").performTouchInput { down(origin); up() }
            rule.runOnIdle { assertEquals(listOf("delete"), actions); actions.clear() }
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("delete-host").performTouchInput { down(origin); moveTo(origin + Offset(20f, 20f)) }
            rule.mainClock.advanceTimeBy(750L)
            var beforeMove = 0
            rule.runOnIdle { beforeMove = actions.size; assertTrue("$layout must repeat", beforeMove > 1) }
            rule.onNodeWithTag("delete-host").performTouchInput { moveTo(origin + Offset(25f, -35f)) }
            rule.mainClock.advanceTimeBy(200L)
            rule.runOnIdle { assertTrue("small movement must not stop repeat", actions.size > beforeMove) }
            rule.onNodeWithTag("delete-host").performTouchInput { up() }
            var stopped = 0
            rule.runOnIdle { stopped = actions.size; assertTrue(actions.all { it == "delete" }); assertTrue(cursorMoves.isEmpty()) }
            rule.mainClock.advanceTimeBy(200L)
            rule.runOnIdle { assertEquals("release must neither tap again nor leave queued repeats", stopped, actions.size) }
            rule.mainClock.autoAdvance = true
        }
    }
}
