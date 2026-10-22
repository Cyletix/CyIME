package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.ColemakLayout
import com.kingzcheung.xime.settings.Cyletix10Layout
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.LetterLayouts
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Cyletix10 / Colemak typed in Chinese, English and Japanese romaji: 4 themes × glass, phone and tablet,
 * plus the layout picker for English and Japanese. Only renders when CYIME_RENDER_OUT is set.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LetterLayoutRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderLetterLayouts() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val schemas = listOf("rime_ice", "t9_pinyin", Cyletix10Layout.ID, ColemakLayout.ID, "japanese", "japanese_kana")
            .map { SchemaInfo(it, it, "", "", "") } + InputModes.english
        var theme by mutableStateOf("soft_blue")
        var glass by mutableStateOf(false)
        var tablet by mutableStateOf(false)
        var schema by mutableStateOf("rime_ice")
        var ascii by mutableStateOf(false)
        var panel by mutableStateOf(false)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val dark = theme == "pure_black"
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            val width = if (tablet) 900.dp else 400.dp
            val height = if (tablet) 340.dp else 280.dp
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (tablet) 900 else 400; screenHeightDp = if (tablet) 1280 else 860
                smallestScreenWidthDp = screenWidthDp
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalKeyboardExplicitWidth provides true,
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width, height).testTag("layout-body")
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        if (panel) SchemaListView(schemas.filter { it.schemaId == InputModes.selectedId(schema, ascii) },
                            InputModes.selectedId(schema, ascii), palette.background, palette.accent, palette.text, palette.key,
                            onSelectSchema = {}, modifier = Modifier.requiredSize(width, height), availableSchemas = schemas)
                        else KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark, isAsciiMode = ascii),
                            ascii, Modifier.requiredSize(width, height))
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun show(language: String, layout: String, t: String, g: Boolean, tab: Boolean, picker: Boolean = false) {
            val (s, a) = when (language) { "en" -> "rime_ice" to true; "ja" -> "japanese" to false; else -> layout to false }
            LetterLayouts.setForTest(InputLanguage.ENGLISH, if (language == "en") layout else null)
            LetterLayouts.setForTest(InputLanguage.JAPANESE, if (language == "ja") layout else null)
            KeysConfigHelper.applyLetterLayouts()
            KeysConfigHelper.setActiveKeyboardSchema(s)
            rule.runOnIdle { vm.resetKeyboard(a, s); schema = s; ascii = a; theme = t; glass = g; tablet = tab; panel = picker; revision++ }
            rule.waitForIdle()
        }
        for (layout in listOf(Cyletix10Layout.ID, ColemakLayout.ID)) for (language in listOf("zh", "en", "ja"))
            for (t in listOf("transparent_glass", "pure_black", "lavender_purple", "soft_blue"))
                for (g in listOf(false, true)) for (tab in listOf(false, true)) {
                    show(language, layout, t, g, tab)
                    shot("${layout.removePrefix("pinyin_")}-$language-$t-${if (g) "glass" else "solid"}-${if (tab) "tablet" else "phone"}")
                }
        for (language in listOf("en", "ja")) for (tab in listOf(false, true)) {
            show(language, Cyletix10Layout.ID, "soft_blue", false, tab, picker = true)
            shot("picker-$language-${if (tab) "tablet" else "phone"}")
        }
        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
        LetterLayouts.setForTest(InputLanguage.JAPANESE, null)
        KeysConfigHelper.applyLetterLayouts()
    }
}
