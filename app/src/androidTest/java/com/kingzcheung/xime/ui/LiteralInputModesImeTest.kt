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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real input connection + complete gestures across the shared literal-input route. */
class LiteralInputModesImeTest {
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

    @Before fun startKeyboard(): Unit = runBlocking {
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
        globe.performTouchInput { down(center) }
        rule.waitUntil(3000) { rule.onAllNodesWithTag("language-schema:$id").fetchSemanticsNodes().isNotEmpty() }
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

    @Test fun englishTypedWordThenSwipeDigitDoesNotRepeatTheWord() {
        chooseMode(InputModes.ENGLISH)
        listOf("h", "i").forEach(::tap)
        rule.waitUntil(5000) { text().equals("hi", ignoreCase = true) }
        val word = text()
        swipeUp("q")
        assertSettled(word + "1")
        tap("w")
        rule.waitUntil(5000) { text().equals(word + "1w", ignoreCase = true) }
        assertEquals("", engine.getInput())
    }

    @Test fun japaneseUppercaseKatakanaSurvivesFollowingSwipeDigit() {
        chooseMode("japanese")
        rule.onNodeWithText("q", ignoreCase = false).assertExists()
        rule.onNodeWithTag("shift-key").performTouchInput { down(center); up() }
        "KATA".forEach { tap(it.toString()) }
        rule.waitUntil(10_000) { engine.getComposition().preedit.replace(" ", "") == "カタ" }
        swipeUp("Q")
        assertSettled("カタ1")
        tap("K"); tap("A")
        rule.waitUntil(5000) { engine.getComposition().preedit.replace(" ", "") == "カ" }
        assertFalse("上滑不能混入 q 编码", engine.getInput().contains("q", ignoreCase = true))
        engine.clearQueuedComposition()
    }

    @Test fun t9CandidateThenDigitThenImmediateNextKeyKeepsOnlyNewCode() {
        chooseMode("t9_pinyin")
        tap("ABC")
        rule.waitUntil(5000) { engine.getInput().isNotEmpty() && engine.getCandidates().isNotEmpty() }
        val previousCandidate = engine.getCandidates().first()
        swipeUp("DEF")
        assertSettled(previousCandidate + "3")
        tap("GHI")
        rule.waitUntil(5000) { engine.getInput() == "4" }
        val secondCandidate = engine.getCandidates().first()
        // Send the next touch immediately, before waiting for Compose/service work to settle.
        val digitNode = rule.onNodeWithText("ABC")
        val digitBounds = digitNode.fetchSemanticsNode().boundsInRoot
        val digit = digitBounds.center - digitBounds.topLeft
        val next = rule.onNodeWithText("JKL").fetchSemanticsNode().boundsInRoot.center - digitBounds.topLeft
        val distance = swipeDistance
        digitNode.performTouchInput {
            down(digit)
            moveTo(digit - Offset(0f, distance), delayMillis = 120)
            up()
            advanceEventTime(1)
            down(next)
            up()
        }
        rule.waitUntil(5000) { engine.getInput() == "5" }
        android.os.SystemClock.sleep(350)
        rule.waitForIdle()
        assertEquals("延迟的清空不能删除新九键输入", "5", engine.getInput())
        assertEquals(previousCandidate + "3" + secondCandidate + "2", text())
        engine.clearQueuedComposition()
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
