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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.FourteenKeySymbols
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 14 键自己的一套角标（FourteenKeySymbols）：每个键只有一组上下角标，前两排上 1-0、下半角符号，第三排中文标点。
 * 出：平时、er 下拉（上角标 2）、m 上滑（下角标 》）三张，并排一张 26 键对照；最后出符号设置页「中文14键」。
 * 浅、深、纯黑、纯白，玻璃开关，手机/平板宽度。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FourteenCornerHintsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(out: String, name: String, tag: String) {
        val body = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
        val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
            body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderCornerHints() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var schema by mutableStateOf("pinyin_14jian")
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(240)
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
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
        fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        fun drag(tag: String, pullDown: Boolean, name: String) {
            val key = rule.onNodeWithTag(tag, true)
            val h = key.fetchSemanticsNode().boundsInRoot.height
            key.performTouchInput {
                down(Offset(this.width / 2f, this.height / 2f))
                for (step in 1..6) moveTo(Offset(this.width / 2f, this.height / 2f + (if (pullDown) 1 else -1) * h * 0.12f * step))
            }
            rule.waitForIdle()
            shot(out, name, "layout-body")
            key.performTouchInput { up() }
            rule.waitForIdle()
        }
        for ((w, h) in listOf(360 to 240, 800 to 300)) for ((id, isDark) in listOf("soft_blue" to false,
            "soft_blue" to true, "pure_black" to true, "pure_black" to false)) for (g in listOf(false, true)) {
            val name = "${w}-$id-${if (isDark) "dark" else "light"}-${if (g) "glass" else "solid"}"
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
            rule.runOnIdle { schema = "rime_ice"; width = w; height = h; theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            shot(out, "26-$name", "layout-body")
            KeysConfigHelper.setActiveKeyboardSchema("pinyin_14jian")
            rule.runOnIdle { schema = "pinyin_14jian" }
            rule.waitForIdle()
            shot(out, "14-$name-0-normal", "layout-body")
            // 每个键的上下角标都在键面上。
            for (key in FourteenKeySymbols.keys) {
                assertTrue("14 键 $key 显示上角标", shown(FourteenKeySymbols.up.getValue(key)))
                assertTrue("14 键 $key 显示下角标", shown(FourteenKeySymbols.down.getValue(key)))
            }
            // 默认下拉出上角标：er 下拉是 2；上滑出下角标：m 上滑是 》。
            drag("qwerty-key:er", pullDown = true, name = "14-$name-1-er-pull-down")
            drag("qwerty-key:m", pullDown = false, name = "14-$name-2-m-swipe-up")
        }
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }

    @Test fun renderSymbolEditorFourteenTab() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(400.dp, 900.dp).testTag("editor-body")) {
                    com.kingzcheung.xime.ui.settings.QwertySymbolEditor(onBack = {})
                }
            }
        }
        File(out!!).mkdirs()
        rule.waitForIdle()
        rule.onNodeWithTag("symbol-target:k14", true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("symbol-editor-key:qw", true).assertExists()
        assertTrue(rule.onAllNodesWithText("QW", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("中文14键").assertExists()
        shot(out, "editor-14", "editor-body")
    }
}
