package com.kingzcheung.xime.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.*
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Production KeyboardView + real touch coordinates, not a recreated navigation mock. */
class ModeNavigationTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("mode_navigation_test_$name", mode)
    }
    private lateinit var vm: KeyboardViewModel
    private val keys = mutableListOf<String>()
    private val commits = mutableListOf<String>()

    @Before fun prepare() {
        SettingsPreferences.getPrefsPublic(context).edit().clear().commit()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.reload(app)
    }
    @After fun cleanup() { SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }

    private fun mount(schema: String, english: Boolean = false, width: Int = 400, split: Boolean = false, floating: Boolean = false) {
        SettingsPreferences.setSplitKeyboardEnabled(context, split)
        KeysConfigHelper.setActiveKeyboardSchema(schema)
        vm = KeyboardViewModel(app)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = width; screenHeightDp = if (width > 600) 700 else 850
                orientation = if (width > 600) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            var ui by remember { mutableStateOf(KeyboardUiState(currentSchemaId = schema, isAsciiMode = english,
                isDarkTheme = true, themeId = "soft_blue", keyboardHeightDp = 330,
                keyboardBottomPaddingDp = 12, isFloatingMode = floating, floatingScreenHeightDp = 700)) }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context, LocalDensity provides Density(1f)) {
                XimeTheme(darkTheme = true, themeId = "soft_blue") {
                    Box(Modifier.size(width.dp, 360.dp).testTag("navigation-keyboard").background(KeyboardThemes.getKeyboardBackgroundColor("soft_blue", true))) {
                        KeyboardView(vm, ui, KeyboardCallbacks(onKeyPress = { key, _ ->
                            keys.add(key)
                            if (key == "ime_switch") ui = ui.copy(isAsciiMode = !ui.isAsciiMode)
                        }, onCandidateSelect = {}, onCommitText = { commits.add(it) }), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(slot: Int): Rect = rule.onNodeWithTag("mode-slot-$slot", true).fetchSemanticsNode().boundsInRoot
    private fun same(expected: Rect, actual: Rect) {
        assertEquals("slot left", expected.left, actual.left, 1.5f)
        assertEquals("slot top", expected.top, actual.top, 1.5f)
        assertEquals("slot right", expected.right, actual.right, 1.5f)
        assertEquals("slot bottom", expected.bottom, actual.bottom, 1.5f)
    }
    private fun tapAt(rect: Rect) {
        val root = rule.onNodeWithTag("navigation-keyboard", true).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("navigation-keyboard", true).performTouchInput { click(rect.center - root.topLeft) }
        rule.waitForIdle()
    }
    private fun label(slot: Int, expected: String) {
        rule.onNode(hasTestTag("mode-slot-$slot") and hasAnyDescendant(hasText(expected)), true).assertExists()
    }
    private fun verifyTriangle(label: String, screenshot: String) {
        val original = vm.keyboardState.value
        val first = bounds(1); val second = bounds(2)
        label(1, "!@#"); label(2, "123")
        tapAt(first) // text -> symbols
        label(1, label); label(2, "123")
        same(first, bounds(1)); same(second, bounds(2))
        rule.onNodeWithText("暂无最近使用").assertDoesNotExist()
        save("$screenshot-symbols")
        tapAt(first) // same physical coordinate -> text
        rule.runOnIdle { assertEquals(original, vm.keyboardState.value); assertTrue(vm.page.value is KeyboardPage.Main) }
        tapAt(second) // text -> numbers
        label(1, "!@#"); label(2, label)
        same(first, bounds(1)); same(second, bounds(2))
        rule.onNodeWithText("1").performTouchInput { click() }
        save("$screenshot-numbers")
        tapAt(second) // same physical coordinate -> text
        rule.runOnIdle { assertEquals(original, vm.keyboardState.value); assertTrue(vm.page.value is KeyboardPage.Main) }
        tapAt(first); tapAt(second) // text -> symbols -> numbers
        label(1, "!@#"); label(2, label)
        tapAt(first) // numbers -> symbols
        label(1, label); label(2, "123")
        tapAt(first) // symbols -> text, never back to numbers
        rule.runOnIdle {
            assertEquals(original, vm.keyboardState.value)
            assertTrue(vm.page.value is KeyboardPage.Main)
            assertEquals(listOf("1"), keys) // mode keys cannot type commands or change language
        }
        save("$screenshot-text")
    }
    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        val dir = File(app.getExternalFilesDir(null), "mode-navigation").apply { mkdirs() }
        rule.onNodeWithTag("navigation-keyboard", true).captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun chinese26UsesDirectPeerNavigation() { mount("rime_ice"); verifyTriangle("中文", "chinese26") }
    @Test fun englishReturnNeverUsesGlobeOrChangesLanguage() { mount("rime_ice", true); verifyTriangle("ABC", "english") }
    @Test fun chinese14KeepsTheSameTwoSlots() { mount("pinyin_14jian"); verifyTriangle("中文", "chinese14") }
    @Test fun doublePinyinKeepsTheSameTwoSlots() { mount("double_pinyin_flypy"); verifyTriangle("中文", "double-pinyin") }
    @Test fun nineKeyOnTabletKeepsExactNavigationBounds() { mount("t9_pinyin", width = 1000); verifyTriangle("中文", "tablet-t9") }
    @Test fun strokeKeepsExactNavigationBounds() { mount("stroke"); verifyTriangle("中文", "stroke") }
    @Test fun splitKeyboardKeepsTheTwoSlotsAtTheLeftEdge() { mount("rime_ice", true, 1000, true); verifyTriangle("ABC", "split") }
    @Test fun floatingKeyboardKeepsItsSlotCoordinates() { mount("rime_ice", false, 1000, floating = true); verifyTriangle("中文", "floating") }
    @Test fun japanese26ReturnsToJapanese() { mount("jaroomaji"); verifyTriangle("あいう", "japanese26") }

    @Test fun japaneseFlickKeepsItsKeysAndOpensCommonSymbolsInOneTap() {
        mount("japanese_kana")
        val symbolBounds = rule.onNodeWithText("記号").fetchSemanticsNode().boundsInRoot
        val numbersBounds = rule.onNodeWithText("123").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("記号").performTouchInput { click() }
        rule.onNodeWithText("常用").assertIsDisplayed()
        rule.onNodeWithText("、").assertIsDisplayed()
        rule.onNodeWithText("ー").assertIsDisplayed()
        rule.onNodeWithText("符号").assertDoesNotExist()
        label(1, "あいう")
        rule.onNodeWithTag("mode-slot-1", true).performTouchInput { click() }
        same(symbolBounds, rule.onNodeWithText("記号").fetchSemanticsNode().boundsInRoot)
        same(numbersBounds, rule.onNodeWithText("123").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertTrue(keys.isEmpty()) }
        save("japanese-flick")
    }

    @Test fun handwritingReturnsToTheCanvasFromEitherSecondaryPage() {
        mount("rime_ice")
        rule.runOnIdle { vm.switchMain(MainType.HANDWRITING) }
        val first = rule.onNodeWithTag("handwriting-key:symbol", true).fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("handwriting-key:number", true).fetchSemanticsNode().boundsInRoot
        tapAt(first)
        same(first, bounds(1)); same(second, bounds(2))
        tapAt(first)
        rule.onNodeWithTag("handwriting-canvas", true).assertIsDisplayed()
        tapAt(second)
        same(first, bounds(1)); same(second, bounds(2))
        tapAt(second)
        rule.onNodeWithTag("handwriting-canvas", true).assertIsDisplayed()
    }

    @Test fun viewModelReturnsToOriginalLayoutAndMainThroughToolOverlays() {
        vm = KeyboardViewModel(app)
        for (layout in listOf(KeyboardLayoutState.Chinese, KeyboardLayoutState.English, KeyboardLayoutState.T9Pinyin, KeyboardLayoutState.Stroke)) {
            vm.switchMain(MainType.FULL); vm.setKeyboardState(layout)
            vm.enterPanel(PanelType.NUMBER); vm.showOverlay(OverlayRoute.Symbol)
            vm.enterPanel(PanelType.NUMBER); vm.showOverlay(OverlayRoute.Menu)
            vm.enterPanel(PanelType.NUMBER); vm.returnToTextKeyboard()
            assertEquals(layout, vm.keyboardState.value)
            assertEquals(KeyboardPage.Main(MainType.FULL), vm.page.value)
        }
        vm.switchMain(MainType.HANDWRITING)
        vm.enterPanel(PanelType.NUMBER); vm.showOverlay(OverlayRoute.Symbol)
        vm.enterPanel(PanelType.NUMBER); vm.returnToTextKeyboard()
        assertEquals(KeyboardPage.Main(MainType.HANDWRITING), vm.page.value)
    }
}
