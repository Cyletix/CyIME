package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
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
 * 编辑面板（四行：行首/行尾、段首/段尾、选择）出图：手机、平板宽 × 最小、默认高度 ×
 * 浅色纯色 / 深色玻璃 / 图片主题，看四角文字是否压到中间的方向圆盘。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w900dp-h500dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditPanelRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderEditPanel() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var width by mutableStateOf(360)
        var height by mutableStateOf(250)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            XimeTheme(darkTheme = dark, themeId = theme) {
                CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("edit-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground,
                            dark, palette.background, config)) {
                        EditKeyboardLayout(onAction = {}, onBack = {}, backgroundColor = palette.background,
                            textColor = palette.text, accentColor = palette.accent, keyBgColor = palette.key,
                            showBackKey = false, modifier = Modifier.requiredSize(width.dp, height.dp))
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTagBounds("edit-body")
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // 手机 360dp、平板 800dp；最小高度取尺寸调节的下限附近，默认取常用键盘高度。
        val sizes = listOf(Triple("phone", 360, listOf("min" to 180, "default" to 250)),
            Triple("tablet", 800, listOf("min" to 200, "default" to 320)))
        val looks = listOf(Triple("soft_blue", false, false), Triple("pure_black", true, true),
            Triple("olivcelso_desert", false, false))
        for ((device, w, heights) in sizes) for ((label, h) in heights) for ((t, d, g) in looks) {
            com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(app).edit().putString("keyboard_theme", t).commit()
            rule.runOnIdle { theme = t; dark = d; glass = g; width = w; height = h }
            rule.waitForIdle()
            shot("$device-$label-$t-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}")
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithTagBounds(tag: String) =
        onNode(androidx.compose.ui.test.hasTestTag(tag), true).fetchSemanticsNode().boundsInRoot
}
