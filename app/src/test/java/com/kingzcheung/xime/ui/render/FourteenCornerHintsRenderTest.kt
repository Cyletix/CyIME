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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.QwertySwipeSymbols
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
 * 14 键角标和 26 键同一字母一样：双字母键左右两半各画自己字母的上下角标（er 键左半是 e 的、右半是 r 的），
 * 按在右半下滑，方向盘里是 r 的角标。每组同时出一张 26 键对照图。
 * 浅、深、纯黑、纯白，玻璃开关，手机/平板宽度。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FourteenCornerHintsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

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
        fun shot(name: String) {
            val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for ((w, h) in listOf(360 to 240, 800 to 300)) for ((id, isDark) in listOf("soft_blue" to false,
            "soft_blue" to true, "pure_black" to true, "pure_black" to false)) for (g in listOf(false, true)) {
            val name = "${w}-$id-${if (isDark) "dark" else "light"}-${if (g) "glass" else "solid"}"
            KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
            rule.runOnIdle { schema = "rime_ice"; width = w; height = h; theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            shot("26-$name")
            KeysConfigHelper.setActiveKeyboardSchema("pinyin_14jian")
            rule.runOnIdle { schema = "pinyin_14jian" }
            rule.waitForIdle()
            shot("14-$name-0-normal")
            // 两个字母的上角标都在键面上（er：e 的 3、r 的 4），不只画左边那个。
            for (letter in listOf("e", "r", "w", "q")) {
                assertTrue("14 键显示 $letter 的上角标", shown(QwertySwipeSymbols.up.getValue(letter)))
            }
            val er = rule.onNodeWithTag("qwerty-key:er", true).fetchSemanticsNode().boundsInRoot
            val key = rule.onNodeWithTag("qwerty-key:er", true)
            // 按在 er 的右半，往下滑：方向盘应是 r 的角标。
            key.performTouchInput {
                down(Offset(this.width * 0.75f, this.height / 2f))
                for (step in 1..6) moveTo(Offset(this.width * 0.75f, this.height / 2f + er.height * 0.12f * step))
            }
            rule.waitForIdle()
            shot("14-$name-1-right-half-down")
            key.performTouchInput { up() }
            rule.waitForIdle()
            key.performTouchInput {
                down(Offset(this.width * 0.25f, this.height / 2f))
                for (step in 1..6) moveTo(Offset(this.width * 0.25f, this.height / 2f + er.height * 0.12f * step))
            }
            rule.waitForIdle()
            shot("14-$name-2-left-half-down")
            key.performTouchInput { up() }
            rule.waitForIdle()
        }
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }
}
