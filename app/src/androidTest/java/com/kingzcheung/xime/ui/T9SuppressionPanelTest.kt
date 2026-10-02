package com.kingzcheung.xime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.ui.settings.T9SuppressionPanel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class T9SuppressionPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun restoringVisibleFeedbackReenablesTheNativeCandidate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val engine = RimeEngine.getInstance()
        val schema = engine.getCurrentSchema()
        val ascii = engine.isAsciiMode()
        var suppressed: String? = null
        fun input() {
            engine.clearQueuedT9Composition()
            "6364986743663".forEach { engine.processQueuedT9Key(it.code) }
        }
        try {
            assertTrue(engine.switchSchema("t9_pinyin"))
            engine.setOption("ascii_mode", false)
            input()
            val state = engine.readQueuedComposition()!!
            val word = engine.getAllCandidates(1).first().text
            assertTrue(engine.suppressCandidateAtRevision(0, state.engineRevision, global = true))
            suppressed = word
            rule.setContent { MaterialTheme { Surface { T9SuppressionPanel() } } }
            rule.waitUntil(10_000) {
                rule.onAllNodes(androidx.compose.ui.test.hasText(word)).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(word).assertIsDisplayed()
            rule.onNodeWithText("恢复推荐").assertIsDisplayed()
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "t9-feedback-panel.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            rule.onNodeWithText("恢复推荐").performClick()
            rule.waitUntil(10_000) { word !in engine.getSuppressedCandidates() }
            rule.onNodeWithText("暂无屏蔽的候选").assertIsDisplayed()
            input()
            assertTrue(engine.getAllCandidates(100).any { it.text == word })
        } finally {
            suppressed?.let { engine.restoreSuppressedCandidate(it) }
            engine.clearQueuedT9Composition()
            engine.switchSchema(schema)
            engine.setOption("ascii_mode", ascii)
        }
    }
}
