package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardCandidateBar
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
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
 * 实体键盘浮条：拼音里的光标颜色，以及点「⌄」展开候选的过渡帧（0/80/160/240ms）。
 * 浅色、深色、纯黑、纯白，玻璃开关。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1400dp-h900dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HardwareStripRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderStrip() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = listOf(
            Triple("soft_blue", false, false), Triple("soft_blue", true, false),
            Triple("pure_black", true, false), Triple("pure_black", false, false),
            Triple("pure_black", true, true), Triple("soft_blue", false, true),
        )
        val words = listOf("这个逻辑不对", "这个逻辑", "这个", "这歌", "最合格", "这个落", "折", "者", "这", "哲")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row {
                for (half in listOf(themes.take(3), themes.drop(3))) Column {
                    for ((id, dark, glass) in half) {
                        val palette = resolveKeyboardPalette(id, dark)
                        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                        Text("$id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"}", fontSize = 10.sp,
                            color = Color.White, modifier = Modifier.background(Color(0xFF606060)).padding(2.dp))
                        Box(Modifier.width(680.dp).height(260.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFF1565C0))))) {
                            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                                HardwareKeyboardCandidateBar(
                                    inputText = "zhegeluojibudui", preeditText = "zhe ge luo ji bu d|ui",
                                    candidates = words, hasNextPage = true, hasPrevPage = false, cursorAnchor = null,
                                    highlightIndex = 0, cardBackgroundColor = palette.background.copy(alpha = 1f),
                                    candidateTextColor = palette.candidateText, activeColor = palette.accent,
                                    selectedTextColor = palette.selectedText, preeditBackgroundColor = palette.key,
                                    surface = { Modifier.keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground,
                                        dark, palette.background, frostedGlass = config, translucentSurface = true) },
                                    frostedGlass = config,
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        File(out!!).mkdirs()
        fun shot(name: String) {
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("strip-collapsed")
        val expands = rule.onAllNodesWithTag("hardware-candidate-expand")
        for (i in 0 until expands.fetchSemanticsNodes().size) expands[i].performClick()
        var elapsed = 0L
        for (t in listOf(0L, 80L, 160L, 240L)) {
            rule.mainClock.advanceTimeBy(t - elapsed + if (t == 0L) 16 else 0)
            elapsed = t + if (t == 0L) 16 else 0
            shot("strip-expand-${t}ms")
        }
    }
}
