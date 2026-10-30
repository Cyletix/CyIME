package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.legibleForTheme
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
import org.mockito.kotlin.doReturn
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 最小高度下图标键的文字（清空、重输）与图标是否重叠。
 * 几何：九键 / 26 / 14 / 笔画 / 数字 / 常用符号 × 手机、平板 × 竖横屏 × 固定下限 228dp、悬浮绝对下限 160dp
 * × 实色 / 玻璃 × 字号 1.0 / 1.3；配色：全部主题 × 深浅色 × 实色 / 玻璃（九键、26 键，手机竖屏下限）。
 * 只在设了 CYIME_RENDER_OUT 时出图；CYIME_RENDER_STAGE 给文件名前缀（默认 current）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1300dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconHintFitRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Screen(val name: String, val width: Int, val screenW: Int, val screenH: Int)

    @Test fun renderMinimumHeight() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val stage = System.getenv("CYIME_RENDER_STAGE")?.takeIf { it.isNotBlank() } ?: "current"
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val t9 = org.mockito.kotlin.mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        var theme by mutableStateOf("soft_blue")
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var schema by mutableStateOf("t9_pinyin")
        var layout by mutableStateOf("t9")
        var screen by mutableStateOf(Screen("phone-portrait", 400, 400, 860))
        var height by mutableStateOf(228)
        var fontScale by mutableStateOf(1f)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (screen.screenW > screen.screenH) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = screen.screenW; screenHeightDp = screen.screenH
                smallestScreenWidthDp = minOf(screen.screenW, screen.screenH)
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalKeyboardExplicitWidth provides true,
                LocalDensity provides Density(density.density, fontScale),
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Column(Modifier.requiredSize(screen.width.dp, height.dp).testTag("fit-body")
                        .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                            Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                            palette.background, config)) {
                        CandidateBar(
                            state = CandidateBarState.ChineseCandidates(listOf("你好", "拟好", "你", "妮", "泥"),
                                inputText = "nihao", hasMore = true),
                            visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                KeyboardThemes.getDividerColor(theme, dark), palette.accent, palette.selectedText, dark,
                                palette.key),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {}),
                            showPreeditPreview = false, modifier = Modifier.fillMaxWidth().height(44.dp))
                        KeyboardLayoutScreen(
                            keyboardState = when (layout) {
                                "t9" -> KeyboardLayoutState.T9Pinyin
                                "stroke" -> KeyboardLayoutState.Stroke
                                "num" -> KeyboardLayoutState.Number
                                "sym" -> KeyboardLayoutState.CommonSymbol
                                else -> KeyboardLayoutState.Chinese
                            },
                            uiState = KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark),
                            viewModel = vm,
                            callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            onKeyPress = {}, modifier = Modifier.fillMaxWidth().weight(1f), t9Controller = t9)
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("fit-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val prefs = SettingsPreferences.getPrefsPublic(app)
        fun show(l: String, t: String, d: Boolean, g: Boolean, s: Screen, h: Int, fs: Float) {
            val id = when (l) { "26" -> "rime_ice"; "14" -> "pinyin_14jian"; "stroke" -> "stroke"; else -> "t9_pinyin" }
            KeysConfigHelper.setActiveKeyboardSchema(id)
            prefs.edit().putString("keyboard_theme", t).commit()
            rule.runOnIdle {
                vm.resetKeyboard(false, id); theme = t; dark = d; glass = g; schema = id; layout = l
                screen = s; height = h; fontScale = fs; revision++
            }
            rule.waitForIdle()
        }
        val layouts = listOf("t9", "26", "14", "stroke", "num", "sym")
        val screens = listOf(Screen("phone-portrait", 400, 400, 860), Screen("phone-landscape", 860, 860, 400),
            Screen("tablet-portrait", 800, 800, 1280), Screen("tablet-landscape", 1280, 1280, 800))
        val heights = (System.getenv("CYIME_RENDER_HEIGHTS")?.split(',')?.map { it.trim().toInt() }) ?: listOf(228, 160)
        for (l in layouts) for (s in screens) for (h in heights) for (g in listOf(false, true))
            for (fs in listOf(1f, 1.3f)) {
                show(l, "soft_blue", false, g, s, h, fs)
                shot("$stage-geo-$l-${s.name}-h$h-${if (g) "glass" else "solid"}-fs${(fs * 10).toInt()}")
            }
        for (t in KeyboardThemes.themes.map { it.id }.distinct()) for (l in listOf("t9", "26"))
            for (d in listOf(false, true)) for (g in listOf(false, true)) {
                show(l, t, d, g, screens[0], heights.first(), 1f)
                shot("$stage-theme-$t-$l-${if (d) "dark" else "light"}-${if (g) "glass" else "solid"}")
            }
    }
}
