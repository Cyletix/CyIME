package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.settings.InputProfileSelectors
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 当前方案是小鹤双拼时，键盘里的选择面板和设置页的布局行：没有小鹤双拼的布局（九键）标灰。
 * 浅色/纯黑 × 不透明/玻璃 × 手机/平板，加设置页浅色/深色。只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchemeLayoutHintRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderUnavailableLayouts() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        val schemas = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "double_pinyin_flypy", "double_pinyin_flypy_14jian")
            .map { SchemaInfo(it, if (it.startsWith("double_pinyin_flypy")) "小鹤双拼" else it, "", "", "") }
        val current = "double_pinyin_flypy"
        var theme by mutableStateOf("soft_blue")
        var glass by mutableStateOf(false)
        var tablet by mutableStateOf(false)
        var settings by mutableStateOf(false)
        rule.setContent {
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
                if (settings) XimeTheme(darkTheme = dark) {
                    Surface(Modifier.requiredSize(400.dp, 300.dp).testTag("layout-body")) {
                        InputProfileSelectors(schemas, schemas.first { it.schemaId == current }) {}
                    }
                } else MaterialTheme {
                    Box(Modifier.requiredSize(width, height).testTag("layout-body")
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        SchemaListView(schemas, current, palette.background, palette.accent, palette.text, palette.key,
                            onSelectSchema = {}, modifier = Modifier.requiredSize(width, height))
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            rule.waitForIdle()
            val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (t in listOf("soft_blue", "pure_black")) for (g in listOf(false, true)) for (tab in listOf(false, true)) {
            rule.runOnIdle { theme = t; glass = g; tablet = tab; settings = false }
            shot("panel-$t-${if (g) "glass" else "solid"}-${if (tab) "tablet" else "phone"}")
        }
        for (t in listOf("soft_blue", "pure_black")) {
            rule.runOnIdle { theme = t; settings = true }
            shot("settings-$t")
        }
    }
}
