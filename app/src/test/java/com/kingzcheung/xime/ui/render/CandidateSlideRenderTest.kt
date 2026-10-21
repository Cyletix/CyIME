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
import androidx.compose.runtime.mutableStateOf
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
 * 候选换词（打字）后第 0/30/60/120ms 应该一动不动；换选中项时高亮底色滑过去的关键帧。
 * 用真实 CandidateBar 代码在电脑上渲染。只在设了环境变量 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w600dp-h420dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateSlideRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderSlideFrames() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val themes = listOf(
            Triple("soft_blue", false, false), Triple("soft_blue", true, false),
            Triple("pure_black", true, true), Triple("soft_blue", false, true),
        )
        val first = listOf("这到底", "这道题", "折倒", "这到底是什么原理")
        val second = listOf("是什么", "什么", "是", "是什么原理")
        val words = mutableStateOf(first)
        val focus = mutableStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((id, dark, glass) in themes) {
                    val palette = resolveKeyboardPalette(id, dark)
                    val config = FrostedGlassConfig.defaults(dark, enabled = glass)
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("$id ${if (dark) "深" else "浅"} ${if (glass) "玻璃" else "不透明"}", fontSize = 10.sp,
                            color = Color.White, modifier = Modifier.width(110.dp))
                        Box(Modifier.width(460.dp).height(44.dp)
                            .then(if (glass) Modifier.background(Brush.linearGradient(
                                listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF1565C0)))) else Modifier)
                            .keyboardBackground(KeyboardThemes.getRenderingScheme(id).keyboardBackground, dark, palette.background, config)) {
                            CompositionLocalProvider(LocalKeyboardPalette provides palette,
                                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                                CandidateBar(
                                    state = CandidateBarState.ChineseCandidates(words.value, inputText = "zhedaodi", hasMore = true,
                                        highlightIndex = focus.value),
                                    visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                        if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent,
                                        palette.selectedText, dark, palette.key),
                                    callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {}),
                                    showPreeditPreview = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { words.value = second }
        File(out!!).mkdirs()
        // 先走一帧让新候选上屏，之后按动画开始后的时间截图。
        rule.mainClock.advanceTimeByFrame()
        var elapsed = 0L
        for (t in listOf(0L, 30L, 60L, 120L)) {
            rule.mainClock.advanceTimeBy(t - elapsed)
            elapsed = t
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "slide-${t}ms.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // 换选中项（Tab、方向键）：高亮底色从「是什么」滑到「什么」，再滑到「是什么原理」。
        for ((step, index) in listOf(1, 3).withIndex()) {
            rule.mainClock.advanceTimeBy(1000)
            rule.runOnIdle { focus.value = index }
            rule.mainClock.advanceTimeByFrame()
            elapsed = 0L
            for (t in listOf(0L, 60L, 120L, 240L)) {
                rule.mainClock.advanceTimeBy(t - elapsed)
                elapsed = t
                val root = rule.activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
                File(out, "focus${step + 1}-${t}ms.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
