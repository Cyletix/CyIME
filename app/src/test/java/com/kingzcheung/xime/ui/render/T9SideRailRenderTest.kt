package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.rime.T9InputController
import org.mockito.kotlin.doReturn
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
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
 * 九键左栏（用户 10-30：只有四个标点、滑不动、找不到在哪改）：默认 16 个常用标点，右缘滚动条，
 * 列尾「编辑」。柔和蓝 / 黑白 / iOS 渐变 × 深浅色 × 毛玻璃，各出列首和滑到底两张。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T9SideRailRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderRailAcrossThemes() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        // 九键只画键盘，不碰 Rime：控制器用桩，左栏停在空闲态的符号列。
        val t9 = org.mockito.kotlin.mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        rule.setContent {
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            XimeTheme(darkTheme = dark, themeId = theme) {
                CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                    Box(Modifier.requiredSize(360.dp, 250.dp).testTag("rail-body")
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground,
                            dark, palette.background, config)) {
                        KeyboardLayoutScreen(keyboardState = KeyboardLayoutState.T9Pinyin,
                            uiState = KeyboardUiState(currentSchemaId = "t9_pinyin", themeId = theme, isDarkTheme = dark),
                            viewModel = vm, callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            onKeyPress = {}, modifier = Modifier.requiredSize(360.dp, 250.dp), t9Controller = t9)
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("rail-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (t in listOf("soft_blue", "pure_black", "transparent_glass")) for (d in listOf(false, true)) for (g in listOf(false, true)) {
            KeysConfigHelper.setActiveKeyboardSchema("t9_pinyin")
            com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(app).edit().putString("keyboard_theme", t).commit()
            rule.runOnIdle { vm.resetKeyboard(false, "t9_pinyin"); theme = t; dark = d; glass = g }
            rule.waitForIdle()
            rule.onNodeWithTag("t9-pinyin-options", true).performScrollToIndex(0)
            rule.waitForIdle()
            val name = "$t-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}"
            shot("$name-top")
            rule.onNodeWithTag("t9-pinyin-options", true).performScrollToIndex(16)
            rule.waitForIdle()
            assertTrue(rule.onNodeWithTag("t9-side-symbols-edit", true).fetchSemanticsNode().boundsInRoot.height > 0f)
            shot("$name-end")
        }
    }
}
