package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.caption.CallCaptionBar
import com.kingzcheung.xime.ui.caption.CallRoute
import com.kingzcheung.xime.ui.caption.CaptionController
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 后台浮窗：字幕页离开后在别的应用上方显示的字幕条（竖屏手机）。只在设置了 CYIME_RENDER_OUT 时出图。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionFloatRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderFloatingBar() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val controller = CaptionController(ApplicationProvider.getApplicationContext<Context>())
        controller.speakers.add(com.kingzcheung.xime.ui.caption.SpeakerUi(1, 1))
        controller.speakers.add(com.kingzcheung.xime.ui.caption.SpeakerUi(2, 2))
        listOf("孙子周末回来吃饭吗？" to 1, "回来的，周六中午到，想吃您做的红烧肉。" to 2, "好好好，我明天就去买肉。" to 1).forEachIndexed { i, (text, who) ->
            controller.segments.add(com.kingzcheung.xime.ui.caption.SegmentUi(i.toLong(), 0L).apply {
                this.text = text; confirmed = true; reveal.showAll(); speakerId = who })
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            // Another app behind it (a chat list), the bar at the bottom like the real overlay.
            Box(Modifier.width(400.dp).height(800.dp).background(Brush.verticalGradient(listOf(Color(0xFFEDEDED), Color(0xFFD9E3EA))))) {
                Column(Modifier.padding(16.dp)) { repeat(8) { Text("其他应用的内容 $it", fontSize = 18.sp, modifier = Modifier.padding(vertical = 14.dp)) } }
                Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 80.dp).fillMaxWidth()) {
                    CallCaptionBar(controller, CallRoute.Speaker, onDrag = {}, onClose = {}, floating = true, onOpen = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "caption-float.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        controller.release()
    }
}
