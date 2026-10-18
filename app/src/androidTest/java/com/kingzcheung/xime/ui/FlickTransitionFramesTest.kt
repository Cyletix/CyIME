package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.KeyFlickFeedback
import com.kingzcheung.xime.ui.keyboard.SwipeableIconKeyButton
import com.kingzcheung.xime.ui.keyboard.SwipeableKeyButton
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Frame strips of a pull-down on the delete key and on a letter key with a digit hint. */
class FlickTransitionFramesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deleteAndDigitHintPullDownFrames() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Row(Modifier.background(Color(0xFF121212))) {
                        SwipeableKeyButton("q", onClick = {}, backgroundColor = Color(0xFF3C3C3C),
                            textColor = Color(0xFFE8EAED), modifier = Modifier.size(64.dp, 64.dp).testTag("digit-key"),
                            swipeText = "1", onSwipe = {})
                        SwipeableIconKeyButton(rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace), onClick = {},
                            backgroundColor = Color(0xFF555555), iconColor = Color(0xFFE8EAED),
                            modifier = Modifier.size(96.dp, 64.dp).testTag("delete-key"),
                            swipeText = "清空", onSwipe = {}, followSymbolSwipeDirection = true,
                            upFeedback = KeyFlickFeedback.DELETE, swipeUpLabel = "上滑清空",
                            downFeedback = KeyFlickFeedback.UNDO, swipeDownLabel = "下滑撤回",
                            onSwipeUp = {}, onSwipeDown = {})
                    }
                }
            }
        }
        for (tag in listOf("digit-key", "delete-key")) {
            val frames = mutableListOf<Bitmap>()
            frames += rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            rule.onNodeWithTag(tag).performTouchInput { down(center) }
            repeat(8) {
                rule.onNodeWithTag(tag).performTouchInput { moveBy(Offset(0f, 16f)) }
                rule.waitForIdle()
                frames += rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            }
            rule.onNodeWithTag(tag).performTouchInput { cancel() }
            save(tag, frames)
        }
    }

    private fun save(name: String, frames: List<Bitmap>) {
        val w = frames.first().width; val h = frames.first().height
        val strip = Bitmap.createBitmap(w * frames.size + 8 * (frames.size - 1), h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(strip)
        canvas.drawColor(android.graphics.Color.WHITE)
        frames.forEachIndexed { i, frame -> canvas.drawBitmap(frame, (i * (w + 8)).toFloat(), 0f, null) }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "flick-frames").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(strip.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
