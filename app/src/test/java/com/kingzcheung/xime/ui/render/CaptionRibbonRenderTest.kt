package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.caption.SoundRibbon
import com.kingzcheung.xime.ui.caption.StatusRow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 字幕状态行的声音丝带：轻声、正常说话、大声三档要一眼看出大小。只在设置了 CYIME_RENDER_OUT 时出图。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h500dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionRibbonRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderLoudness() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        // Spectrum band values (0..1 over the -66..-30 dB window) for three voices.
        val shape = floatArrayOf(.55f, .9f, 1f, .8f, .6f, .45f, .3f, .2f)
        val levels = listOf("轻声" to .28f, "正常说话" to .5f, "大声" to .78f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF1B1D22)).padding(8.dp)) {
                for ((label, peak) in levels) {
                    Text(label, fontSize = 13.sp, color = Color.White)
                    Column(Modifier.width(400.dp).padding(vertical = 6.dp)) {
                        StatusRow("中文", Color(0xFFAFC6FF), "正在听", onPickLanguage = {}) { h ->
                            SoundRibbon(FloatArray(8) { shape[it] * peak }, true, Color(0xFFAFC6FF), Modifier.fillMaxWidth().height(h))
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "caption-ribbon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
