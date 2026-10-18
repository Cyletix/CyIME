package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassProfiles
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.KeyboardInputActions
import com.kingzcheung.xime.ui.keyboard.LanguageKeyButton
import com.kingzcheung.xime.ui.keyboard.LanguageMenuPanel
import com.kingzcheung.xime.ui.keyboard.LocalFrostedGlassPreview
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputActions
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 语言菜单在四个主题、深浅色、玻璃开关下都用不透明的主题色底，截图供人工查看。 */
class LanguageMenuThemesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun languageMenuFollowsEveryOfferedTheme() {
        var theme by mutableStateOf("transparent_glass")
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(true)
        rule.setContent {
            val config = FrostedGlassConfig(glass, 10f, .22f, .29f)
            val palette = resolveKeyboardPalette(theme, dark)
            CompositionLocalProvider(LocalDensity provides Density(2f),
                LocalFrostedGlassPreview provides FrostedGlassProfiles(config, config),
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputActions provides KeyboardInputActions(
                    schemas = listOf(SchemaInfo("first", "中文26键", "", "", "", language = InputLanguage.CHINESE),
                        SchemaInfo("second", "日语模式", "", "", "", language = InputLanguage.JAPANESE)),
                    currentInputModeId = "first", onSwitchSchema = {})) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))))
                        .testTag("menu-host"), contentAlignment = Alignment.Center) {
                        Text(("Q W E R T Y U I O P\n").repeat(30), color = palette.text)
                        Box(Modifier.size(360.dp, 280.dp)) {
                            LanguageMenuPanel {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                                    LanguageKeyButton(onClick = {}, backgroundColor = palette.function,
                                        modifier = Modifier.size(44.dp).testTag("language-key"))
                                }
                            }
                        }
                    }
                }
            }
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "language-menu-themes")
            .apply { mkdirs() }
        for (t in listOf("transparent_glass", "pure_black", "lavender_purple", "soft_blue"))
            for (d in listOf(true, false)) for (g in listOf(true, false)) {
                rule.runOnIdle { theme = t; dark = d; glass = g }
                rule.mainClock.autoAdvance = false
                rule.onNodeWithTag("language-key").performTouchInput { down(center) }
                rule.mainClock.advanceTimeBy(350L)
                rule.mainClock.autoAdvance = true
                rule.waitUntil(5_000L) { rule.onAllNodesWithTag("language-menu").fetchSemanticsNodes().isNotEmpty() }
                Thread.sleep(300)
                val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                File(dir, "$t-${if (d) "dark" else "light"}-${if (g) "glass" else "plain"}.jpg").outputStream()
                    .use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
                image.recycle()
                rule.onNodeWithTag("language-key").performTouchInput { cancel() }
                rule.waitForIdle()
            }
    }
}
