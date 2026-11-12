package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TraditionalLayoutRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun preferenceDefaultsOffAndRoundTripsWithoutChangingOtherPreferences() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(app)
        prefs.edit().clear().commit()
        assertFalse(KeyboardInputPreferences.read(app).baiduTraditionalLayout)
        val before = KeyboardInputPreferences.read(app)
        before.copy(layoutStyle = KeyboardLayoutStyle.BAIDU_TRADITIONAL).save(app)
        assertTrue(KeyboardInputPreferences.read(app).baiduTraditionalLayout)
        assertTrue(KeyboardInputPreferences.BAIDU_TRADITIONAL_LAYOUT_KEY in KeyboardInputPreferences.watchedKeys)
        assertEquals(before.qwertyCommaKey, KeyboardInputPreferences.read(app).qwertyCommaKey)
        before.copy(layoutStyle = KeyboardLayoutStyle.DEFAULT).save(app)
        assertFalse(KeyboardInputPreferences.read(app).baiduTraditionalLayout)
    }

    @Test fun numberPositionsActionsAndDefaultRestoration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        AppFonts.initialize(app)
        var traditional by mutableStateOf(true)
        var size by mutableStateOf(400 to 280)
        val presses = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(layoutStyle = if (traditional) KeyboardLayoutStyle.BAIDU_TRADITIONAL else KeyboardLayoutStyle.DEFAULT)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(size.first.dp, size.second.dp).background(Color(0xFFE7E7EC)).testTag("canvas")) {
                        NumberKeyboardLayout({ presses.add(it) }, Color.White, Color.Black, Color.LightGray,
                            modifier = Modifier.requiredSize(size.first.dp, size.second.dp), specialKeyTextColor = Color.Black)
                    }
                }
            }
        }
        for ((w, h) in listOf(400 to 280, 800 to 320, 280 to 190)) {
            rule.runOnIdle { size = w to h }
            rule.waitForIdle()
            val at = rule.onNodeWithTag("number-at-key", true).fetchSemanticsNode().boundsInRoot
            val decimal = rule.onNodeWithTag("number-decimal-key", true).fetchSemanticsNode().boundsInRoot
            val zero = rule.onNodeWithTag("number-zero", true).fetchSemanticsNode().boundsInRoot
            val back = rule.onNodeWithTag("traditional-number-back", true).fetchSemanticsNode().boundsInRoot
            assertTrue(at.bottom <= decimal.top + 1f)
            assertTrue(decimal.bottom <= zero.top + 1f)
            assertTrue(back.right <= zero.left)
            assertTrue(zero.width > 0 && zero.height > 0)
            capture("number-${w}x$h")
        }
        rule.onNodeWithText("返回", true).performClick()
        rule.onNodeWithText("符号", true).performClick()
        rule.onNodeWithText("@", true).performClick()
        rule.onNodeWithText(".", true).performClick()
        assertEquals(listOf("abc", "symbol", "@", "."), presses)
        rule.runOnIdle { traditional = false }
        rule.waitForIdle()
        rule.onNodeWithTag("traditional-number-back", true).assertDoesNotExist()
        rule.onNodeWithTag("mode-slot-1", true).assertExists()
        rule.onNodeWithTag("mode-slot-2", true).assertExists()
    }

    @Test fun qwertySwitchRestoresTheDefaultFooter() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var traditional by mutableStateOf(true)
        val presses = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(layoutStyle = if (traditional) KeyboardLayoutStyle.BAIDU_TRADITIONAL else KeyboardLayoutStyle.DEFAULT)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 280.dp).background(Color.LightGray).testTag("canvas")) {
                        KeyboardLayout({ presses.add(it) }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = "rime_ice"), false, Modifier.requiredSize(400.dp, 280.dp))
                    }
                }
            }
        }
        rule.onNodeWithTag("traditional-qwerty-bottom", true).assertExists()
        rule.onNodeWithText("123", true).performClick()
        assertEquals("number", presses.last())
        capture("qwerty-400x280")
        rule.runOnIdle { traditional = false }
        rule.waitForIdle()
        rule.onNodeWithTag("traditional-qwerty-bottom", true).assertDoesNotExist()
        rule.onNodeWithTag("mode-slot-1", true).assertExists()
        rule.onNodeWithTag("mode-slot-2", true).assertExists()
    }

    @Test fun nineKeyEnterSpansTwoRowsAndSwitchingRestoresZero() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        AppFonts.initialize(app)
        val controller = org.mockito.kotlin.mock<com.kingzcheung.xime.rime.T9InputController>()
        org.mockito.kotlin.whenever(controller.leftPanelState).thenReturn(com.kingzcheung.xime.rime.T9InputController.LeftPanelState.IDLE)
        org.mockito.kotlin.whenever(controller.firstOptions).thenReturn(emptyList())
        var traditional by mutableStateOf(true)
        val spacePresses = mutableListOf<String>()
        val directCommits = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(layoutStyle = if (traditional) KeyboardLayoutStyle.BAIDU_TRADITIONAL else KeyboardLayoutStyle.DEFAULT)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 280.dp).background(Color.LightGray).testTag("canvas")) {
                        T9KeyboardLayout({ spacePresses.add(it) }, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onCommitText = { directCommits.add(it) }),
                            KeyboardUiState(currentSchemaId = "t9_pinyin"), controller, Color.White, Color.Black, Color.LightGray,
                            modifier = Modifier.requiredSize(400.dp, 280.dp), specialKeyTextColor = Color.Black)
                    }
                }
            }
        }
        val enter = rule.onNodeWithTag("t9-enter-key", true).fetchSemanticsNode().boundsInRoot
        val delete = rule.onNodeWithTag("t9-delete-key", true).fetchSemanticsNode().boundsInRoot
        assertEquals(delete.height * 2f, enter.height, 2f)
        rule.onNodeWithTag("t9-zero-key", true).assertDoesNotExist()
        rule.onNodeWithTag("traditional-t9-space", true).assertExists()
        rule.onNodeWithTag("traditional-t9-space", true).performClick()
        assertEquals(listOf("space"), spacePresses)
        rule.onNodeWithTag("traditional-t9-space", true).performTouchInput { swipeDown() }
        assertEquals(listOf("0"), directCommits)
        capture("t9-400x280")
        rule.runOnIdle { traditional = false }
        rule.waitForIdle()
        rule.onNodeWithTag("t9-zero-key", true).assertExists()
        val normalEnter = rule.onNodeWithTag("t9-enter-key", true).fetchSemanticsNode().boundsInRoot
        assertEquals(delete.height, normalEnter.height, 2f)
    }

    @Test fun settingsToggleIsVisibleAndPersists() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(app).edit()
            .putBoolean(KeyboardInputPreferences.BAIDU_TRADITIONAL_LAYOUT_KEY, false).commit()
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(480.dp, 800.dp).testTag("canvas")) {
                    com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent(onBack = {})
                }
            }
        }
        rule.onNodeWithText("百度传统布局", true).performClick()
        rule.waitForIdle()
        assertTrue(KeyboardInputPreferences.read(app).baiduTraditionalLayout)
        capture("settings-480x800")
        rule.onNodeWithText("默认布局", true).performClick()
        assertFalse(KeyboardInputPreferences.read(app).baiduTraditionalLayout)
    }

    @Test fun englishAbcSwitchesSchemesWithoutOpeningPicker() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val modes = listOf(com.kingzcheung.xime.settings.InputModes.english,
            com.kingzcheung.xime.settings.SchemaInfo(com.kingzcheung.xime.settings.InputModes.ENGLISH_WORDS, "英文单词", "", "", ""))
        var words by mutableStateOf(false)
        val selected = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(layoutStyle = KeyboardLayoutStyle.BAIDU_TRADITIONAL)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(400.dp, 280.dp).testTag("canvas")) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onSwitchSchema = { selected.add(it) }),
                            KeyboardUiState(currentSchemaId = if (words) com.kingzcheung.xime.settings.InputModes.ENGLISH_WORDS else "rime_ice",
                                isAsciiMode = !words, schemas = modes), !words, Modifier.requiredSize(400.dp, 280.dp))
                    }
                }
            }
        }
        rule.onNodeWithText("abc", true).performClick()
        assertEquals(com.kingzcheung.xime.settings.InputModes.ENGLISH_WORDS, selected.last())
        capture("english-direct")
        rule.runOnIdle { words = true }
        rule.waitForIdle()
        rule.onNodeWithText("abc", true).performClick()
        assertEquals(com.kingzcheung.xime.settings.InputModes.ENGLISH, selected.last())
        capture("english-words")
    }

    private fun capture(name: String) {
        val output = System.getenv("CYIME_RENDER_OUT") ?: return
        val bounds = rule.onNodeWithTag("canvas", true).fetchSemanticsNode().boundsInRoot
        val view = rule.activity.window.decorView
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(full)) }
        val crop = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        File(output).mkdirs()
        File(output, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (crop !== full) crop.recycle()
        full.recycle()
    }
}
