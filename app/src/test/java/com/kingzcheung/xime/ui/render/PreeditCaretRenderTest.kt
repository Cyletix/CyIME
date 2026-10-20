package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.PreeditLabel
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 拼音条里的编辑光标“|”在每个主题浅色/深色下的样子。只在设了 CYIME_RENDER_OUT 时出图。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1400dp-h1600dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreeditCaretRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Test fun renderCaret() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val ids = KeyboardThemes.themes.map { it.id }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FlowRow(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for (id in ids) for (dark in listOf(false, true)) {
                    val palette = resolveKeyboardPalette(id, dark)
                    Column(Modifier.width(340.dp).padding(2.dp)) {
                        Text("$id ${if (dark) "深" else "浅"}", fontSize = 10.sp, color = Color.White)
                        Box(Modifier.background(palette.background).padding(4.dp)) {
                            PreeditLabel("zhe ge luo ji bu d|ui", CandidateBarVisuals(Color.Transparent,
                                palette.candidateText, Color.Gray, palette.accent, palette.selectedText, dark, palette.key))
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "preedit-caret.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 闪烁时移动光标：灭着的时候移一下仍是灭的，节拍不从头来。 */
    @Test fun blinkKeepsRhythmWhileMoving() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val text = androidx.compose.runtime.mutableStateOf("zhe ge luo ji bu d|ui")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF808080)).padding(4.dp)) {
                for ((id, dark) in listOf("soft_blue" to false, "pure_black" to true, "sakura_pink" to false)) {
                    val palette = resolveKeyboardPalette(id, dark)
                    Box(Modifier.background(palette.background).padding(4.dp)) {
                        PreeditLabel(text.value, CandidateBarVisuals(Color.Transparent,
                            palette.candidateText, Color.Gray, palette.accent, palette.selectedText, dark, palette.key))
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.mainClock.advanceTimeBy(200); shot("blink-1-on")
        rule.mainClock.advanceTimeBy(500); shot("blink-2-off")
        text.value = "zhe ge luo ji b|u dui"
        rule.mainClock.advanceTimeBy(32); shot("blink-3-moved-still-off")
        text.value = "zhe ge luo j|i bu dui"
        rule.mainClock.advanceTimeBy(400); shot("blink-4-on-again")
    }
}
