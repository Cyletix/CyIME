package com.kingzcheung.xime.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.SettingsPreferences
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real IME: live code edits retain the original keyboard and committed host text. */
class PreeditEditorImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val engine = RimeEngine.getInstance()
    private lateinit var editor: EditText
    private val swipeDistance get() = 80f * context.resources.displayMetrics.density
    private val imeComponent get() = ComponentName(context, "${context.packageName}.service.XimeInputMethodService")
    private val imeId get() = imeComponent.flattenToShortString()
    private val inputMethodManager get() = context.getSystemService(InputMethodManager::class.java)
    private var previousIme: String? = null
    private var previouslyEnabled = false
    private var previousSubtype = -1
    private var savedSystemImeState = false
    private var oldInputLocation = ""

    @Before fun startKeyboard(): Unit = runBlocking {
        oldInputLocation = SettingsPreferences.getInputTextLocation(context)
        SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR)
        previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        previousSubtype = Settings.Secure.getInt(context.contentResolver, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE, -1)
        previouslyEnabled = inputMethodManager.enabledInputMethodList.any { it.id == imeId }
        savedSystemImeState = true
        // A service-owned ComposeView outlives a test's ComposeTestRule. Reusing it in the
        // next test would keep the previous rule's disposed recomposer and invisible test root.
        stopKeyboardService()
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context))
        assertTrue(engine.ensureSession())
        engine.clearQueuedComposition()
        shell("ime enable $imeId")
        shell("ime set $imeId")
        rule.setContent {
            AndroidView(factory = { EditText(it).also { field ->
                editor = field
                field.hint = "字面输入回归"
                field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } },
                modifier = Modifier.fillMaxWidth().height(120.dp))
        }
        rule.waitUntil(10_000) {
            var ready = false
            rule.runOnUiThread { ready = editor.isAttachedToWindow && editor.hasWindowFocus() }
            ready
        }
        rule.runOnUiThread {
            assertTrue("测试输入框必须取得焦点", editor.requestFocus())
            inputMethodManager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("language-key-control", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun stopKeyboardAndRestoreSystemIme() {
        if (!savedSystemImeState) return
        try {
            if (::editor.isInitialized) {
                rule.runOnUiThread {
                    inputMethodManager.hideSoftInputFromWindow(editor.windowToken, 0)
                    editor.clearFocus()
                }
            }
            stopKeyboardService()
        } finally {
            SettingsPreferences.setInputTextLocation(context, oldInputLocation)
            if (previouslyEnabled) shell("ime enable $imeId")
            previousIme?.takeIf { it.isNotBlank() }?.let { shell("ime set $it") }
            shell("settings put secure selected_input_method_subtype $previousSubtype")
        }
    }

    @Suppress("DEPRECATION") // Android still exposes this application's own running services.
    private fun stopKeyboardService() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val waitForEngineDestroy = RimeEngine.isInitialized() &&
            activityManager.getRunningServices(Int.MAX_VALUE).any { it.service == imeComponent }
        shell("ime disable $imeId")
        rule.waitUntil(10_000) {
            activityManager.getRunningServices(Int.MAX_VALUE).none { it.service == imeComponent } &&
                (!waitForEngineDestroy || !RimeEngine.isInitialized())
        }
        // Service removal and delivery of onDestroy happen on different threads. Drain Main
        // before initializing Rime again or allowing this test's recomposer to be disposed.
        instrumentation.waitForIdleSync()
    }

    private fun chooseMode(id: String) {
        val globe = rule.onAllNodesWithTag("language-key-control", useUnmergedTree = true).onLast()
        // The key can be composed before the async schema list reaches the service UI.
        // A change in hasMenu cancels a held pointerInput, so retry that cancelled hold.
        var menuReady = false
        repeat(2) {
            if (!menuReady) {
                globe.performTouchInput { down(center) }
                try {
                    rule.waitUntil(10_000) { rule.onAllNodesWithTag("language-schema:$id").fetchSemanticsNodes().isNotEmpty() }
                    menuReady = true
                } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                    screenshot("mode-$id-menu-not-ready")
                    val tags = rule.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.TestTag), useUnmergedTree = true)
                        .fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
                    File(context.getExternalFilesDir(null), "mode-menu-tags.txt").writeText(tags.joinToString("\n"))
                    globe.performTouchInput { cancel() }
                }
            }
        }
        assertTrue("language menu did not expose $id", menuReady)
        val choice = rule.onNodeWithTag("language-schema:$id").performScrollTo().fetchSemanticsNode()
        val key = globe.fetchSemanticsNode()
        globe.performTouchInput {
            moveTo(choice.positionOnScreen + Offset(choice.size.width / 2f, choice.size.height / 2f) - key.positionOnScreen)
            up()
        }
        rule.waitUntil(10_000) {
            if (id == InputModes.ENGLISH) engine.isAsciiMode()
            else engine.getCurrentSchema() == id && !engine.isAsciiMode()
        }
        rule.waitForIdle()
    }

    private fun tap(label: String) {
        rule.onNodeWithText(label, ignoreCase = true).performTouchInput { down(center); up() }
    }

    private fun swipeUp(label: String) {
        val distance = swipeDistance
        rule.onNodeWithText(label, ignoreCase = true).performTouchInput {
            down(center)
            moveTo(center - Offset(0f, distance), delayMillis = 120)
            up()
        }
    }

    private fun text(): String {
        var value = ""
        rule.runOnUiThread { value = editor.text.toString() }
        return value
    }

    private fun assertSettled(expected: String) {
        rule.waitUntil(5000) { text() == expected && engine.getInput().isEmpty() }
        android.os.SystemClock.sleep(350)
        rule.waitForIdle()
        assertEquals(expected, text())
        assertEquals("字面上屏后不能留旧编码", "", engine.getInput())
    }


    private fun openEditor() {
        rule.waitUntil(5000) { rule.onAllNodesWithTag("candidate-preedit").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("candidate-preedit").performTouchInput { click() }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("preedit-editor-code").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { assertTrue("点击预编辑浮层不能抢走宿主焦点", editor.hasFocus()) }
    }
    private fun draft(value: String, caret: Int = value.length) {
        rule.onNodeWithTag("preedit-editor-code").performSemanticsAction(SemanticsActions.SetText) { assertTrue(it(AnnotatedString(value))) }
        rule.onNodeWithTag("preedit-editor-code").performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(caret, caret, false)) }
    }
    private fun applyDraft() {
        rule.onNodeWithTag("preedit-editor-close").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("preedit-editor").fetchSemanticsNodes().isEmpty() }
    }
    private fun selectExpandedCandidate(value: String) {
        val candidates = engine.getAllCandidates().toList()
        val candidate = candidates.firstOrNull { it.text == value }
        assertNotNull("Rime 全量候选缺少 $value，input=${engine.getInput()} candidates=${candidates.take(30)}", candidate)
        // Expanded entries render the word and its annotation in one styled Text node.
        val comment = candidate!!.comment.replace("~", "")
        val displayText = value + if (comment.isNotEmpty()) " $comment" else ""
        rule.onNodeWithTag("candidate-expansion").performTouchInput { click() }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("expanded-candidates").fetchSemanticsNodes().isNotEmpty() }
        try {
            rule.onNodeWithTag("expanded-candidates").performScrollToNode(hasText(displayText, substring = false))
            rule.onNode(hasText(displayText, substring = false) and hasAnyAncestor(hasTestTag("expanded-candidates")))
                .performTouchInput { click() }
        } catch (failure: Throwable) {
            screenshot("candidate-$value-failure")
            val dir = File(context.getExternalFilesDir(null), "preedit-editor").apply { mkdirs() }
            File(dir, "candidate-$value-failure.txt").writeText(
                "expected=$displayText input=${engine.getInput()} candidates=$candidates\n" +
                    rule.onNodeWithTag("expanded-candidates", useUnmergedTree = true).printToString())
            throw failure
        }
    }
    private fun setPrefix() = rule.runOnUiThread { editor.setText("前文"); editor.setSelection(editor.length()) }
    private fun screenshot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "preedit-editor").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().also { image ->
            File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }

    @Test fun liveEditorStaysAboveCandidatesAndUsesOriginalKeysWithoutChangingHostText() {
        chooseMode("rime_ice"); setPrefix()
        "nihao".forEach { tap(it.toString()) }
        rule.waitUntil(5000) { engine.getInput() == "nihao" }
        // Editor restarts reapply this setting. Idempotent updates must retain composition.
        repeat(12) { engine.setPageSize("rime_ice", SettingsPreferences.getPageSize(context)) }
        assertEquals("nihao", engine.getInput())
        val qBefore = rule.onNodeWithText("q", ignoreCase = true).fetchSemanticsNode()
        val qPosition = qBefore.positionOnScreen
        val qSize = qBefore.size
        openEditor()
        rule.onNodeWithText("ni'hao", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("candidate-preedit").assertDoesNotExist()
        val bar = rule.onNodeWithTag("preedit-editor").fetchSemanticsNode()
        val candidate = rule.onNodeWithTag("candidate-expansion").fetchSemanticsNode()
        assertTrue("editor must be above the candidate row", bar.positionOnScreen.y + bar.size.height <= candidate.positionOnScreen.y + 2)
        val qAfter = rule.onNodeWithText("q", ignoreCase = true).fetchSemanticsNode()
        assertEquals(qPosition, qAfter.positionOnScreen); assertEquals(qSize, qAfter.size)
        rule.onNodeWithTag("preedit-apply").assertDoesNotExist()
        draft("nihao", 2)
        tap("m")
        rule.waitUntil(5000) { engine.getInput() == "nimhao" }
        assertEquals("前文", text())
        screenshot("pinyin-editor-above-original-keyboard")
        applyDraft()
        assertEquals("nimhao", engine.getInput())
        openEditor(); draft("womf")
        rule.waitUntil(5000) { engine.getInput() == "womf" }
        shell("input keyevent 4")
        rule.waitUntil(5000) { rule.onAllNodesWithTag("preedit-editor").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("language-key-control", useUnmergedTree = true).assertExists()
        assertEquals("womf", engine.getInput()); assertEquals("前文", text())
        rule.runOnUiThread { assertTrue(editor.hasFocus()) }
        engine.clearQueuedComposition()
    }

    @Test fun mergedAndDoublePinyinEditsReturnToTheirOwnLayout() {
        chooseMode("pinyin_14jian"); setPrefix()
        listOf("bn", "ui", "gh", "as", "op").forEach(::tap)
        rule.waitUntil(5000) { engine.getInput() == "bugao" }
        openEditor(); draft("nihao"); applyDraft()
        rule.waitUntil(5000) { engine.getInput() == "nihao" }
        assertEquals("nihao", engine.getInput()); assertEquals("前文", text())
        rule.onNodeWithText("bn", ignoreCase = true).assertExists()
        rule.onNodeWithText("你好").performClick()
        rule.waitUntil(5000) { text() == "前文你好" }
        chooseMode("double_pinyin_flypy")
        "nihk".forEach { tap(it.toString()) }
        rule.waitUntil(5000) { engine.getInput() == "nihk" }
        openEditor(); draft("womf"); applyDraft()
        assertEquals("double_pinyin_flypy", engine.getCurrentSchema())
        rule.waitUntil(5000) { engine.getInput() == "womf" }
        assertEquals("womf", engine.getInput()); assertEquals("前文你好", text())
        engine.clearQueuedComposition()
    }

    @Test fun t9EditsRebuildTheBufferAndKeepPreviouslySelectedText() {
        chooseMode("t9_pinyin"); setPrefix()
        listOf("MNO", "GHI", "GHI", "ABC", "MNO").forEach(::tap)
        rule.waitUntil(5000) { engine.getInput().isNotEmpty() && engine.getCandidates().contains("你好") }
        openEditor(); draft("ni'hao", 3)
        tap("MNO")
        rule.waitUntil(5000) { engine.getInput().contains("6") }
        rule.onNodeWithTag("t9-delete-key").performTouchInput { down(center); up() }
        rule.waitUntil(5000) { engine.getInput().contains("hao") && !engine.getInput().contains("6") }
        rule.waitUntil(5000) { rule.onNodeWithTag("preedit-editor-code").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == "ni'hao" }
        rule.waitForIdle()
        screenshot("t9-live-editor-original-keys")
        applyDraft()
        rule.waitUntil(5000) { engine.getInput().contains("hao") }
        val edited = engine.getInput()
        assertTrue(edited.contains("ni")); assertTrue(edited.contains("hao")); assertEquals("前文", text())
        // Select only the first word, leaving a protected nine-key partial commit.
        selectExpandedCandidate("你")
        rule.waitUntil(5000) { engine.getInput().isNotEmpty() && engine.getCandidates().isNotEmpty() }
        openEditor()
        draft("hao"); applyDraft()
        assertEquals("前文", text())
        screenshot("t9-edited-partial")
        tap("MNO")
        rule.waitUntil(5000) { engine.getInput().contains("hao") && engine.getInput().endsWith("6") }
        // Delete the new key through the original nine-key control.
        rule.onNodeWithTag("t9-delete-key").performTouchInput { down(center); up() }
        rule.waitUntil(5000) { engine.getInput().contains("hao") && !engine.getInput().endsWith("6") }
        selectExpandedCandidate("好")
        rule.waitUntil(5000) { text() == "前文你好" }
        assertEquals("", engine.getInput())
    }

    @Test fun japanesePreeditHasNoChineseEditingEntry() {
        chooseMode("japanese")
        "kana".forEach { tap(it.toString()) }
        rule.waitUntil(5000) { engine.getInput().isNotEmpty() }
        // Its display can remain visible, but the Chinese editor action must be absent.
        rule.onAllNodesWithTag("candidate-preedit").fetchSemanticsNodes().forEach { node ->
            assertFalse(node.config.contains(SemanticsActions.OnClick))
        }
        rule.onNodeWithTag("preedit-editor").assertDoesNotExist()
        engine.clearQueuedComposition()
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
