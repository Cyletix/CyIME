package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Insets
import android.provider.Settings
import android.view.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.KeyboardPanelLaunch
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.service.extractBottomInset
import com.kingzcheung.xime.service.fixedKeyboardBottomInsetPx
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent
import com.kingzcheung.xime.ui.settings.SettingsSearchFocus
import com.kingzcheung.xime.ui.settings.SettingsSearchIndex
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "sw360dp-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardBottomEdgeRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun edgeOptionIsReachableFromSettingsSearch() {
        val context = rule.activity
        KeyboardThemes.initFromConfig(context)
        AppFonts.initialize(context)
        val title = "手势导航时键盘贴底"
        SettingsPreferences.setEdgeToEdgeKeyboard(context, false)
        SettingsSearchFocus.open(SettingsSearchIndex.first { it.title == title })
        rule.setContent { XimeTheme(darkTheme = false, themeId = "soft_blue") {
            LayoutDisplaySettingsContent(onBack = {}, initialPage = "bottom_gap")
        } }
        val row = rule.onNodeWithTag("settings-search-anchor:$title")
        rule.waitUntil(4_000) { row.isDisplayed() }
        row.assertIsDisplayed().performClick()
        assertTrue(SettingsPreferences.edgeToEdgeKeyboard(context))
        save("edge-setting")
        row.performClick()
        assertEquals(false, SettingsPreferences.edgeToEdgeKeyboard(context))
    }

    @Test fun bottomGapCanBeRecoveredWithTheKeyboardClosed() {
        val app = rule.activity.application
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        SettingsPreferences.setEdgeToEdgeKeyboard(app, false)
        SettingsPreferences.setKeyboardBottomAdjustmentDp(app, 2)
        val entry = SettingsSearchIndex.first { it.title == "底部留白校准" }
        rule.setContent {
            XimeTheme(darkTheme = false, themeId = "soft_blue") {
                LayoutDisplaySettingsContent(onBack = {}, initialPage = entry.target.substringAfter("?page="))
            }
        }
        val section = rule.onNodeWithTag("settings-search-anchor:固定键盘")
        rule.waitUntil(4_000) { section.isDisplayed() }
        val down = rule.onNodeWithText("调低")
        val up = rule.onNodeWithText("抬高")
        repeat(2) { down.performTouchInput { click(center) } }
        rule.onNodeWithText("从系统建议调低 2 dp").assertIsDisplayed()
        assertEquals(-2, SettingsPreferences.getKeyboardBottomAdjustmentDp(app))
        if (System.getenv("CYIME_RENDER_OUT") != null) {
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
            save("bottom-recovery-settings")
        }
        repeat(2) { up.performTouchInput { click(center) } }
        assertEquals(2, SettingsPreferences.getKeyboardBottomPaddingDp(app))
        rule.onNodeWithTag("settings-search-anchor:手势导航时键盘贴底").performClick()
        // 贴底另记一份调低值，从贴底位置算起；还能往下调（11-03：识别不出手势导航的手机也能自己去掉底下那段）。
        rule.onNodeWithText("从安全下限抬高 2 dp").assertIsDisplayed()
        down.performTouchInput { click(center) }
        rule.onNodeWithText("已选安全下限").assertIsDisplayed()
        down.performTouchInput { click(center) }
        rule.onNodeWithText("从安全下限调低 2 dp").assertIsDisplayed()
        rule.onNodeWithText("恢复系统建议").performClick()
        rule.onNodeWithText("使用系统建议").assertIsDisplayed()
        assertEquals(false, SettingsPreferences.edgeToEdgeKeyboard(app))
        assertEquals(0, SettingsPreferences.getKeyboardBottomAdjustmentDp(app))
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals(null, KeyboardPanelLaunch.take())
    }

    @Test fun aShortFixedKeyboardMovesDownWithoutShrinkingKeysAndEnterStillWorks() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val viewModel = spy(KeyboardViewModel(app))
        val pressed = mutableListOf<String>()
        val callbacks = KeyboardCallbacks(onKeyPress = { key, _ -> pressed.add(key) }, onCandidateSelect = {})
        val t9 = mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        doReturn(t9).whenever(viewModel).bindT9Controller(callbacks)
        val candidates = mutableStateOf(CandidateState(inputText = "ntm",
            candidates = listOf("南天门", "你听吗", "你听么"), isComposing = true))
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48)).build()
        val win = rule.activity.window
        Settings.Secure.putInt(app.contentResolver, "navigation_mode", 2)
        SettingsPreferences.setEdgeToEdgeKeyboard(app, false)
        val normal = extractBottomInset(insets, win)
        val bottom = mutableIntStateOf(fixedKeyboardBottomInsetPx(insets, win, normal))
        val schema = mutableStateOf("rime_ice")
        rule.setContent {
            Box(Modifier.size(360.dp, 300.dp).background(Color.DarkGray).testTag("viewport")) {
                KeyboardView(viewModel, KeyboardUiState(keyboardHeightDp = 244, currentSchemaId = schema.value,
                    isDarkTheme = true), callbacks, candidateState = candidates, inputSessionManagedByHost = true,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = -bottom.intValue.dp)
                        .size(360.dp, 244.dp))
            }
        }
        val enter = rule.onNodeWithTag("qwerty-enter-key", true)
        val initial = enter.fetchSemanticsNode().boundsInRoot
        val word = rule.onNodeWithText("南天门", useUnmergedTree = true)
        val initialWord = word.fetchSemanticsNode().boundsInRoot.width
        save("bottom-automatic", "viewport")
        rule.runOnIdle {
            SettingsPreferences.setEdgeToEdgeKeyboard(app, true)
            bottom.intValue = fixedKeyboardBottomInsetPx(insets, win, normal)
            SettingsPreferences.setCandidateTextSize(app, 14)
        }
        val lowered = enter.fetchSemanticsNode().boundsInRoot
        assertEquals(0, bottom.intValue)
        assertEquals(48f, lowered.bottom - initial.bottom, 0.5f)
        assertEquals(initial.height, lowered.height, 0.5f)
        assertTrue(word.fetchSemanticsNode().boundsInRoot.width < initialWord)
        enter.performTouchInput { click(center) }
        assertEquals(listOf("enter"), pressed)
        save("bottom-edge-14sp", "viewport")

        rule.runOnIdle {
            val more = listOf("能听吗", "你听没", "难听吗", "能听么", "你听嘛", "你太慢", "你同意吗")
            candidates.value = candidates.value.copy(expandedCandidatesLoaded = true,
                expandedCandidates = (candidates.value.candidates + more).map { RimeCandidate(it, "") })
            viewModel.setCandidatePageExpanded(true)
        }
        // The first few words remain in the strip. Later ones appear in the expanded grid.
        rule.onNodeWithTag("expanded-candidate:8", true).assertIsDisplayed()
        rule.onNodeWithTag("expanded-enter-key", true).assertIsDisplayed()
        save("bottom-expanded", "viewport")
        rule.runOnIdle { viewModel.setCandidatePageExpanded(false) }
        for ((id, bottomKey) in listOf("pinyin_14jian" to "qwerty-enter-key", "t9_pinyin" to "t9-zero-key")) {
            rule.runOnIdle {
                schema.value = id
                viewModel.dispatch(KeyboardDispatchAction.InputSessionStarted(false, id))
            }
            val bounds = rule.onNodeWithTag(bottomKey, true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.bottom <= rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot.bottom)
            save("bottom-$id", "viewport")
        }
        rule.runOnIdle { viewModel.dispatch(KeyboardDispatchAction.ShowNumber) }
        rule.onNodeWithTag("number-zero", true).assertIsDisplayed()
        save("bottom-number", "viewport")
        rule.runOnIdle { viewModel.dispatch(KeyboardDispatchAction.ShowCommonSymbol) }
        rule.waitForIdle()
        save("bottom-symbol", "viewport")
    }

    @Test fun floatingDragBarLeavesTheBottomRowTappableAtItsLowerEdge() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val viewModel = spy(KeyboardViewModel(app))
        val pressed = mutableListOf<String>()
        val callbacks = KeyboardCallbacks(onKeyPress = { key, _ -> pressed.add(key) }, onCandidateSelect = {})
        val t9 = mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        doReturn(t9).whenever(viewModel).bindT9Controller(callbacks)
        val schema = mutableStateOf("rime_ice")
        rule.setContent {
            KeyboardView(viewModel, KeyboardUiState(isFloatingMode = true, keyboardWidthDp = 320, keyboardHeightDp = 244,
                floatingOffsetY = 18, floatingMinOffsetY = 18, currentSchemaId = schema.value, isDarkTheme = true),
                callbacks, inputSessionManagedByHost = true, modifier = Modifier.size(360.dp, 400.dp).testTag("float-host"))
        }
        for ((id, tag) in listOf("rime_ice" to "qwerty-enter-key", "pinyin_14jian" to "qwerty-enter-key",
            "t9_pinyin" to "t9-zero-key")) {
            rule.runOnIdle {
                viewModel.dispatch(KeyboardDispatchAction.InputSessionStarted(false, id))
                schema.value = id
            }
            val key = rule.onNodeWithTag(tag, true)
            val bounds = key.fetchSemanticsNode().boundsInRoot
            val bar = rule.onNodeWithTag("floating-drag-bar", true).fetchSemanticsNode().boundsInRoot
            assertTrue("$id key=$bounds drag=$bar", bounds.bottom <= bar.top + 1f)
            assertDragIndicatorCentered(id)
            if (tag == "qwerty-enter-key") {
                pressed.clear()
                key.performTouchInput { click(Offset(center.x, height - 2f)) }
                rule.runOnIdle { assertEquals(listOf("enter"), pressed) }
            } else {
                pressed.clear()
                val host = rule.onNodeWithTag("float-host", true)
                val origin = host.fetchSemanticsNode().boundsInRoot.topLeft
                host.performTouchInput { click(Offset(bar.center.x - origin.x, bar.top - origin.y - 10f)) }
                rule.runOnIdle { assertEquals(listOf("space"), pressed) }
            }
        }
        save("floating-bottom-hit-areas", "float-host")
    }

    /** 平板尺寸的悬浮键盘：小横条同样在底排键帽下沿和卡片底边正中（11-03 平板实测低了约 5dp）。 */
    @Config(qualifiers = "sw800dp-w1280dp-h800dp-mdpi")
    @Test fun floatingDragIndicatorSitsMidwayBelowTheKeysOnTablet() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val viewModel = spy(KeyboardViewModel(app))
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
        val t9 = mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        doReturn(t9).whenever(viewModel).bindT9Controller(callbacks)
        val schema = mutableStateOf("rime_ice")
        rule.setContent {
            KeyboardView(viewModel, KeyboardUiState(isFloatingMode = true, keyboardWidthDp = 560, keyboardHeightDp = 300,
                floatingOffsetY = 18, floatingMinOffsetY = 18, currentSchemaId = schema.value, isDarkTheme = true),
                callbacks, inputSessionManagedByHost = true, modifier = Modifier.size(1280.dp, 800.dp).testTag("float-host"))
        }
        for (id in listOf("rime_ice", "t9_pinyin")) {
            rule.runOnIdle {
                viewModel.dispatch(KeyboardDispatchAction.InputSessionStarted(false, id))
                schema.value = id
            }
            assertDragIndicatorCentered(id)
        }
        save("floating-drag-indicator-tablet", "float-host")
    }

    /**
     * 小横条竖直方向在「底排键帽看得见的下沿」和卡片底边的正中，左右在卡片中线上。
     * 键帽下沿按像素找：在卡片左侧 12% 处（底排最左的键上，避开中间的小横条）从卡片底往上找第一处不是底色的像素。
     */
    private fun assertDragIndicatorCentered(id: String) {
        rule.waitForIdle()
        val card = rule.onNodeWithTag("floating-keyboard-card", true).fetchSemanticsNode().boundsInWindow
        val indicator = rule.onNodeWithTag("floating-drag-indicator", true).fetchSemanticsNode().boundsInWindow
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        val x = (card.left + card.width * 0.12f).roundToInt()
        val startY = (card.bottom - 3f).roundToInt()
        val background = bitmap.getPixel(x, startY)
        fun differs(c: Int): Boolean {
            val d = kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(background)) +
                kotlin.math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(background)) +
                kotlin.math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(background))
            return d > 36
        }
        var y = startY
        while (y > card.top.roundToInt() && !differs(bitmap.getPixel(x, y))) y--
        val capBottom = y + 1f
        assertTrue("$id no keycap found above the drag bar, card=$card", capBottom > card.top + 1f)
        val expected = (capBottom + card.bottom) / 2f
        assertEquals("$id indicator=$indicator capBottom=$capBottom card=$card", expected, indicator.center.y, 1.5f)
        assertEquals("$id indicator=$indicator card=$card", card.center.x, indicator.center.x, 1f)
    }

    private fun save(name: String, tag: String? = null) {
        val dir = System.getenv("CYIME_RENDER_OUT") ?: return
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        val visible = tag?.let {
            val r = rule.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow
            Bitmap.createBitmap(bitmap, r.left.roundToInt(), r.top.roundToInt(), r.width.roundToInt(), r.height.roundToInt())
        } ?: bitmap
        File(dir, "$name.png").outputStream().use { visible.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
