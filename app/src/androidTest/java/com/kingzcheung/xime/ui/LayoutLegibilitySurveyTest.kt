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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
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
 * 每个布局 × 四个主题 × 深浅色 × 玻璃开关的主键盘截图（叠在彩色条纹上），用于逐键检查文字可读性。
 * Cyletix10 的改位字母强调在玻璃下曾变成黑键黑字。
 */
class LayoutLegibilitySurveyTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val preferenceFiles = mutableSetOf<String>()
    private val namespace = "layout-survey-${System.nanoTime()}"
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = "$namespace-$name"
            preferenceFiles += file
            return app.getSharedPreferences(file, mode)
        }
    }
    private val schemas = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "pinyin_cyletix10", "japanese_kana", "stroke", "wubi86")

    @After fun releaseFixture() {
        store.clear()
        preferenceFiles.forEach { app.deleteSharedPreferences(it) }
        KeysConfigHelper.setActiveKeyboardSchema(SettingsPreferences.getCurrentSchema(app))
    }

    @Test fun ios() = survey(TransparentGlassTheme.ID)
    @Test fun black() = survey(PureBlackTheme.ID)
    @Test fun lavender() = survey("lavender_purple")
    @Test fun blue() = survey(SoftBlueTheme.ID)

    private fun survey(themeId: String) {
        SettingsPreferences.setKeyboardTheme(context, themeId)
        KeysConfigHelper.loadConfig(app)
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app)).get(KeyboardViewModel::class.java)
        val state = mutableStateOf(KeyboardUiState(currentSchemaId = "rime_ice", isDarkTheme = true, themeId = themeId,
            schemas = schemas.map { SchemaInfo(it, it, "", "", "") }))
        val glass = mutableStateOf(false)
        rule.setContent {
            val dark = state.value.isDarkTheme
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT; screenWidthDp = 400; screenHeightDp = 800
            }
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(2f),
                LocalConfiguration provides configuration,
                com.kingzcheung.xime.ui.keyboard.LocalFrostedGlassPreview provides
                    com.kingzcheung.xime.settings.FrostedGlassProfiles(FrostedGlassConfig.defaults(false, enabled = glass.value),
                        FrostedGlassConfig.defaults(true, enabled = glass.value))) {
                XimeTheme(darkTheme = dark, themeId = themeId) {
                    Box(Modifier.requiredSize(400.dp, 300.dp).background(Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))))) {
                        KeyboardView(vm, state.value, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.requiredSize(400.dp, 300.dp).testTag("survey-keyboard"),
                            candidateState = mutableStateOf(CandidateState()), inputSessionManagedByHost = true)
                    }
                }
            }
        }
        for (schema in schemas) for (dark in listOf(true, false)) for (g in listOf(false, true)) {
            rule.runOnIdle {
                vm.resetKeyboard(false, schema)
                glass.value = g
                state.value = state.value.copy(currentSchemaId = schema, isDarkTheme = dark)
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            save("$themeId-$schema-${if (dark) "dark" else "light"}-${if (g) "glass" else "plain"}")
        }
    }

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("survey-keyboard").captureToImage().asAndroidBitmap()
        val directory = File(app.getExternalFilesDir(null), "layout-survey").apply { mkdirs() }
        File(directory, "$name.jpg").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
    }
}
