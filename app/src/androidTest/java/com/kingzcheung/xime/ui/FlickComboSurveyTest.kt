package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Survey of nine-key (Chinese T9, Japanese kana) flick feedback across flick/disc switches and
 * fixed/floating phone/tablet sizes. Screenshots plus the committed keys of every flick.
 */
class FlickComboSurveyTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = SettingsPreferences.getPrefsPublic(app)
    private val keys = listOf("t9_flick_letters", "flick_direction_disc_v2", "fourteen_letter_swipe_dp",
        SettingsPreferences.KEY_SHOW_PRESS_BUBBLE, SettingsPreferences.KEY_SPLIT_KEYBOARD)
    private var original: Map<String, Any?> = emptyMap()
    private val commits: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val report = StringBuilder()

    private data class Mode(val name: String, val screenW: Int, val screenH: Int, val floating: Boolean)
    private val modes = listOf(
        Mode("phone-fixed", 400, 820, false), Mode("phone-float", 400, 820, true),
        Mode("tablet-fixed", 1280, 800, false), Mode("tablet-float", 1280, 800, true))

    @Before fun before() {
        original = keys.associateWith { prefs.all[it] }
        KeysConfigHelper.loadConfig(app)
    }

    @After fun after() {
        rule.mainClock.autoAdvance = true
        prefs.edit().also { edit -> original.forEach { (name, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(name, value)
                is Float -> edit.putFloat(name, value)
                else -> edit.remove(name)
            }
        } }.commit()
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    @Test fun t9FlickOff() = survey("t9", flick = false, disc = false)
    @Test fun t9FlickOn() = survey("t9", flick = true, disc = false)
    @Test fun t9FlickOnDisc() = survey("t9", flick = true, disc = true)
    @Test fun kana() = survey("kana", flick = false, disc = false)
    @Test fun kanaDisc() = survey("kana", flick = false, disc = true)

    private fun survey(layout: String, flick: Boolean, disc: Boolean) {
        prefs.edit().putBoolean("t9_flick_letters", flick).putBoolean("flick_direction_disc_v2", disc)
            .putFloat("fourteen_letter_swipe_dp", 24f).putBoolean(SettingsPreferences.KEY_SHOW_PRESS_BUBBLE, false)
            .putBoolean(SettingsPreferences.KEY_SPLIT_KEYBOARD, false).commit()
        val schema = if (layout == "t9") "t9_pinyin" else "japanese_kana"
        KeysConfigHelper.setActiveKeyboardSchema(schema)
        val modeState = androidx.compose.runtime.mutableStateOf(modes.first())
        rule.setContent {
            val mode = modeState.value
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = mode.screenW; screenHeightDp = mode.screenH
                orientation = if (mode.screenW > mode.screenH) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.requiredSize(mode.screenW.dp, mode.screenH.dp).background(Color(0xFF6B7A8F))
                        .testTag("host"), contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.runtime.key(mode) {
                            val vm = remember { KeyboardViewModel(app) }
                            val height = if (mode.screenW > mode.screenH) 300 else 260
                            val width = if (mode.floating) 0 else mode.screenW
                            KeyboardView(vm, KeyboardUiState(currentSchemaId = schema, isDarkTheme = true,
                                isFloatingMode = mode.floating, keyboardHeightDp = height,
                                keyboardWidthDp = width, fixedWidthDp = width,
                                floatingScreenHeightDp = mode.screenH),
                                KeyboardCallbacks(onKeyPress = { text, _ -> commits += "key:$text" },
                                    onCandidateSelect = {}, onCommitText = { commits += "lit:$it" },
                                    onPreeditKeyInput = { false }, onHideKeyboard = {}, onUpdateToolbarButtons = {},
                                    onJapaneseKanaAction = { commits += "kana:$it" }),
                                modifier = if (mode.floating) Modifier.requiredSize(mode.screenW.dp, mode.screenH.dp)
                                    else Modifier.requiredSize(mode.screenW.dp, (height + 44).dp))
                        }
                    }
                }
            }
        }
        val name = "$layout-flick${if (flick) 1 else 0}-disc${if (disc) 1 else 0}"
        for (mode in modes) {
            rule.runOnIdle { modeState.value = mode; commits.clear() }
            rule.waitForIdle()
            val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val node = if (layout == "t9") rule.onAllNodesWithText("JKL", useUnmergedTree = true).onFirst()
                else rule.onNodeWithTag("kana-key:na")
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val c = bounds.center - host.topLeft
            val unit = with(rule.density) { 1.dp.toPx() }
            val prefix = "$name/${mode.name}"
            report.append("$prefix key=${bounds.width / unit}x${bounds.height / unit}dp\n")
            shot("$prefix-0-idle")
            rule.mainClock.autoAdvance = false
            fun step(name: String, block: TouchInjectionScope.() -> Unit) {
                rule.onNodeWithTag("host").performTouchInput(block)
                rule.mainClock.advanceTimeBy(40)
                shot("$prefix-$name")
            }
            step("1-held") { down(c) }
            step("2-left-half") { moveTo(c + Offset(-10f * unit, 0f)) }
            step("3-left-full") { moveTo(c + Offset(-40f * unit, 0f)) }
            step("4-to-up") { moveTo(c + Offset(0f, -40f * unit)) }
            rule.onNodeWithTag("host").performTouchInput { up() }
            rule.mainClock.advanceTimeBy(100)
            report.append("$prefix left->up commits=${commits.toList()}\n"); commits.clear()
            step("5-down-full") { down(c); moveTo(c + Offset(0f, 12f * unit)); moveTo(c + Offset(0f, 40f * unit)) }
            rule.onNodeWithTag("host").performTouchInput { up() }
            rule.mainClock.advanceTimeBy(100)
            report.append("$prefix down commits=${commits.toList()}\n"); commits.clear()
            rule.onNodeWithTag("host").performTouchInput { down(c); moveTo(c + Offset(-12f * unit, 0f)); moveTo(c + Offset(-30f * unit, 0f)); up() }
            rule.mainClock.advanceTimeBy(100)
            report.append("$prefix left30 commits=${commits.toList()}\n"); commits.clear()
            rule.onNodeWithTag("host").performTouchInput { down(c); moveTo(c + Offset(-15f * unit, 0f)); moveTo(c + Offset(-40f * unit, 0f)); moveTo(c + Offset(-30f * unit, -45f * unit)); up() }
            rule.mainClock.advanceTimeBy(100)
            report.append("$prefix left-then-diagonal-up commits=${commits.toList()}\n"); commits.clear()
            rule.onNodeWithTag("host").performTouchInput { down(c) }
            rule.mainClock.advanceTimeBy(600)
            shot("$prefix-6-long")
            rule.onNodeWithTag("host").performTouchInput { up() }
            rule.mainClock.advanceTimeBy(100)
            report.append("$prefix long commits=${commits.toList()}\n"); commits.clear()
            rule.mainClock.autoAdvance = true
        }
        File(app.getExternalFilesDir(null), "flick-combo").apply { mkdirs() }.resolve("$name.txt").writeText(report.toString())
    }

    private fun shot(name: String) {
        // Popup windows compose a frame after they are added; let a few frames through.
        if (!rule.mainClock.autoAdvance) repeat(4) { Thread.sleep(40); rule.mainClock.advanceTimeByFrame() }
        Thread.sleep(150)
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(app.getExternalFilesDir(null), "flick-combo/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
