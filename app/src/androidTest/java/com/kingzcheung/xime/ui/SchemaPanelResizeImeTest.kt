package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.KeyboardHeightProfiles
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real IME window: opening the selector must never replace the saved keyboard height. */
class SchemaPanelResizeImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun minimumThenTallKeyboardKeepsItsHeightAcrossOpeningTheSelector() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val landscape = context.resources.configuration.screenWidthDp > context.resources.configuration.screenHeightDp
        val suffix = if (landscape) "_landscape" else ""
        val keys = listOf("keyboard_height_dp$suffix", "floating_mode$suffix", "toolbar_buttons", "keyboard_bottom_padding_dp")
        val saved = keys.associateWith { prefs.all[it] }
        lateinit var editor: EditText
        try {
            SettingsPreferences.setFloatingMode(context, false, landscape)
            SettingsPreferences.setKeyboardBottomPaddingDp(context, 0)
            SettingsPreferences.setToolbarButtons(context, listOf("schema", "float"))
            rule.setContent {
                AndroidView(factory = { EditText(it).also { field -> editor = field; field.hint = "输入方式高度回归" } },
                    modifier = Modifier.fillMaxWidth().height(80.dp))
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val range = KeyboardHeightProfiles.fixedBounds(context, landscape)
            var smallHeight = 0f
            for ((index, target) in listOf(range.first, range.last).withIndex()) {
                KeyboardHeightProfiles.save(context, false, landscape, target, context.resources.configuration.screenHeightDp)
                rule.runOnUiThread {
                    editor.requestFocus()
                    imm.restartInput(editor)
                    imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                }
                rule.waitUntil(30_000) {
                    rule.onAllNodesWithContentDescription("输入方式").fetchSemanticsNodes().isNotEmpty() &&
                        rule.onAllNodesWithTag("fixed-keyboard-card").fetchSemanticsNodes().let { nodes ->
                            nodes.size == 1 && (index == 0 || nodes.single().size.height > smallHeight + 16)
                        }
                }
                val before = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode().size.height.toFloat()
                if (index == 0) smallHeight = before
                repeat(2) {
                    rule.onNodeWithContentDescription("输入方式").performClick()
                    rule.waitUntil(5_000) { rule.onAllNodesWithTag("input-profile-panel").fetchSemanticsNodes().isNotEmpty() }
                    rule.waitForIdle()
                    assertEquals("Opening the selector must preserve actual IME height", before,
                        rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode().size.height.toFloat(), 2f)
                    assertEquals(target, prefs.getInt("keyboard_height_dp$suffix", -1))
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val dir = File(context.cacheDir, "ui-verification").apply { mkdirs() }
                    File(dir, "real-ime-profile-$index-$it.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                    bitmap.recycle()
                    rule.onNodeWithContentDescription("返回").performClick()
                    rule.waitUntil(5_000) { rule.onAllNodesWithTag("input-profile-panel").fetchSemanticsNodes().isEmpty() }
                    assertEquals(before, rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode().size.height.toFloat(), 2f)
                }
            }
        } finally {
            prefs.edit().also { edit ->
                saved.forEach { (key, value) -> when (value) {
                    null -> edit.remove(key)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is String -> edit.putString(key, value)
                } }
            }.commit()
        }
    }
}
