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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
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
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.TonalThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots the restored tonal themes (light/dark × plain/glass) on the 26-key keyboard with candidates,
 * the symbol, clipboard and number panels, over a striped host. Output: files/tonal-survey/ on the device.
 */
class TonalThemesSurveyTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val preferenceFiles = mutableSetOf<String>()
    private val namespace = "tonal-survey-${System.nanoTime()}"
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

    @Test fun lightPlain() = surveyAll(dark = false, glass = false)
    @Test fun darkPlain() = surveyAll(dark = true, glass = false)
    @Test fun lightGlass() = surveyAll(dark = false, glass = true)
    @Test fun darkGlass() = surveyAll(dark = true, glass = true)

    private fun surveyAll(dark: Boolean, glass: Boolean) {
        KeyboardThemes.reload(app)
        val ids = TonalThemes.create().map { it.id }
        ids.forEach { assertEquals(it, KeyboardThemes.getThemeById(it).id) }
        val themeState = mutableStateOf(ids.first())
        FrostedGlassPreferences.save(context, FrostedGlassConfig.defaults(dark, enabled = glass), dark)
        KeysConfigHelper.loadConfig(app)
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app)).get(KeyboardViewModel::class.java)
        vm.resetKeyboard(false, "rime_ice")
        val state = mutableStateOf(KeyboardUiState(currentSchemaId = "rime_ice", isDarkTheme = dark,
            themeId = ids.first(), schemas = listOf(SchemaInfo("rime_ice", "rime_ice", "", "", ""))))
        val candidates = mutableStateOf(CandidateState(candidates = listOf("你好", "拟好", "你", "妮", "泥", "尼"),
            inputText = "nihao", preeditText = "ni hao", isComposing = true, hasNextPage = true))
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 400
                screenHeightDp = 800
            }
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(2f),
                LocalConfiguration provides configuration) {
                XimeTheme(darkTheme = dark, themeId = themeState.value) {
                    Box(Modifier.requiredSize(400.dp, 340.dp).background(Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))))) {
                        KeyboardView(vm, state.value, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.requiredSize(400.dp, 340.dp).testTag("survey-keyboard"),
                            candidateState = candidates, inputSessionManagedByHost = true)
                    }
                }
            }
        }
        val mode = (if (dark) "dark" else "light") + if (glass) "-glass" else ""
        for (id in ids) {
            rule.runOnIdle {
                SettingsPreferences.setKeyboardTheme(context, id)
                themeState.value = id
                state.value = state.value.copy(themeId = id)
                vm.closeOverlay(); vm.exitPanel()
                candidates.value = candidates.value.copy(isComposing = true)
            }
            settle(); save("$id-$mode-main")
            val steps: List<Pair<String, () -> Unit>> = listOf(
                "symbol" to { candidates.value = candidates.value.copy(isComposing = false); vm.showOverlay(OverlayRoute.Symbol) },
                "clipboard" to { vm.closeOverlay(); vm.showOverlay(OverlayRoute.Clipboard(0)) },
                "number" to { vm.closeOverlay(); vm.enterPanel(PanelType.NUMBER) },
            )
            for ((name, action) in steps) {
                rule.runOnIdle { action() }
                settle(); save("$id-$mode-$name")
            }
        }
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val image = rule.onNodeWithTag("survey-keyboard").captureToImage().asAndroidBitmap()
        val directory = File(app.getExternalFilesDir(null), "tonal-survey").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
