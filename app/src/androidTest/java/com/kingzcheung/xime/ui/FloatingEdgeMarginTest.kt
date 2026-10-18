package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tablet floating window at its smallest and a large width: the toolbar's first button and the
 * first key column keep a margin from the card edge, and the toolbar stays inside the card.
 */
class FloatingEdgeMarginTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val width = mutableStateOf(400)

    @Test fun smallAndLargeFloatingWindowsKeepEdgeMargins() {
        KeysConfigHelper.loadConfig(app)
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 1280; screenHeightDp = 800; orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalConfiguration provides configuration) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF6B7A8F))) {
                        val vm = remember { KeyboardViewModel(app) }
                        KeyboardView(vm, KeyboardUiState(currentSchemaId = "rime_ice", isDarkTheme = true,
                            isFloatingMode = true, floatingWidthDp = width.value, keyboardWidthDp = width.value,
                            keyboardHeightDp = 300, floatingScreenHeightDp = 800),
                            KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                                onHideKeyboard = {}, onUpdateToolbarButtons = {}),
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        val report = StringBuilder()
        for (w in listOf(400, 900)) {
            rule.runOnIdle { width.value = w }
            rule.waitForIdle()
            val card = rule.onNodeWithTag("floating-keyboard-card").fetchSemanticsNode().boundsInRoot
            val leading = rule.onNodeWithTag("toolbar-leading", true).fetchSemanticsNode().boundsInRoot
            val hide = rule.onNodeWithTag("toolbar-hide", true).fetchSemanticsNode().boundsInRoot
            val q = rule.onAllNodesWithText("q", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
            val p = rule.onAllNodesWithText("p", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
            report.append("w=$w card=${card.left}..${card.right} leading=${leading.left}..${leading.right} " +
                "hide=${hide.left}..${hide.right} q=${q.left} p=${p.right}\n")
            save("floating-$w")
            assertTrue("toolbar stays inside the card at $w: $report", hide.right <= card.right + 0.5f && leading.left >= card.left - 0.5f)
        }
        File(app.getExternalFilesDir(null), "floating-edges").apply { mkdirs() }.resolve("report.txt").writeText(report.toString())
    }

    private fun save(name: String) {
        Thread.sleep(300)
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "floating-edges").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
