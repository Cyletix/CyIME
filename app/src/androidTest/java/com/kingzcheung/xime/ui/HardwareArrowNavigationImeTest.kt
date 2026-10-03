package com.kingzcheung.xime.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual hardware events, native candidates and host selection; no candidate-state injection. */
class HardwareArrowNavigationImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativePinyinArrowsUseVisibleSelectionInFloatingAndScreenKeyboard() = withIme("rime_ice") {
        verifyChineseNavigationInBothSurfaces()
    }

    @Test fun physicalLettersWithT9LayoutUseVisibleSelectionInBothSurfaces() = withIme("t9_pinyin") {
        verifyChineseNavigationInBothSurfaces()
    }

    @Test fun englishSuggestionsNavigateAndCancelInFloatingAndScreenKeyboard() = withIme("rime_ice") {
        key("SHIFT_LEFT")
        rule.waitUntil(10_000) { engine.isAsciiMode() && languageIs("EN") }
        for (full in listOf(false, true)) {
            resetEditor("before ")
            surface(full)
            key("H", "E", "L")
            rule.waitUntil(10_000) { document() == "before hel" && englishCompletion() != null }
            val caret = selection()
            val word = requireNotNull(englishCompletion())
            key("DPAD_RIGHT")
            rule.waitUntil(10_000) { selectedText(word) }
            assertEquals("English highlighting must not move the host caret (full=$full)", caret, selection())
            key("DPAD_LEFT")
            rule.waitUntil(10_000) { selectedText("hel") }
            key("DPAD_RIGHT")
            rule.waitUntil(10_000) { selectedText(word) }
            key("SPACE")
            rule.waitUntil(10_000) { document() == "before $word" }
            assertEquals("Only the highlighted completion replaces the pending prefix", "before $word", document())

            resetEditor("before ")
            surface(full)
            key("H", "E", "L")
            rule.waitUntil(10_000) { document() == "before hel" && englishCompletion() != null }
            key("ESCAPE", "DPAD_LEFT")
            rule.waitUntil(10_000) { selection() == (9 to 9) }
            key("DPAD_RIGHT")
            rule.waitUntil(10_000) { selection() == (10 to 10) }
            assertEquals("Esc cancels suggestions without removing typed English", "before hel", document())

            resetEditor("before ")
            surface(full)
            key("H", "E", "L")
            rule.waitUntil(10_000) { document() == "before hel" }
            key("DEL", "DEL", "DEL", "DPAD_LEFT")
            rule.waitUntil(10_000) { document() == "before " && selection() == (6 to 6) }
            key("DPAD_RIGHT")
            rule.waitUntil(10_000) { selection() == (7 to 7) }
        }
    }

    @Test fun sideDockHiddenEnglishSuggestionsDoNotStealEditorArrows() = withIme("rime_ice") {
        key("SHIFT_LEFT")
        rule.waitUntil(10_000) { engine.isAsciiMode() && languageIs("EN") }
        resetEditor("before ")
        toolbarAction("靠左")
        rule.waitUntil(5_000) {
            val node = rule.onNodeWithTag("hardware-keyboard-toolbar", true).fetchSemanticsNode()
            node.size.height > node.size.width && !has("hardware-docked-candidates")
        }
        key("H", "E", "L")
        rule.waitUntil(10_000) { document() == "before hel" }
        assertFalse("Side docking intentionally has no suggestion row", has("hardware-docked-candidates"))
        assertFalse("English does not create an independent candidate popup", has("hardware-candidate-card"))
        key("DPAD_LEFT")
        rule.waitUntil(10_000) { selection() == (9 to 9) }
        key("DPAD_RIGHT")
        rule.waitUntil(10_000) { selection() == (10 to 10) }
        assertEquals("Invisible suggestions must not consume arrows or alter host text", "before hel", document())
        key("SPACE")
        rule.waitUntil(10_000) { document() == "before hel " }
    }

    private inner class Fixture(val context: Context, val engine: RimeEngine, val editor: EditText) {
        fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
        fun languageIs(label: String) = rule.onAllNodesWithTag("hardware-toolbar-language", true)
            .fetchSemanticsNodes().any { node ->
                SemanticsProperties.ContentDescription in node.config &&
                    node.config[SemanticsProperties.ContentDescription].any { it.startsWith("当前语言：$label，") }
            }
        fun document(): String {
            var text = ""
            rule.runOnUiThread { text = editor.text.toString() }
            return text
        }
        fun selection(): Pair<Int, Int> {
            var result = -1 to -1
            rule.runOnUiThread { result = editor.selectionStart to editor.selectionEnd }
            return result
        }
        fun key(vararg names: String) { shell("input keyboard keyevent " + names.joinToString(" ") { "KEYCODE_$it" }) }
        fun selected(index: Int) = rule.onAllNodesWithTag("bar-candidate:$index", true)
            .fetchSemanticsNodes().any { node ->
                SemanticsProperties.Selected in node.config && node.config[SemanticsProperties.Selected]
            }
        private val candidateNode = SemanticsMatcher("candidate row item") { node ->
            val tag = if (SemanticsProperties.TestTag in node.config) node.config[SemanticsProperties.TestTag] else ""
            tag.startsWith("bar-candidate:") || tag.startsWith("bar-association:")
        }
        fun selectedText(text: String) = rule.onAllNodes(candidateNode and hasText(text) and isSelected())
            .fetchSemanticsNodes().isNotEmpty()
        fun candidate(index: Int): String = rule.onNodeWithTag("bar-candidate:$index")
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text.removePrefix("${(index + 1) % 10} ")
        // Right from the raw prefix chooses index 1 in both actual renderers.
        // Do not accidentally read a later visible suggestion or another selected control.
        fun englishCompletion(): String? = rule.onAllNodes(
            hasTestTag("bar-candidate:1") or hasTestTag("bar-association:1"))
            .fetchSemanticsNodes().mapNotNull { node ->
                if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text].firstOrNull()?.text else null
            }.firstOrNull { it.startsWith("hel", true) && it.length > 3 }

        fun resetEditor(text: String, cursor: Int = text.length) {
            key("ESCAPE")
            rule.waitUntil(10_000) { engine.getInput().isEmpty() }
            rule.runOnUiThread {
                editor.setText(text)
                editor.setSelection(cursor)
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(editor)
            }
            rule.waitUntil(10_000) { document() == text && selection() == (cursor to cursor) && engine.getInput().isEmpty() }
            rule.waitForIdle()
        }
        fun toolbarAction(label: String) {
            val action = rule.onNodeWithTag("hardware-toolbar-drag", true).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == label }
            rule.runOnIdle { assertTrue(action.action()) }
        }
        fun surface(full: Boolean) {
            if (full) {
                if (has("hardware-toolbar-keyboard")) rule.onNodeWithTag("hardware-toolbar-keyboard", true).performClick()
                rule.waitUntil(10_000) { has("keyboard-underlay") && !has("hardware-keyboard-toolbar") }
            } else {
                if (!has("hardware-keyboard-toolbar")) key("BACK")
                rule.waitUntil(10_000) { has("hardware-keyboard-toolbar") }
                toolbarAction("停靠底部")
                rule.waitForIdle()
                val before = rule.onNodeWithTag("hardware-keyboard-toolbar", true).fetchSemanticsNode().positionOnScreen.y
                val dy = 150 * context.resources.displayMetrics.density
                rule.onNodeWithTag("hardware-toolbar-drag", true).performTouchInput {
                    swipe(center, center - Offset(0f, dy), durationMillis = 400)
                }
                rule.waitUntil(5_000) {
                    before - rule.onNodeWithTag("hardware-keyboard-toolbar", true).fetchSemanticsNode().positionOnScreen.y > dy / 2
                }
            }
        }

        fun verifyChineseNavigationInBothSurfaces() {
            val original = "第一行正文\n第二行正文\n第三行正文"
            val cursor = original.indexOf("第二") + 3
            for (full in listOf(false, true)) {
                resetEditor(original, cursor)
                surface(full)
                // Batched input checks arrows after native processing, even before UI publication.
                key("N", "I", "DPAD_RIGHT")
                rule.waitUntil(15_000) { engine.getInput().isNotEmpty() && selected(1) }
                assertEquals("Arrow focus must be visible without changing the host document", original, document())
                assertEquals(cursor to cursor, selection())
                val selectedWord = candidate(1)
                assertTrue("Use genuine Chinese engine candidates", selectedWord.any { it.code in 0x4E00..0x9FFF })
                key("DPAD_LEFT")
                rule.waitUntil(10_000) { selected(0) }
                key("DPAD_RIGHT")
                rule.waitUntil(10_000) { selected(1) }
                key("SPACE")
                val expected = original.substring(0, cursor) + selectedWord + original.substring(cursor)
                rule.waitUntil(10_000) { engine.getInput().isEmpty() && document() == expected }

                resetEditor(original, cursor)
                surface(full)
                key("N", "I")
                rule.waitUntil(15_000) { engine.getInput().isNotEmpty() && has("bar-candidate:1") }
                val input = engine.getInput()
                assertFalse("Boundary fixture begins on the first candidate page", engine.hasPrevPage())
                key("DPAD_UP", "DPAD_RIGHT")
                rule.waitUntil(10_000) { selected(1) }
                assertEquals("Up at the first candidate page must not move into the preceding text line", cursor to cursor, selection())
                assertEquals(input, engine.getInput())
                if (engine.hasNextPage()) {
                    key("DPAD_DOWN")
                    rule.waitUntil(10_000) { engine.hasPrevPage() }
                    assertEquals("Candidate paging must not move the host caret down", cursor to cursor, selection())
                    key("DPAD_UP")
                    rule.waitUntil(10_000) { !engine.hasPrevPage() }
                } else {
                    key("DPAD_DOWN", "DPAD_LEFT")
                    rule.waitUntil(10_000) { selected(0) }
                    assertEquals("Down at the final candidate page must not move the host caret", cursor to cursor, selection())
                }
                assertEquals(original, document())
                key("ESCAPE", "DPAD_LEFT")
                rule.waitUntil(10_000) { engine.getInput().isEmpty() && selection() == (cursor - 1 to cursor - 1) }
                key("DPAD_RIGHT")
                rule.waitUntil(10_000) { selection() == (cursor to cursor) }
                assertEquals("Cancelled pinyin must not be committed by cursor navigation", original, document())

                key("N", "I")
                rule.waitUntil(15_000) { engine.getInput().isNotEmpty() }
                key("DEL", "DEL", "DPAD_LEFT")
                rule.waitUntil(10_000) { engine.getInput().isEmpty() && selection() == (cursor - 1 to cursor - 1) }
                assertEquals("Deleting the composition must preserve existing host text", original, document())
            }
        }
    }

    private fun withIme(schema: String, block: Fixture.() -> Unit): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Connect a physical keyboard; a skipped test is not device validation",
            context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS)
        val prefs = SettingsPreferences.getPrefsPublic(context)
        fun fixtureKey(key: String) = key.startsWith(HardwareKeyboardPreferences.PREFIX) ||
            key.startsWith("current_schema") || key.startsWith("last_input_mode_") ||
            key.startsWith("selected_input_profile_") || key == LanguagePreferences.KEY ||
            key == "input_language_order" || key == "input_text_location" || key == "space_commit_association"
        val savedPrefs = prefs.all.filterKeys(::fixtureKey).mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        val oldIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        assertTrue("The real engine must be prepared", RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val savedSchema = engine.getCurrentSchema()
        val savedOptions = listOf("ascii_mode", "full_shape", "ascii_punct").associateWith(engine::getOption)
        val savedAscii = engine.getUserConfigBool("var/option/ascii_mode")
        val savedPrevious = engine.getUserConfigString("var/previously_selected_schema")
        val schemaFiles = listOf("default.custom.yaml", "default.yaml").associate { name ->
            val file = File(context.filesDir, "rime/$name")
            file to file.takeIf { it.isFile }?.readBytes()
        }
        lateinit var editor: EditText
        var ready = false
        try {
            assertTrue("The requested real schema must already be compiled: $schema", SchemaManager.isSchemaCompiled(context, schema))
            SchemaManager.setEnabledSchemas(context, (SchemaManager.getEnabledSchemas(context) + schema).distinct())
            prefs.edit().putStringSet(LanguagePreferences.KEY, setOf("zh", "en"))
                .putString("input_language_order", "zh\nen")
                .putString("selected_input_profile_zh", schema)
                .putString("input_text_location", SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR)
                .putBoolean("space_commit_association", false)
                .putBoolean(HardwareKeyboardPreferences.PREFIX + "shift_tap", true)
                .putBoolean(HardwareKeyboardPreferences.PREFIX + "dock_edge", true).commit()
            assertTrue(engine.switchSchema(schema))
            engine.clearComposition()
            engine.setOption("ascii_mode", false)
            engine.setUserConfigBool("var/option/ascii_mode", false)
            SettingsPreferences.setCurrentSchema(context, schema)
            shell("ime set ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
            rule.setContent {
                AndroidView(factory = { ctx ->
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(80, 140, 80, 0)
                        editor = EditText(ctx).apply {
                            hint = "Disposable hardware arrow test editor"
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            minLines = 3
                        }
                        ready = true
                        addView(editor, LinearLayout.LayoutParams(-1, 280))
                    }
                }, modifier = Modifier.fillMaxSize())
            }
            rule.runOnUiThread {
                editor.requestFocus()
                editor.post {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.restartInput(editor)
                    imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
                }
            }
            val fixture = Fixture(context, engine, editor)
            // A live service preserves an explicitly opened screen keyboard across editors
            // and test activities. Start each scenario from the real compact toolbar.
            rule.waitUntil(20_000) { fixture.has("hardware-keyboard-toolbar") || fixture.has("keyboard-underlay") }
            if (!fixture.has("hardware-keyboard-toolbar")) fixture.key("BACK")
            rule.waitUntil(20_000) { fixture.has("hardware-keyboard-toolbar") && fixture.languageIs("中") }
            fixture.block()
        } finally {
            if (ready) rule.runOnUiThread {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
                editor.clearFocus()
            }
            engine.clearComposition()
            if (savedSchema.isNotBlank()) engine.switchSchema(savedSchema)
            savedOptions.forEach { (key, value) -> engine.setOption(key, value) }
            engine.setUserConfigBool("var/option/ascii_mode", savedAscii)
            if (savedPrevious != null) engine.setUserConfigString("var/previously_selected_schema", savedPrevious)
            schemaFiles.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
            val edit = prefs.edit()
            (prefs.all.keys.filter(::fixtureKey) + savedPrefs.keys).distinct().forEach { restore(edit, it, savedPrefs[it]) }
            edit.commit()
            if (!oldIme.isNullOrBlank() && oldIme != "null") shell("ime set $oldIme")
        }
    }

    private fun restore(edit: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> edit.remove(key)
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
