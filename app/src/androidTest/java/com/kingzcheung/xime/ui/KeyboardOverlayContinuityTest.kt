package com.kingzcheung.xime.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Regression coverage uses the production keyboard and its actual panel transition host. */
class KeyboardOverlayContinuityTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val preferenceFiles = mutableSetOf<String>()
    private val namespace = "overlay-continuity-${System.nanoTime()}"
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = "$namespace-$name"
            preferenceFiles += file
            return app.getSharedPreferences(file, mode)
        }
    }
    private lateinit var vm: KeyboardViewModel
    private val modeReports = mutableListOf<Boolean>()
    private val presses = mutableListOf<String>()
    private val candidates = mutableStateOf(CandidateState(associationCandidates = listOf("保留联想")))

    @After fun releaseFixture() {
        rule.mainClock.autoAdvance = true
        store.clear()
        preferenceFiles.forEach { app.deleteSharedPreferences(it) }
        KeysConfigHelper.setActiveKeyboardSchema(SettingsPreferences.getCurrentSchema(app))
    }

    @Test fun fullKeyboardStaysBehindEveryToolAndChineseModeDoesNotTurnOff() {
        mount("rime_ice")
        verifyOpeningAndClosing(
            keyTag = "qwerty-enter-key", expectedMode = true,
            routes = listOf(OverlayRoute.Menu, OverlayRoute.SchemaList, OverlayRoute.Emoji, OverlayRoute.Clipboard(0)),
        )
    }

    @Test fun clipboardKeepsOriginalToolbarSizeAndKeyboardGeometry() {
        mount("rime_ice")
        val enter = bounds("qwerty-enter-key")
        rule.runOnIdle { vm.showOverlay(OverlayRoute.Clipboard(0)) }
        rule.waitForIdle()
        val leading = bounds("toolbar-leading")
        val board = bounds("clipboard-board")
        assertEquals("clipboard keeps the original toolbar button height", 40f, leading.height, 1f)
        assertTrue("the board stays below the original 44dp toolbar", board.top - leading.top in 42f..45f)
        assertEquals("classification and actions use a single row", 24f, bounds("clipboard-controls").height, 1f)
        assertBoundsEqual(enter, bounds("qwerty-enter-key", unmerged = true))
        java.io.File(app.cacheDir, "clipboard-compact-keyboard.png").outputStream().use {
            assertTrue(screenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        rule.onNodeWithTag("toolbar-leading").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("clipboard-board").assertDoesNotExist()
        assertBoundsEqual(enter, bounds("qwerty-enter-key"))
        rule.runOnIdle {
            assertEquals("the back button emits its existing toolbar feedback", listOf("toolbar"), presses)
            presses.clear()
        }
        rule.onNodeWithTag("qwerty-enter-key").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("enter"), presses) }
    }

    @Test fun nineKeyGeometryAndInteractionSurviveMenuAndEmoji() {
        mount("t9_pinyin")
        verifyOpeningAndClosing("t9-zero-key", true, listOf(OverlayRoute.Menu, OverlayRoute.Emoji))
    }

    @Test fun clipboardKeepsItsToolbarEvenWhenEditorHasAutofillSuggestions() {
        // Only the nonempty-list admission matters here; native inflation is not part
        // of the tool panel and must never replace these actions.
        mount("rime_ice", inlineSuggestions = listOf(Any()))
        rule.runOnIdle { vm.showOverlay(OverlayRoute.Clipboard(0)) }
        rule.onNodeWithTag("clipboard-board").assertIsDisplayed()
        rule.onNodeWithContentDescription("剪贴板").assertIsDisplayed()
        rule.onNodeWithContentDescription("表情").assertIsDisplayed()
        rule.onNodeWithTag("toolbar-leading").assertIsDisplayed()
        assertEquals(40f, bounds("toolbar-leading").height, 1f)
    }

    @Test fun numberPanelRemainsTheSamePanelUnderTools() {
        mount("rime_ice")
        rule.runOnIdle { vm.enterPanel(PanelType.NUMBER) }
        verifyOpeningAndClosing("number-zero", false, listOf(OverlayRoute.SchemaList, OverlayRoute.Clipboard(0)))
        rule.runOnIdle { assertEquals(KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL), vm.page.value) }
    }

    @Test fun symbolsKeepTheNumberUnderlayUntilClosingAndThenReturnToText() {
        mount("rime_ice")
        rule.runOnIdle { vm.enterPanel(PanelType.NUMBER) }
        val zero = bounds("number-zero")
        val root = bounds("continuity-keyboard")
        val underlay = bounds("keyboard-underlay")
        val baseline = screenshot()
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { modeReports.clear(); presses.clear(); vm.showOverlay(OverlayRoute.Symbol) }
            rule.mainClock.advanceTimeBy(80)
            assertBoundsEqual(underlay, bounds("keyboard-underlay"))
            assertBoundsEqual(zero, bounds("number-zero", unmerged = true))
            assertRegionUnchanged(baseline, screenshot(), zero.translate(-root.left, -root.top),
                "Opening symbols must not swap the visible number underlay to a text keyboard")
            rule.onNodeWithTag("number-zero").assertDoesNotExist()
            rule.runOnIdle {
                assertTrue(modeReports.isNotEmpty())
                assertTrue("Opening symbols must keep the original number mode", modeReports.none { it })
            }
            rule.mainClock.advanceTimeBy(300)
            rule.runOnIdle { vm.closeOverlay() }
            rule.mainClock.advanceTimeBy(300)
            rule.onNodeWithTag("keyboard-overlay").assertDoesNotExist()
            rule.onNodeWithTag("number-zero").assertDoesNotExist()
            rule.onNodeWithTag("qwerty-enter-key").assertIsDisplayed().performTouchInput { click() }
            rule.runOnIdle {
                assertEquals(KeyboardPage.Main(MainType.FULL), vm.page.value)
                assertEquals(listOf("enter"), presses)
            }
        } finally { rule.mainClock.autoAdvance = true }
    }

    @Test fun leavingSymbolsForAnotherToolStillReturnsToTextWhenTheOverlaySessionCloses() {
        mount("rime_ice")
        rule.mainClock.autoAdvance = false
        try {
            for (next in listOf(OverlayRoute.Emoji, OverlayRoute.Menu)) {
                rule.runOnIdle { vm.enterPanel(PanelType.NUMBER) }
                rule.mainClock.advanceTimeBy(32)
                val zero = bounds("number-zero")
                val root = bounds("continuity-keyboard")
                val baseline = screenshot()
                rule.runOnIdle { modeReports.clear(); vm.showOverlay(OverlayRoute.Symbol) }
                rule.mainClock.advanceTimeBy(80)
                assertBoundsEqual(zero, bounds("number-zero", unmerged = true))
                assertRegionUnchanged(baseline, screenshot(), zero.translate(-root.left, -root.top),
                    "The number surface must remain visible during the first Symbol reveal")
                rule.mainClock.advanceTimeBy(300)
                rule.runOnIdle { vm.showOverlay(next) }
                rule.mainClock.advanceTimeBy(32)
                assertBoundsEqual(zero, bounds("number-zero", unmerged = true))
                rule.runOnIdle {
                    assertTrue("Replacing symbols with $next must not change the underlying mode", modeReports.none { it })
                    vm.closeOverlay()
                }
                rule.mainClock.advanceTimeBy(300)
                rule.onNodeWithTag("keyboard-overlay").assertDoesNotExist()
                rule.onNodeWithTag("number-zero").assertDoesNotExist()
                rule.onNodeWithTag("qwerty-enter-key").assertIsDisplayed()
                rule.runOnIdle { assertEquals(KeyboardPage.Main(MainType.FULL), vm.page.value) }
            }
        } finally { rule.mainClock.autoAdvance = true }
    }

    @Test fun fullscreenHandwritingKeepsCanvasAndSavedFooterBounds() {
        mount("rime_ice", handwriting = true)
        val canvas = bounds("handwriting-canvas")
        val footer = bounds("handwriting-controls")
        val bottom = bounds("handwriting-bottom-row")
        val root = bounds("continuity-keyboard")
        assertTrue("Fixture must exercise a narrowed, offset footer", footer.width < canvas.width)
        assertTrue(footer.center.x < canvas.center.x)
        val underlay = bounds("keyboard-underlay")
        val baseline = screenshot()
        rule.mainClock.autoAdvance = false
        try {
            for (route in listOf(OverlayRoute.Menu, OverlayRoute.SchemaList)) {
                rule.runOnIdle { modeReports.clear(); presses.clear(); vm.showOverlay(route) }
                rule.mainClock.advanceTimeBy(80)
                assertBoundsEqual(underlay, bounds("keyboard-underlay"))
                assertBoundsEqual(canvas, bounds("handwriting-canvas", unmerged = true))
                assertBoundsEqual(footer, bounds("handwriting-controls", unmerged = true))
                assertBoundsEqual(bottom, bounds("handwriting-bottom-row", unmerged = true))
                assertRegionUnchanged(baseline, screenshot(), bottom.translate(-root.left, -root.top),
                    "Opening $route must not move or remove the fullscreen handwriting footer")
                rule.onNodeWithTag("handwriting-key:enter").assertDoesNotExist()
                rule.runOnIdle {
                    assertTrue(modeReports.isNotEmpty())
                    assertTrue("A tool overlay must not turn off handwriting's Chinese mode", modeReports.all { it })
                    assertEquals(listOf("保留联想"), candidates.value.associationCandidates)
                    vm.closeOverlay()
                }
                rule.mainClock.advanceTimeBy(300)
                rule.onNodeWithTag("handwriting-canvas").assertIsDisplayed()
                assertBoundsEqual(canvas, bounds("handwriting-canvas"))
                assertBoundsEqual(footer, bounds("handwriting-controls"))
            }
            rule.onNodeWithTag("handwriting-key:enter").performTouchInput { click() }
            rule.runOnIdle { assertEquals(listOf("enter"), presses) }
        } finally { rule.mainClock.autoAdvance = true }
    }

    @Test fun changingToolsDoesNotRestartTheCurtainOrExposeTheKeys() {
        mount("rime_ice")
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { vm.showOverlay(OverlayRoute.Menu) }
            rule.mainClock.advanceTimeBy(350)
            val overlay = bounds("keyboard-overlay")
            rule.runOnIdle { vm.pushOverlay(OverlayRoute.SchemaList) }
            rule.mainClock.advanceTimeBy(32)
            val early = screenshot()
            assertBoundsEqual(overlay, bounds("keyboard-overlay"))
            rule.mainClock.advanceTimeBy(350)
            val complete = screenshot()
            val root = bounds("continuity-keyboard")
            val lowerPanel = Rect(overlay.left, overlay.bottom - 60f, overlay.right, overlay.bottom)
                .translate(-root.left, -root.top)
            assertRegionUnchanged(complete, early, lowerPanel,
                "A menu-to-scheme change must show the new panel fully, without revealing keyboard keys below")
            rule.runOnIdle { vm.closeOverlay() }
            rule.mainClock.advanceTimeBy(300)
            rule.onNodeWithTag("qwerty-key:a").assertIsDisplayed().performTouchInput { click() }
            rule.runOnIdle { assertEquals(listOf("a"), presses) }
        } finally { rule.mainClock.autoAdvance = true }
    }

    private fun mount(schemaId: String, handwriting: Boolean = false, inlineSuggestions: List<Any> = emptyList()) {
        KeysConfigHelper.loadConfig(app)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))
            .get(KeyboardViewModel::class.java)
        vm.resetKeyboard(false, schemaId)
        val state = KeyboardUiState(
            currentSchemaId = schemaId, isDarkTheme = true,
            schemas = listOf("rime_ice", "t9_pinyin", "pinyin_14jian").map { SchemaInfo(it, it, "", "", "") },
            handwritingExpanded = handwriting, handwritingControlsWidthDp = if (handwriting) 260 else 0,
            handwritingControlsOffsetX = if (handwriting) -30 else 0,
        )
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 360
                screenHeightDp = 640
            }
            CompositionLocalProvider(
                LocalContext provides context, LocalDensity provides Density(1f),
                LocalConfiguration provides configuration,
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, if (handwriting) 480.dp else 320.dp).background(Color.White)) {
                        KeyboardView(vm, state,
                            KeyboardCallbacks(
                                onKeyPress = { _, _ -> }, onCandidateSelect = {},
                                onKeyPressDown = { presses += it }, onCommitText = {},
                                onKeyboardModeChange = { active ->
                                    modeReports += active
                                    // Same clearing condition as the production service callback.
                                    if (!active && candidates.value.associationCandidates.isNotEmpty()) {
                                        candidates.value = candidates.value.copy(associationCandidates = emptyList())
                                    }
                                },
                            ),
                            modifier = Modifier.requiredSize(360.dp, if (handwriting) 480.dp else 320.dp)
                                .testTag("continuity-keyboard"),
                            candidateState = candidates, inputSessionManagedByHost = true,
                            inlineSuggestions = inlineSuggestions,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        if (handwriting) rule.runOnIdle { vm.switchMain(MainType.HANDWRITING) }
        rule.waitForIdle()
    }

    private fun verifyOpeningAndClosing(keyTag: String, expectedMode: Boolean, routes: List<OverlayRoute>) {
        val key = bounds(keyTag)
        val root = bounds("continuity-keyboard")
        val underlay = bounds("keyboard-underlay")
        val originalPage = vm.page.value
        val baseline = screenshot()
        rule.mainClock.autoAdvance = false
        try {
            for (route in routes) {
                rule.runOnIdle { modeReports.clear(); presses.clear(); vm.showOverlay(route) }
                rule.mainClock.advanceTimeBy(80)
                assertBoundsEqual(underlay, bounds("keyboard-underlay"))
                assertBoundsEqual(key, bounds(keyTag, unmerged = true))
                assertRegionUnchanged(baseline, screenshot(), key.translate(-root.left, -root.top),
                    "The original $keyTag must remain in place beneath the opening $route")
                rule.onNodeWithTag(keyTag).assertDoesNotExist()
                rule.onNodeWithTag("continuity-keyboard").performTouchInput {
                    click(Offset(key.center.x - root.left, key.center.y - root.top))
                }
                rule.runOnIdle {
                    assertTrue("The unrevealed part of an opening menu must block underlying keys", presses.isEmpty())
                    assertTrue(modeReports.isNotEmpty())
                    assertTrue("Opening $route changed the underlying input mode", modeReports.all { it == expectedMode })
                    if (expectedMode) assertEquals(listOf("保留联想"), candidates.value.associationCandidates)
                    vm.closeOverlay()
                }
                rule.mainClock.advanceTimeBy(32)
                rule.onNodeWithTag(keyTag).assertDoesNotExist()
                rule.onNodeWithTag("continuity-keyboard").performTouchInput {
                    click(Offset(key.center.x - root.left, key.center.y - root.top))
                }
                rule.runOnIdle { assertTrue("Closing animation must still block the keyboard", presses.isEmpty()) }
                rule.mainClock.advanceTimeBy(300)
                rule.onNodeWithTag("keyboard-overlay").assertDoesNotExist()
                rule.onNodeWithTag(keyTag).assertIsDisplayed()
                assertBoundsEqual(key, bounds(keyTag))
                rule.runOnIdle { assertEquals(originalPage, vm.page.value) }
            }
            rule.onNodeWithTag(keyTag).performTouchInput { click() }
            rule.runOnIdle { assertEquals("A restored key must respond exactly once", 1, presses.size) }
        } finally { rule.mainClock.autoAdvance = true }
    }

    private fun bounds(tag: String, unmerged: Boolean = false): Rect =
        rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    private fun screenshot(): Bitmap = rule.onNodeWithTag("continuity-keyboard").captureToImage().asAndroidBitmap()

    private fun assertBoundsEqual(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }

    private fun assertRegionUnchanged(expected: Bitmap, actual: Bitmap, region: Rect, message: String) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val left = ceil(region.left + 3).toInt().coerceIn(0, expected.width - 1)
        val top = ceil(region.top + 3).toInt().coerceIn(0, expected.height - 1)
        val right = floor(region.right - 3).toInt().coerceIn(left + 1, expected.width)
        val bottom = floor(region.bottom - 3).toInt().coerceIn(top + 1, expected.height)
        var changed = 0
        for (y in top until bottom) for (x in left until right) {
            val a = expected.getPixel(x, y)
            val b = actual.getPixel(x, y)
            if (abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) > 2 ||
                abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) > 2 ||
                abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) > 2) changed++
        }
        assertEquals("$message ($changed pixels changed)", 0, changed)
    }
}
