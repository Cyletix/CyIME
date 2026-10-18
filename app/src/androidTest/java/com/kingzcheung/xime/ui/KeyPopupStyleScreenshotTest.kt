package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.SwipeBubbleOverlay
import com.kingzcheung.xime.ui.keyboard.SwipeState
import com.kingzcheung.xime.ui.keyboard.rememberSwipeBubbleDrawData
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Screenshots of the shared key popup (long press and swipe choice) on the dark theme. */
class KeyPopupStyleScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longPressAndSwipeChoicePopups() {
        val state = mutableStateOf(SwipeState())
        val key = Rect(150f, 60f, 200f, 120f)
        rule.setContent {
            CompositionLocalProvider {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.size(360.dp, 200.dp).background(Color(0xFF121212))) {
                        Box(Modifier.offset(key.left.dp, key.top.dp).size(key.width.dp, key.height.dp)
                            .background(Color(0xFF4A4A4A), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text("a", color = Color(0xFFE8EAED), fontSize = 22.sp)
                        }
                        val density = LocalDensity.current.density
                        val bounds = Rect(key.left * density, key.top * density, key.right * density, key.bottom * density)
                        SwipeBubbleOverlay(rememberSwipeBubbleDrawData(state.value, bounds, Color(0xFF3C3C3C),
                            Color(0xFFE8EAED), keyWidth = bounds.width, keyboardWidth = 360f * density))
                    }
                }
            }
        }
        val cases = listOf(
            "long-press" to SwipeState(isPressed = true, isLongPress = true,
                longPressItems = listOf("a", "A", "@", "á"), selectedLongPressIndex = 1),
            "swipe-choice" to SwipeState(isSwiping = true, swipeText = "@"),
        )
        // The first popup window takes longer to appear; the second pass is the one saved.
        for ((name, value) in cases + cases) {
            rule.runOnIdle { state.value = value }
            rule.waitForIdle()
            // The popup is a separate window; give it a frame to reach the screen.
            Thread.sleep(500)
            save(name)
        }
    }

    private fun save(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "key-popup-style").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
