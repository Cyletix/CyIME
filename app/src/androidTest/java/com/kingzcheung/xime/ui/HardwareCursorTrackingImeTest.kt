package com.kingzcheung.xime.ui

import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeEngine
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real editor-to-IME cursor reporting, with a connected physical keyboard. No user document is edited. */
class HardwareCursorTrackingImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeEditorCaretAndScrolledEditorMoveTheRealCandidateWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS)
        val oldIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val ime = "${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService"
        val density = context.resources.displayMetrics.density
        lateinit var editor: EditText
        try {
            shell("ime set $ime")
            rule.setContent {
                AndroidView(factory = { ctx ->
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(80, 140, 80, 0)
                        editor = EditText(ctx).apply {
                            setText("Hardware cursor sample")
                            setSingleLine(true)
                        }
                        addView(editor, LinearLayout.LayoutParams(-1, 120))
                    }
                }, modifier = Modifier.fillMaxSize())
            }
            rule.runOnUiThread {
                editor.requestFocus()
                editor.setSelection(2)
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                editor.post {
                    imm.restartInput(editor)
                    imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
                }
            }
            rule.waitUntil(30_000) { rule.onAllNodesWithTag("hardware-keyboard-toolbar").fetchSemanticsNodes().isNotEmpty() }
            val engine = RimeEngine.getInstance()
            rule.waitUntil(30_000) { RimeEngine.isInitialized() && engine.getCurrentSchema().isNotEmpty() }
            shell("input keyboard keyevent KEYCODE_N KEYCODE_I")
            rule.waitUntil(30_000) { rule.onAllNodesWithTag("hardware-candidate-card", true).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle()
            fun card() = rule.onNodeWithTag("hardware-candidate-card", true).fetchSemanticsNode().positionOnScreen
            val first = card()
            val location = IntArray(2)
            rule.runOnUiThread { editor.getLocationOnScreen(location) }
            assertTrue("Candidate must be near the editor, not bottom-center: $first", first.y < location[1] + editor.height + 64 * density)
            rule.runOnUiThread { editor.translationY += 160f }
            rule.waitUntil(10_000) { kotlin.math.abs(card().y - first.y - 160f) < 4f }
            val moved = card()
            rule.runOnUiThread { editor.setSelection(18) }
            rule.waitUntil(10_000) { card().x > moved.x + 40f }
            fun document(): String {
                var text = ""
                rule.runOnUiThread { text = editor.text.toString() }
                return text
            }
            fun candidates() = engine.getCandidates().toList()
            val composing = engine.getInput()
            val originalText = document()
            assertTrue("Paging fixture needs an active composition", composing.isNotEmpty())
            val firstPage = candidates()
            assertTrue("Paging fixture needs multiple pages", engine.hasNextPage())
            for ((next, previous) in listOf(
                "keyevent KEYCODE_EQUALS" to "keyevent KEYCODE_MINUS",
                "keyevent KEYCODE_RIGHT_BRACKET" to "keyevent KEYCODE_LEFT_BRACKET",
                "keyevent KEYCODE_PERIOD" to "keyevent KEYCODE_COMMA",
                "keycombination KEYCODE_SHIFT_LEFT KEYCODE_PERIOD" to "keycombination KEYCODE_SHIFT_LEFT KEYCODE_COMMA",
            )) {
                shell("input keyboard $next")
                rule.waitUntil(10_000) { candidates() != firstPage && engine.hasPrevPage() }
                assertEquals(composing, engine.getInput())
                shell("input keyboard $previous")
                rule.waitUntil(10_000) { candidates() == firstPage && !engine.hasPrevPage() }
                assertEquals(originalText, document())
            }
            // First-page boundaries still consume every previous-page key.
            shell("input keyboard keyevent KEYCODE_MINUS KEYCODE_LEFT_BRACKET KEYCODE_COMMA")
            android.os.SystemClock.sleep(200)
            assertEquals(composing, engine.getInput())
            assertEquals(firstPage, candidates())
            assertEquals(originalText, document())
            repeat(composing.length) { shell("input keyboard keyevent KEYCODE_DEL") }
            rule.waitUntil(10_000) { engine.compositionActiveForDeletion() == false }
            rule.runOnUiThread { editor.setText(""); editor.setSelection(0) }
            shell("input keyboard keyevent KEYCODE_N KEYCODE_I KEYCODE_ENTER")
            rule.waitUntil(10_000) { engine.compositionActiveForDeletion() == false && document() == "ni" }
            assertEquals("ni", document())
            // Only an empty composition permits literal punctuation, including shifted < and >.
            for (key in listOf("KEYCODE_MINUS", "KEYCODE_EQUALS", "KEYCODE_LEFT_BRACKET", "KEYCODE_RIGHT_BRACKET", "KEYCODE_COMMA", "KEYCODE_PERIOD")) {
                val before = document()
                shell("input keyboard keyevent $key")
                rule.waitUntil(10_000) { document() != before }
            }
            for (key in listOf("KEYCODE_COMMA", "KEYCODE_PERIOD")) {
                val before = document()
                shell("input keyboard keycombination KEYCODE_SHIFT_LEFT $key")
                rule.waitUntil(10_000) { document() != before }
            }
            rule.onNodeWithTag("hardware-toolbar-keyboard").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("hardware-keyboard-toolbar").fetchSemanticsNodes().isEmpty() }
        } finally {
            shell("input keyevent KEYCODE_ESCAPE")
            if (!oldIme.isNullOrBlank()) shell("ime set $oldIme")
        }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
