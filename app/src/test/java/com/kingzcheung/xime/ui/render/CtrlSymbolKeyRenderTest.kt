package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
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
 * 26 键（和 14 键）按住 !@# 当 Ctrl：刚按下键面不变，按满时限（这里 2 秒）字母键渐变成快捷键键面，再拖到 C 上。
 * 浅、深、纯黑、纯白，玻璃开关，手机/平板宽度。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CtrlSymbolKeyRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderCtrlHold() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val schema = System.getenv("CYIME_RENDER_SCHEMA").orEmpty()
        if (schema.isNotEmpty()) KeysConfigHelper.setActiveKeyboardSchema(schema)
        val vm = KeyboardViewModel(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(240)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config, symbolKeyHoldSeconds = 2f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark),
                            false, Modifier.requiredSize(width.dp, height.dp))
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
        val prefix = if (schema.isEmpty()) "26" else schema
        for ((w, h) in listOf(360 to 240, 800 to 300)) for ((id, isDark) in listOf("soft_blue" to false,
            "soft_blue" to true, "pure_black" to true, "pure_black" to false)) for (g in listOf(false, true)) {
            rule.runOnIdle { width = w; height = h; theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            val name = "$prefix-$w-$id-${if (isDark) "dark" else "light"}-${if (g) "glass" else "solid"}"
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("mode-slot-1", true).performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(300)
            shot("$name-0-just-pressed")
            rule.mainClock.advanceTimeBy(1700 + com.kingzcheung.xime.ui.keyboard.CTRL_FACE_FADE_MS / 2L)
            shot("$name-0-fading")
            rule.mainClock.advanceTimeBy(com.kingzcheung.xime.ui.keyboard.CTRL_FACE_FADE_MS + 50L)
            rule.mainClock.autoAdvance = true
            shot("$name-1-held")
            val symbol = rule.onNodeWithTag("mode-slot-1", true).fetchSemanticsNode().boundsInRoot
            val c = rule.onNodeWithTag("qwerty-key:c", true).fetchSemanticsNode().boundsInRoot
            rule.onNodeWithTag("mode-slot-1", true).performTouchInput {
                val target = c.center - symbol.topLeft
                for (step in 1..8) moveTo(center + (target - center) * (step / 8f))
            }
            rule.waitForIdle()
            shot("$name-2-drag-c")
            // 拖回键外空白再松手：什么都不发，只是收起。
            rule.onNodeWithTag("mode-slot-1", true).performTouchInput { moveTo(center + androidx.compose.ui.geometry.Offset(0f, -2000f)); up() }
            rule.waitForIdle()
        }
    }
}
