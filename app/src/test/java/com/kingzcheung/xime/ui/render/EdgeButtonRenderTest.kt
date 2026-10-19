package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalEdgeButtonStrength
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
 * 候选栏两端「<」「^」按钮在各主题下的样子，用真实 CandidateBar 代码在电脑上渲染成一张对比图。
 * 只在设置了环境变量 CYIME_RENDER_OUT=<目录> 时出图（平时跳过，不进普通单测）。
 * 玻璃开时候选栏后面垫一张彩色“壁纸”，才看得出半透明。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1340dp-h1000dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EdgeButtonRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderEdgeButtons() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        // 用户手机上开着“候选栏取消按钮”，左边的「<」才会出现。
        com.kingzcheung.xime.settings.SettingsPreferences.setShowCandidateCancelButton(context, true)
        val themes = listOf(
            Triple("soft_blue", false, "柔和蓝·浅"), Triple("soft_blue", true, "柔和蓝·深"),
            Triple("pure_black", false, "黑白·纯白"), Triple("pure_black", true, "黑白·纯黑"),
            Triple("cream_beige", true, "奶油米白·深"),
        )
        val strengths = (System.getenv("CYIME_RENDER_STRENGTHS") ?: "0.45").split(",").map { it.trim().toFloat() }
        val styles = (System.getenv("CYIME_RENDER_STYLES") ?: "CIRCLE").split(",").map {
            com.kingzcheung.xime.ui.keyboard.EdgeButtonStyle.valueOf(it.trim()) }
        val widths = (System.getenv("CYIME_RENDER_WIDTHS") ?: System.getenv("CYIME_RENDER_WIDTH") ?: "420")
            .split(",").map { it.trim().toInt() }
        val themeFilter = System.getenv("CYIME_RENDER_THEMES")?.split(",")?.map { it.trim() }
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (width in widths) for (style in styles) for (strength in strengths) for (glass in listOf(false, true)) for ((id, dark, label) in themes) {
                    if (themeFilter != null && "$id/${if (dark) "dark" else "light"}/${if (glass) "glass" else "solid"}" !in themeFilter) continue
                    val barWidth = width.dp
                    val palette = resolveKeyboardPalette(id, dark)
                    val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${width}dp $label ${if (glass) "玻璃" else "不透明"} $style", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.width(120.dp))
                        Box(Modifier.width(barWidth).height(44.dp)
                            .then(if (glass) Modifier.background(Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground, dark, palette.background, config)) {
                            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config),
                                LocalEdgeButtonStrength provides strength,
                                com.kingzcheung.xime.ui.keyboard.LocalEdgeButtonStyle provides style) {
                                CandidateBar(
                                    // 和手机上一样配 7 个工具按钮：两端胶囊宽度按同一套格子算。
                                    toolbarActions = com.kingzcheung.xime.keyboard.ToolbarButton.entries.take(7).map {
                                        com.kingzcheung.xime.keyboard.ToolbarAction(
                                            com.kingzcheung.xime.keyboard.ToolbarButtonItem.Builtin(it)) {} },
                                    state = CandidateBarState.ChineseCandidates(listOf("这到底", "是什么", "原理", "这到底是什么原理"),
                                        inputText = "zhedaodi", hasMore = true),
                                    visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                        if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent,
                                        palette.selectedText, dark, palette.key),
                                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {},
                                        onShowMoreCandidates = {}),
                                    showPreeditPreview = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // Robolectric 下 captureToImage 等不到 PixelCopy；直接把整个窗口画到位图上。
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "edge-buttons.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
