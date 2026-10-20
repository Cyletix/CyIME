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
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "preedit-caret.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
