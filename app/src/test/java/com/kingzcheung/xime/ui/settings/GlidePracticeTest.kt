package com.kingzcheung.xime.ui.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlidePracticeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun practiceIsLocalUntilEnabledAndOnlyUploadsPromptAndGeometry() {
        val sent = mutableListOf<String>()
        rule.setContent { MaterialTheme { GlidePracticeDialog(false, {}, { _, rows -> sent.addAll(rows) }) } }
        val pad = rule.onNodeWithTag("glide-practice-pad")
        fun trace(from: Offset, to: Offset) {
            pad.performTouchInput {
                down(Offset(width * from.x, height * from.y))
                moveTo(Offset(width * to.x, height * to.y))
                up()
            }
        }
        // On this centred QWERTY pad H is in the middle row, O in the first.
        trace(Offset(.60f, .50f), Offset(.85f, .16f))
        rule.onNodeWithText("请滑出 world").assertExists()
        rule.mainClock.advanceTimeBy(2000)
        rule.runOnIdle { assertTrue(sent.isEmpty()) }
        rule.onNodeWithText("自动贡献本次练习").performClick()
        rule.onNodeWithText("暂不贡献").performClick()
        rule.onNodeWithText("已上传 0 条，待传 0 条").assertDoesNotExist()
        rule.onNodeWithText("自动贡献本次练习").performClick()
        System.getenv("CYIME_RENDER_OUT")?.let { dir ->
            val view = rule.onNodeWithText("贡献样本协议").fetchSemanticsNode().root as android.view.View
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { view.draw(Canvas(image)) }
            File(dir, "consent.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onNodeWithText("同意协议").performClick()
        rule.onNodeWithText("已上传 0 条，待传 0 条").assertExists()
        trace(Offset(.15f, .16f), Offset(.30f, .5f))
        rule.onNodeWithText("请滑出 good").assertExists()
        rule.waitUntil(8000) {
            rule.mainClock.advanceTimeBy(100)
            sent.isNotEmpty()
        }
        assertEquals(1, sent.size)
        val sample = JSONObject(sent.single())
        assertEquals("world", sample.getString("target"))
        assertEquals(setOf("target","layout","id","points","aspect"), sample.keys().asSequence().toSet())
        System.getenv("CYIME_RENDER_OUT")?.let { dir ->
            val view = ShadowDialog.getLatestDialog().window!!.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { view.draw(Canvas(image)) }
            File(dir, "practice.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        trace(Offset(.50f, .50f), Offset(.30f, .50f))
        rule.onNodeWithText("自动贡献本次练习").performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.runOnIdle { assertEquals(1, sent.size) }
    }
}
