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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 14 键按住 Shift 拖到字母上：双字母键拆回单字母键，显示手指停的那个字母；松手恢复 14 键。
 * 另外 jk、l、m 下滑时的样子（应该都是方向盘）。
 * 浅、深、纯黑、纯白，玻璃开关，手机/平板宽度。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FourteenShiftDragRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderShiftDrag() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        KeysConfigHelper.setActiveKeyboardSchema("pinyin_14jian")
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
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = "pinyin_14jian", themeId = theme, isDarkTheme = dark),
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
        fun tagExists(tag: String) = runCatching { rule.onNodeWithTag(tag, true).fetchSemanticsNode() }.isSuccess
        for ((w, h) in listOf(360 to 240, 800 to 300)) for ((id, isDark) in listOf("soft_blue" to false,
            "soft_blue" to true, "pure_black" to true, "pure_black" to false)) for (g in listOf(false, true)) {
            rule.runOnIdle { width = w; height = h; theme = id; dark = isDark; glass = g }
            rule.waitForIdle()
            val name = "14-${w}-$id-${if (isDark) "dark" else "light"}-${if (g) "glass" else "solid"}"
            shot("$name-0-normal")
            assertTrue("14 键平时是双字母键", tagExists("qwerty-key:er"))
            val shift = rule.onNodeWithTag("shift-key", true).fetchSemanticsNode().boundsInRoot
            val er = rule.onNodeWithTag("qwerty-key:er", true).fetchSemanticsNode().boundsInRoot
            // 手指停在 er 键的右半：拆开后应该是独立的 r 键。
            val target = Offset(er.left + er.width * 0.75f, er.center.y)
            rule.onNodeWithTag("shift-key", true).performTouchInput {
                down(center)
                val origin = Offset(shift.left, shift.top)
                for (step in 1..8) moveTo(center + (target - origin - center) * (step / 8f))
            }
            rule.waitForIdle()
            shot("$name-1-dragging")
            assertTrue("拖动时 r 是独立字母键", tagExists("qwerty-key:r") && !tagExists("qwerty-key:er"))
            rule.onNodeWithTag("shift-key", true).performTouchInput { up() }
            rule.waitForIdle()
            assertTrue("松手恢复双字母键", tagExists("qwerty-key:er"))
            // 下滑：双字母键 jk 和落单的 l、m 都应该出同一个方向盘。
            for (key in listOf("jk", "l", "m")) {
                rule.onNodeWithTag("qwerty-key:$key", true).performTouchInput {
                    down(center)
                    for (step in 1..6) moveTo(center + Offset(0f, height * 0.12f * step))
                }
                rule.waitForIdle()
                shot("$name-2-down-$key")
                rule.onNodeWithTag("qwerty-key:$key", true).performTouchInput { up() }
                rule.waitForIdle()
            }
        }
    }
}
