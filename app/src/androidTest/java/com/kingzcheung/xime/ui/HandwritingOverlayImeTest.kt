package com.kingzcheung.xime.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.handwriting.HandwritingEngine
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.LanguagePreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the actual service's mode, IME insets and Back dispatch in a disposable editor. */
@SdkSuppress(minSdkVersion = 30)
class HandwritingOverlayImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun toolsKeepFullscreenHandwritingAndItsHostInsetsUntilBackLeavesTheTool() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Install the handwriting model before this real-IME check", HandwritingEngine.hasModel(context))
        assertTrue("The actual engine must be ready", RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val previousSchema = engine.getCurrentSchema()
        val previousAscii = engine.isAsciiMode()
        val previousSavedAscii = engine.getUserConfigBool("var/option/ascii_mode")
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val fixtureKeys = setOf("toolbar_buttons", LanguagePreferences.KEY,
            "floating_mode", "floating_mode_landscape", "fixed_width_dp", "fixed_width_dp_landscape",
            "fixed_offset_x", "fixed_offset_x_landscape", "keyboard_height_dp", "keyboard_height_dp_landscape",
            "keyboard_bottom_padding_dp", "keyboard_bottom_padding_dp_landscape")
        fun fixtureKey(key: String) = key in fixtureKeys || key.startsWith("current_schema") ||
            key.startsWith("last_input_mode_") || key.startsWith("selected_input_profile_")
        val saved = prefs.all.filterKeys(::fixtureKey).mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val ime = "${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService"
        val enabledBefore = shell("ime list -s").lineSequence().any { it.trim() == ime }
        lateinit var editor: EditText
        var editorReady = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun has(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        fun hostInset(): Int {
            var inset = -1
            rule.runOnUiThread { inset = editor.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: -1 }
            return inset
        }
        try {
            prefs.edit().apply {
                putStringSet(LanguagePreferences.KEY, setOf(InputLanguage.CHINESE.id, InputLanguage.ENGLISH.id))
                putString("toolbar_buttons", "handwriting_lookup,clipboard,emoji,schema")
                for (suffix in listOf("", "_landscape")) {
                    putBoolean("floating_mode$suffix", false)
                    putInt("fixed_width_dp$suffix", 300)
                    putInt("fixed_offset_x$suffix", -50)
                    putInt("keyboard_height_dp$suffix", 260)
                    putInt("keyboard_bottom_padding_dp$suffix", 0)
                }
            }.commit()
            SettingsPreferences.setCurrentSchema(context, "rime_ice")
            assertTrue(engine.switchSchema("rime_ice"))
            engine.setOption("ascii_mode", false)
            engine.setUserConfigBool("var/option/ascii_mode", false)
            shell("ime enable $ime")
            shell("ime set $ime")
            rule.runOnUiThread {
                rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            rule.setContent {
                AndroidView(factory = { EditText(it).also { view ->
                    editor = view
                    editorReady = true
                    view.hint = "全屏手写菜单测试"
                } }, modifier = Modifier.fillMaxWidth().height(80.dp))
            }
            rule.runOnUiThread {
                editor.requestFocus()
                imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
            }
            rule.waitUntil(30_000) { has("keyboard-underlay") || has("hardware-toolbar-keyboard") }
            if (!has("keyboard-underlay")) rule.onNodeWithTag("hardware-toolbar-keyboard").performClick()
            rule.waitUntil(30_000) { has("keyboard-underlay") }
            rule.onNodeWithContentDescription("手写").performClick()
            rule.onNodeWithTag("handwriting-key:expand").assertExists()
            val normalFooter = rule.onNodeWithTag("handwriting-controls").fetchSemanticsNode().size
            rule.onNodeWithTag("handwriting-key:expand").performClick()
            rule.waitForIdle()
            val expandedBody = rule.onNodeWithTag("keyboard-underlay").fetchSemanticsNode().size
            val expandedFooter = rule.onNodeWithTag("handwriting-controls").fetchSemanticsNode().size
            val inset = hostInset()
            assertTrue("Host must retain the normal keyboard reservation", inset > 0)
            assertEquals(normalFooter.width, expandedFooter.width)

            for (tool in listOf("剪贴板", "表情", "输入方案")) {
                rule.onNodeWithContentDescription(tool).performClick()
                rule.waitForIdle()
                assertTrue("$tool must open its overlay", has("keyboard-overlay"))
                assertEquals("$tool must not collapse the actual service window", expandedBody,
                    rule.onNodeWithTag("keyboard-underlay").fetchSemanticsNode().size)
                assertEquals("$tool must not change the host's reserved area", inset, hostInset())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                rule.waitUntil(10_000) { !has("keyboard-overlay") }
                rule.onNodeWithTag("handwriting-key:collapse").assertExists()
                assertEquals(expandedFooter, rule.onNodeWithTag("handwriting-controls").fetchSemanticsNode().size)
                assertEquals(expandedBody, rule.onNodeWithTag("keyboard-underlay").fetchSemanticsNode().size)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.waitUntil(10_000) { has("handwriting-key:expand") }
            assertEquals(normalFooter, rule.onNodeWithTag("handwriting-controls").fetchSemanticsNode().size)
            rule.runOnUiThread { assertEquals("", editor.text.toString()) }
        } finally {
            if (editorReady) rule.runOnUiThread {
                imm.hideSoftInputFromWindow(editor.windowToken, 0)
                editor.clearFocus()
            }
            if (previousSchema.isNotBlank()) engine.switchSchema(previousSchema)
            engine.setOption("ascii_mode", previousAscii)
            engine.setUserConfigBool("var/option/ascii_mode", previousSavedAscii)
            prefs.edit().apply {
                prefs.all.keys.filter(::fixtureKey).forEach(::remove)
                saved.forEach { (key, value) -> when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
            if (!previousIme.isNullOrBlank()) shell("ime set $previousIme")
            if (!enabledBefore) shell("ime disable $ime")
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
