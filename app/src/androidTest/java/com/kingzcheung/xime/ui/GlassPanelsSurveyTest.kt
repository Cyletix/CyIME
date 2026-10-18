package com.kingzcheung.xime.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.theme.PureBlackTheme
import com.kingzcheung.xime.ui.theme.SoftBlueTheme
import com.kingzcheung.xime.ui.theme.TransparentGlassTheme
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots every keyboard page, tool panel and the OCR review sheet for one theme mode,
 * over a striped host, inside the same XimeTheme wrapper the service uses.
 */
class GlassPanelsSurveyTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val preferenceFiles = mutableSetOf<String>()
    private val namespace = "glass-survey-${System.nanoTime()}"
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = "$namespace-$name"
            preferenceFiles += file
            return app.getSharedPreferences(file, mode)
        }
    }

    @After fun releaseFixture() {
        store.clear()
        preferenceFiles.forEach { app.deleteSharedPreferences(it) }
        KeysConfigHelper.setActiveKeyboardSchema(SettingsPreferences.getCurrentSchema(app))
    }

    @Test fun iosGlass() = survey("ios-glass", TransparentGlassTheme.ID, dark = true, glass = true)
    @Test fun iosPlain() = survey("ios-plain", TransparentGlassTheme.ID, dark = true, glass = false)
    @Test fun iosLight() = survey("ios-light", TransparentGlassTheme.ID, dark = false, glass = false)
    @Test fun blackDark() = survey("black-dark", PureBlackTheme.ID, dark = true, glass = false)
    @Test fun blackLightGlass() = survey("black-light-glass", PureBlackTheme.ID, dark = false, glass = true)
    @Test fun blueLight() = survey("blue-light", SoftBlueTheme.ID, dark = false, glass = false)
    @Test fun lavenderDark() = survey("lavender-dark", "lavender_purple", dark = true, glass = false)
    @Test fun lavenderLightGlass() = survey("lavender-light-glass", "lavender_purple", dark = false, glass = true)
    @Test fun blueDarkGlass() = survey("blue-dark-glass", SoftBlueTheme.ID, dark = true, glass = true)
    @Test fun iosLightGlass() = survey("ios-light-glass", TransparentGlassTheme.ID, dark = false, glass = true)

    private fun survey(prefix: String, themeId: String, dark: Boolean, glass: Boolean) {
        SettingsPreferences.setKeyboardTheme(context, themeId)
        FrostedGlassPreferences.save(context, FrostedGlassConfig.defaults(dark, enabled = glass), dark)
        KeysConfigHelper.loadConfig(app)
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app)).get(KeyboardViewModel::class.java)
        vm.resetKeyboard(false, "rime_ice")
        val state = mutableStateOf(KeyboardUiState(currentSchemaId = "rime_ice", isDarkTheme = dark,
            themeId = themeId, schemas = listOf(SchemaInfo("rime_ice", "rime_ice", "", "", ""))))
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 400
                screenHeightDp = 800
            }
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(2f),
                LocalConfiguration provides configuration) {
                XimeTheme(darkTheme = dark, themeId = themeId) {
                    // Stripes stand in for host content behind a translucent keyboard.
                    Box(Modifier.requiredSize(400.dp, 340.dp).background(Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))))) {
                        KeyboardView(vm, state.value, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.requiredSize(400.dp, 340.dp).testTag("survey-keyboard"),
                            candidateState = mutableStateOf(CandidateState()), inputSessionManagedByHost = true)
                    }
                }
            }
        }
        rule.waitForIdle()
        save("$prefix-main")
        val steps: List<Pair<String, () -> Unit>> = listOf(
            "symbol" to { vm.showOverlay(OverlayRoute.Symbol) },
            "emoji" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.Emoji) },
            "clipboard" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.Clipboard(0)) },
            "edit" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.Edit) },
            "menu" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.Menu) },
            "schemes" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.SchemaList) },
            "number" to { vm.closeOverlay(); vm.enterPanel(PanelType.NUMBER) },
            "ocr-editor" to { vm.exitPanel(); state.value = state.value.copy(showQuickSendForm = true, ocrDraft = true,
                quickSendEditingItemText = "识别出的示例文字") },
            "handwriting" to { state.value = state.value.copy(showQuickSendForm = false, ocrDraft = false, isHandwritingMode = true)
                vm.switchMain(MainType.HANDWRITING) },
            "handwriting-full" to { state.value = state.value.copy(handwritingExpanded = true) },
        )
        for ((name, action) in steps) {
            rule.runOnIdle { action() }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            save("$prefix-$name")
        }
    }

    /** Whole-screen capture so popups (OCR sheet) are included. */
    private fun save(name: String) {
        Thread.sleep(300)
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "glass-survey").apply { mkdirs() }
        File(directory, "$name.jpg").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
        image.recycle()
    }
}
