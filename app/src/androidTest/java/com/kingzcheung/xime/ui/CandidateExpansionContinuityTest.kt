package com.kingzcheung.xime.ui

import com.kingzcheung.xime.service.hasSameSelectionSource

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CandidateExpansionContinuityTest {
    @get:Rule val rule = createComposeRule()
    private val entries = List(100) { CandidateEntry("候选$it", globalIndex = it + 10) }
    private val bg = Color(0xFF211D29)

    @Test fun keyboardViewExpansionKeepsHeaderPrefixAndRoutesGlobalSelection() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        KeysConfigHelper.loadConfig(app)
        val vm = com.kingzcheung.xime.viewmodel.KeyboardViewModel(app)
        vm.setKeyboardState(KeyboardLayoutState.T9Pinyin)
        val words = List(60) { "词条$it" }
        val candidates = mutableStateOf(com.kingzcheung.xime.service.CandidateState(
            candidates = words.take(15), inputText = "746", preeditText = "pin", isComposing = true,
            expandedCandidatesLoaded = true,
            expandedCandidates = words.map { com.kingzcheung.xime.rime.RimeCandidate(it, "") },
        ))
        var selected = -1
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardView(vm, KeyboardUiState(currentSchemaId = "t9_pinyin"),
                        KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            isCandidateSnapshotCurrent = { it.hasSameSelectionSource(candidates.value) },
                            onGlobalCandidateSelect = { selected = it }),
                        modifier = Modifier.size(360.dp, 240.dp).testTag("candidate-test-keyboard"), candidateState = candidates)
                }
            }
        }
        val delete = rule.onNodeWithTag("t9-delete-key").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { vm.setCandidatePageExpanded(true) }
        rule.onNodeWithTag("expanded-candidates").assertIsDisplayed()
        // The restored page uses the entire keyboard body, including the former bottom row.
        val list = rule.onNodeWithTag("expanded-candidates").fetchSemanticsNode().boundsInRoot
        assertTrue("Expansion must include the former bottom row", list.height > delete.height * 3.5f)
        rule.onNodeWithContentDescription("回车").assertIsDisplayed()
        rule.runOnIdle { vm.toggleSingleCharFilter() }
        rule.onAllNodes(SemanticsMatcher("header candidates") {
            (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) ?: "").startsWith("bar-candidate:")
        }).assertCountEquals(0)
        rule.onNodeWithTag("expanded-candidate:0").assertDoesNotExist()
        rule.runOnIdle { vm.toggleSingleCharFilter() }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "candidate-full-panel.png").outputStream().use {
            rule.onNodeWithTag("candidate-test-keyboard").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val bars = rule.onAllNodes(SemanticsMatcher("visible header") {
            (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) ?: "").startsWith("bar-candidate:")
        }).fetchSemanticsNodes()
        assertTrue(bars.isNotEmpty())
        val prefixCount = bars.size
        for (i in 0 until prefixCount) rule.onNodeWithTag("expanded-candidate:$i").assertDoesNotExist()
        rule.onNodeWithTag("expanded-candidate:$prefixCount").performTouchInput { down(center) }
        rule.runOnIdle {
            candidates.value = candidates.value.copy(
                associationCandidates = listOf("无关联想刷新"),
                expandedCandidates = candidates.value.expandedCandidates + com.kingzcheung.xime.rime.RimeCandidate("补页候选", ""),
            )
        }
        rule.onNodeWithTag("expanded-candidate:$prefixCount").performTouchInput { up() }
        rule.runOnIdle { assertEquals(prefixCount, selected); assertFalse(vm.candidatePageExpanded.value) }
        rule.onNodeWithTag("expanded-candidates").assertDoesNotExist()
    }

    @Test fun genericCandidateRailKeepsEnterWithinShortPortraitAndLandscapePanel() {
        var landscape by mutableStateOf(false)
        var entered = 0
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f)) {
                CandidatePage(CandidatePageState(entries, backgroundColor = bg, textColor = Color.White),
                    CandidatePageCallbacks(onCandidateSelect = {}, onDelete = {}, onEnter = { entered++ }),
                    Modifier.size(360.dp, 144.dp).testTag("short-panel"))
            }
        }
        for (wide in listOf(false, true)) {
            rule.runOnIdle { landscape = wide }
            val body = rule.onNodeWithTag("short-panel").fetchSemanticsNode().boundsInRoot
            val enter = rule.onNodeWithContentDescription("回车").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(enter.bottom <= body.bottom)
            rule.onNodeWithContentDescription("回车").performTouchInput { click() }
        }
        rule.runOnIdle { assertEquals(2, entered) }
    }

    @Test fun downwardRevealUsesStableBoundsAndReversesWithoutClickingUnderlyingKeys() {
        var visible by mutableStateOf(false)
        var underlyingClicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(200.dp).testTag("fixed-slot").background(Color.Blue).clickable { underlyingClicks++ }) {
                    KeyboardPanelReveal(visible, Modifier.matchParentSize().testTag("reveal")) {
                        Box(Modifier.fillMaxSize().background(Color.Red))
                    }
                }
            }
        }
        val bounds = rule.onNodeWithTag("fixed-slot").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { visible = true }
        rule.mainClock.advanceTimeBy(100)
        val pixels = rule.onNodeWithTag("fixed-slot").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[100, 5])
        assertEquals(Color.Blue, pixels[100, 195])
        assertEquals(bounds, rule.onNodeWithTag("fixed-slot").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("fixed-slot").performTouchInput { click(bottomCenter - androidx.compose.ui.geometry.Offset(0f, 5f)) }
        rule.runOnIdle { assertEquals(0, underlyingClicks); visible = false }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { visible = true }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(Color.Red, rule.onNodeWithTag("fixed-slot").captureToImage().toPixelMap()[100,195])
        rule.runOnIdle { visible = false }
        rule.mainClock.advanceTimeBy(250)
        rule.onNodeWithTag("reveal", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(bounds, rule.onNodeWithTag("fixed-slot").fetchSemanticsNode().boundsInRoot)
    }
}
