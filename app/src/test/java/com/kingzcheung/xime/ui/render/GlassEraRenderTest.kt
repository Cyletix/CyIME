package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.kingzcheung.xime.rime.T9InputController
import org.mockito.kotlin.doReturn
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 玻璃效果前后对比：手机竖屏，九键 / 26 键 × 深浅色 × 深色聊天页 / 浅色网页两种宿主。
 * 宿主近似系统模糊（10dp）后垫在键盘后面，等同真机的 ImeGlassBlurWindow。
 * 主题：iOS 渐变、黑白、柔和蓝；布局：九键、26 键、数字。
 * 只在设了 CYIME_RENDER_OUT 时出图；CYIME_RENDER_STAGE 给文件名前缀（默认 current）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassEraRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderGlassEras() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val stage = System.getenv("CYIME_RENDER_STAGE")?.takeIf { it.isNotBlank() } ?: "current"
        val app = ApplicationProvider.getApplicationContext<Application>()
        val custom = File(app.filesDir, "rime/xime.custom.yaml").apply { parentFile?.mkdirs() }
        fun reload() { KeysConfigHelper.loadConfig(app); KeyboardThemes.reload(app) }
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        var theme by mutableStateOf("transparent_glass")
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(true)
        var hostDark by mutableStateOf(true)
        var schema by mutableStateOf("t9_pinyin")
        var layout by mutableStateOf("t9")
        // 九键只画键盘，不碰 Rime：控制器用桩，左栏空闲。
        val t9 = org.mockito.kotlin.mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        var revision by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val palette = resolveKeyboardPalette(theme, dark)
            // 与真机相同：过透时按主题自动加深（KeyboardInputPreferences 里的 legibleForTheme）。
            val config = FrostedGlassConfig.defaults(dark, enabled = glass).legibleForTheme(theme, dark)
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true, LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                MaterialTheme {
                    Column(Modifier.requiredSize(400.dp, 400.dp).testTag("era-body")) {
                        // 键盘上方一条不模糊的宿主，看清键盘后面是什么页面。
                        Box(Modifier.fillMaxWidth().height(60.dp).clipToBounds()) { Host(hostDark, Modifier.offset(y = (-200).dp)) }
                        Box(Modifier.fillMaxWidth().height(340.dp).clipToBounds()) {
                            if (glass) Host(hostDark, Modifier.offset(y = (-260).dp), soft = true)
                            Column(Modifier.requiredSize(400.dp, 340.dp)
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
                                        "num" -> KeyboardLayoutState.Number
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
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val body = rule.onNodeWithTag("era-body", true).fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
            val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
                body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
            File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val prefs = SettingsPreferences.getPrefsPublic(app)
        val eras = listOf(stage)
        for (era in eras) {
            custom.delete(); reload()
            val themes = listOf("transparent_glass", "pure_black", "soft_blue")
            for (t in themes) for (l in listOf("t9", "26", "num")) for (d in listOf(true, false)) for (h in listOf(true, false)) {
                val s = if (l == "26") "rime_ice" else "t9_pinyin"
                KeysConfigHelper.setActiveKeyboardSchema(s)
                prefs.edit().putString("keyboard_theme", t).commit()
                rule.runOnIdle {
                    vm.resetKeyboard(false, s); theme = t; schema = s; layout = l; dark = d; hostDark = h
                    glass = true; revision++
                }
                rule.waitForIdle()
                shot("$era-$t-$l-${if (d) "dark" else "light"}-host${if (h) "dark" else "light"}")
            }
        }
        custom.delete()
    }

    /** 深色聊天页（像截图里的 Claude）或浅色网页：大块文字条、气泡和一张彩色图。 */
    @Composable
    private fun Host(dark: Boolean, modifier: Modifier, soft: Boolean = false) {
        val page = if (dark) Color(0xFF1A1A1A) else Color(0xFFF7F7F7)
        val bubble = if (dark) Color(0xFF2B2B33) else Color(0xFFE3ECFA)
        val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF222222)
        // Robolectric 画不出 RenderEffect 模糊；soft 时把每块内容换成同色、低不透明度的大圆角色块，近似 10dp 系统模糊。
        val a = if (soft) .55f else 1f
        val r = if (soft) 22 else 0
        Box(modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(400.dp, 700.dp).background(page)) {
            for (i in 0 until 14) {
                val y = 30 + i * 46
                val bubbleRow = i % 4 == 1
                if (bubbleRow) Box(Modifier.offset(150.dp, y.dp).size(230.dp, 40.dp)
                    .background(bubble.copy(alpha = a), RoundedCornerShape((18 + r).dp)))
                Box(Modifier.offset((if (bubbleRow) 166 else 20).dp, (y + 12).dp)
                    .size((if (bubbleRow) 190 else 120 + (i * 37) % 220).dp, 14.dp)
                    .background(text.copy(alpha = if (soft) .25f else 1f), RoundedCornerShape((4 + r / 3).dp)))
            }
            Box(Modifier.offset(30.dp, 300.dp).size(200.dp, 120.dp).background(Color(0xFFE5753C).copy(alpha = a), RoundedCornerShape((12 + r).dp)))
            Box(Modifier.offset(240.dp, 340.dp).size(130.dp, 160.dp).background(Color(0xFF3C8DE5).copy(alpha = a), RoundedCornerShape((12 + r).dp)))
        }
    }
}
