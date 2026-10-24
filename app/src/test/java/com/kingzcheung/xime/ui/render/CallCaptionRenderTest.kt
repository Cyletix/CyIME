package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.caption.CallLine
import com.kingzcheung.xime.ui.caption.SegmentUi
import com.kingzcheung.xime.ui.caption.speakerColor
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 通话字幕条在竖屏手机宽度下：说话人只是一个色点，字占满一行。只在设置了 CYIME_RENDER_OUT 时出图。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallCaptionRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderCallLines() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val lines = listOf("喂，你那边能听清楚吗？" to 1, "我明天下午三点到，你在小区门口等我就行。" to 1, "好的好的，路上慢点。" to 2)
            .mapIndexed { i, (text, who) -> SegmentUi(i.toLong(), 0L).apply { this.text = text; confirmed = true; reveal.showAll() } to who }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.background(Color(0xFF3A4A5A)).padding(8.dp)) {
                Text("改之后（竖屏 400dp）", color = Color.White, fontSize = 12.sp)
                Column(Modifier.width(384.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .66f))
                    .padding(start = 16.dp, end = 44.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((line, who) in lines) CallLine(line, speakerColor(who), 24 * 1.3f)
                }
            }
        }
        rule.mainClock.advanceTimeBy(5000)
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "call-caption-lines.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
