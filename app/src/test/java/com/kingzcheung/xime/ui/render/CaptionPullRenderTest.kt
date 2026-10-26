package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.caption.NewSegmentPull
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 上拉很长一段开始新一段：拉到一半出进度条（出图），短拉不触发、拉满松手才触发。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionPullRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun shortPullDoesNothingLongPullStartsNewSegment() {
        var started = 0
        rule.setContent {
            NewSegmentPull(Modifier.width(400.dp).height(800.dp).background(Color(0xFF1B1D22)), onNewSegment = { started++ }) {
                LazyColumn(Modifier.fillMaxSize()) { items(5) { Text("第 $it 句字幕", fontSize = 22.sp, color = Color.White) } }
            }
        }
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 300f, durationMillis = 600) }
        rule.waitForIdle()
        assertEquals("a short pull starts nothing", 0, started)
        rule.onRoot().performTouchInput { swipeUp(startY = bottom - 10f, endY = 10f, durationMillis = 900) }
        rule.waitForIdle()
        assertEquals("a long pull starts a new segment", 1, started)
    }

    @Test fun renderHalfwayBar() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        rule.setContent {
            NewSegmentPull(Modifier.width(400.dp).height(800.dp).background(Color(0xFF1B1D22)), onNewSegment = {}) {
                LazyColumn(Modifier.fillMaxSize()) { items(5) { Text("第 $it 句字幕", fontSize = 22.sp, color = Color.White) } }
            }
        }
        rule.onRoot().performTouchInput { down(center.copy(y = bottom - 10f)); moveBy(androidx.compose.ui.geometry.Offset(0f, -500f)) }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out!!).mkdirs()
        File(out, "caption-pull.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
