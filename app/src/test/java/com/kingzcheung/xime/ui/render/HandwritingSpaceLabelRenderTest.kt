package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
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
 * 手写空格键上的「中文手写」「英文手写」：手写底排比全键盘矮（五行），看字和空格图标会不会挤在一起。
 * 手写区高度 = 键盘总高减 44dp 候选栏；手机 400dp、平板 800/1280dp × 160/228/301（手机默认）/448（平板竖屏默认） × 字号 1.0/1.3 × 浅色/深色。
 * 只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1300dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HandwritingSpaceLabelRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderSpaceLabel() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var width by mutableStateOf(400)
        var height by mutableStateOf(228)
        var fontScale by mutableStateOf(1f)
        var language by mutableStateOf(InputLanguage.CHINESE)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = false)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    HandwritingKeyboardLayout(language = language, bottomPaddingDp = 0,
                        keyTextColor = palette.text, keyBackgroundColor = palette.key,
                        specialKeyBackgroundColor = palette.function, specialKeyTextColor = palette.functionText,
                        modifier = Modifier.requiredSize(width.dp, (height - 44).dp).testTag("hw-body")
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                                palette.background, config))
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("hw-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (w in listOf(400, 800, 1280)) for (h in listOf(160, 228, 301, 448)) for (fs in listOf(1f, 1.3f))
            for (d in listOf(false, true)) for (l in listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH)) {
                rule.runOnIdle {
                    width = w; height = h; fontScale = fs; dark = d; theme = if (d) "pure_black" else "soft_blue"; language = l
                }
                rule.waitForIdle()
                shot("hw-space-w$w-h$h-fs${(fs * 10).toInt()}-${if (d) "dark" else "light"}-${l.id}")
            }
    }
}
