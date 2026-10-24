package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import com.kingzcheung.xime.ui.keyboard.CANDIDATE_BAR_BASE_HEIGHT_DP
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutScreen
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.PreeditLabel
import com.kingzcheung.xime.ui.keyboard.ScaledCandidateBar
import com.kingzcheung.xime.ui.keyboard.candidateBarScale
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 候选栏随键盘尺寸半速放大：手机/平板、几档键盘尺寸、九键与 26 键、深浅色与玻璃，
 * 每档出改前（固定 44dp）与改后两张，拼音浮块画在候选栏上方。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1400dp-h1100dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateBarScaleRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Size(val name: String, val width: Int, val height: Int, val screenWidth: Int, val screenHeight: Int)

    @Test fun renderCandidateBarScale() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        // 九键要靠 keyboard.t9.schemas 绑定才认得出来。
        val custom = File(app.filesDir, "rime/xime.custom.yaml").apply { parentFile?.mkdirs() }
        custom.writeText("keyboard:\n  t9:\n    schemas: [t9_pinyin]\n")
        KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
        // 真控制器要加载 Rime 原生库；画键盘只读它的状态，用空壳代替。
        val t9 = org.mockito.kotlin.mock<com.kingzcheung.xime.rime.T9InputController>(defaultAnswer = org.mockito.Mockito.RETURNS_MOCKS)
        var size by mutableStateOf(SIZES.first())
        var theme by mutableStateOf("soft_blue")
        var glass by mutableStateOf(false)
        var schema by mutableStateOf("t9_pinyin")
        var scaled by mutableStateOf(true)
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val dark = theme == "pure_black"
            val palette = resolveKeyboardPalette(theme, dark)
            val config = FrostedGlassConfig.defaults(dark, enabled = glass)
            val scale = if (scaled) candidateBarScale(size.width.toFloat(), size.height.toFloat()) else 1f
            val bubbleArea = (CANDIDATE_BAR_BASE_HEIGHT_DP * 1.25f).dp
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (size.screenWidth > size.screenHeight) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = size.screenWidth; screenHeightDp = size.screenHeight
                smallestScreenWidthDp = minOf(size.screenWidth, size.screenHeight)
            }
            val visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                KeyboardThemes.getDividerColor(theme, dark), palette.accent, palette.selectedText, dark, palette.key)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalKeyboardExplicitWidth provides true,
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Column(Modifier.requiredSize(size.width.dp, size.height.dp + bubbleArea).testTag("scale-body")
                        .background(if (dark) Color(0xFF303030) else Color(0xFFEDEDED))) {
                        // 浮块真机上是贴着候选栏上沿的弹窗，这里按同样的尺寸画在上方。
                        Box(Modifier.fillMaxWidth().height(bubbleArea), contentAlignment = androidx.compose.ui.Alignment.BottomStart) {
                            ScaledCandidateBar(scale) {
                                PreeditLabel(if (schema == "t9_pinyin") "ni hao" else "ni'hao", visuals, null,
                                    Modifier.padding(start = 4.dp).widthIn(max = 300.dp).height(44.dp).padding(vertical = 6.dp))
                            }
                        }
                        Column(Modifier.requiredSize(size.width.dp, size.height.dp)
                            .then(if (glass) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E7D32),
                                Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark,
                                palette.background, config)) {
                            ScaledCandidateBar(scale) {
                                CandidateBar(
                                    state = CandidateBarState.ChineseCandidates(listOf("你好", "拟好", "你", "妮", "泥", "逆", "腻", "倪", "尼", "拟"),
                                        inputText = "nihao", hasMore = true),
                                    visuals = visuals,
                                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {}),
                                    showPreeditPreview = false, modifier = Modifier.fillMaxWidth())
                            }
                            val keyboardState by vm.keyboardState.collectAsState()
                            KeyboardLayoutScreen(keyboardState,
                                KeyboardUiState(currentSchemaId = schema, themeId = theme, isDarkTheme = dark),
                                vm, callbacks, onKeyPress = {}, modifier = Modifier.fillMaxWidth().weight(1f),
                                t9Controller = t9)
                        }
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("scale-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun show(s: Size, t: String, g: Boolean, layout: String, after: Boolean) {
            KeysConfigHelper.setActiveKeyboardSchema(layout)
            rule.runOnIdle { vm.resetKeyboard(false, layout); size = s; theme = t; glass = g; schema = layout; scaled = after; revision++ }
            rule.waitForIdle()
            val key = if (layout == "t9_pinyin") "t9" else "26"
            shot("${s.name}-$key-$t-${if (g) "glass" else "solid"}-${if (after) "after" else "before"}")
        }
        for (s in SIZES) for (layout in listOf("t9_pinyin", "rime_ice")) for (after in listOf(false, true))
            show(s, "soft_blue", false, layout, after)
        for (s in SIZES.filter { it.name in setOf("phone-tall", "tablet-portrait", "tablet-landscape-tall") })
            for ((t, g) in listOf("pure_black" to false, "pure_black" to true, "transparent_glass" to true, "soft_blue" to true))
                show(s, t, g, "t9_pinyin", true)
        custom.delete()
    }

    private companion object {
        // 手机 411×882、平板 824×1318（SM-X800）；默认 35%/25% 高度，加上拉高过的几档与悬浮窄卡片。
        val SIZES = listOf(
            Size("phone-default", 411, 308, 411, 882),
            Size("phone-tall", 411, 397, 411, 882),
            // 用户 10-24 截图里的平板竖屏九键：面板约 435dp 高、行高约 97dp。
            Size("tablet-portrait", 824, 435, 824, 1318),
            Size("tablet-landscape", 1318, 330, 1318, 824),
            Size("tablet-landscape-mid", 1318, 390, 1318, 824),
            Size("tablet-landscape-tall", 1318, 450, 1318, 824),
            Size("tablet-floating", 520, 420, 824, 1318),
        )
    }
}
