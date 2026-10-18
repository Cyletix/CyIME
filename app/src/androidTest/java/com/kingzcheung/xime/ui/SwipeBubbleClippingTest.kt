package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SwipeBubbleClippingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun topRowChoicesEscapeRoundedCardAtPhoneAndTabletWidths() {
        val width = mutableIntStateOf(360)
        val keyX = mutableFloatStateOf(8f)
        val visible = mutableStateOf(true)
        rule.setContent {
            // 934dp at density 1 fits the actual 1080px host; assert this below.
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.size(width.intValue.dp, 260.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF203030)).testTag("bubble-card")) {
                        val bounds = Rect(keyX.floatValue, 8f, keyX.floatValue + 70f, 88f)
                        val data = rememberSwipeBubbleDrawData(
                            if (visible.value) SwipeState(isPressed = true, isLongPress = true,
                                longPressItems = listOf("q", "Q"), selectedLongPressIndex = 1) else SwipeState(),
                            bounds, Color.DarkGray, Color.White, keyWidth = bounds.width,
                            keyboardWidth = width.intValue.toFloat())
                        SwipeBubbleOverlay(data)
                    }
                }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "key-popup-clipping").apply { mkdirs() }
        for (w in listOf(360, 934)) for (x in listOf(8f, w / 2f - 35f, w - 78f)) {
            rule.runOnIdle { width.intValue = w; keyX.floatValue = x }
            val card = rule.onNodeWithTag("bubble-card").fetchSemanticsNode()
            assertEquals("fixture must not silently shrink", w, card.size.width)
            val popup = rule.onNodeWithTag("key-choice-popup")
            val node = popup.fetchSemanticsNode()
            assertTrue("choice body must remain above the card", node.positionOnScreen.y + 20 < card.positionOnScreen.y)
            assertTrue(node.positionOnScreen.x >= 0 && node.positionOnScreen.y >= 0)
            assertTrue(node.size.height > 100)
            File(directory, "popup-$w-${x.toInt()}.png").outputStream().use {
                popup.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        rule.runOnIdle { visible.value = false }
        rule.onNodeWithTag("key-choice-popup").assertDoesNotExist()
    }
}
