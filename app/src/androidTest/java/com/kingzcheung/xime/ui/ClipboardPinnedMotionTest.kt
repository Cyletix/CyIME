package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises disappearing lazy items, not only an alpha helper or an expanded end state. */
class ClipboardPinnedMotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun foldedRecentRowsFadeInAndRemainPaintedDuringFadeOut() {
        val pinned = (1L..2L).map { ClipboardItem(it, "固定记录 $it", timestamp = 1 - it) }
        // Recent copies fold only when pins sit below them.
        val history = pinned + (101L..108L).map {
            ClipboardItem(it, "最近复制 $it\n第一行内容\n第二行内容", timestamp = 1000 - it)
        }
        var pasted = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(),
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.requiredSize(360.dp, 400.dp)) {
                        ClipboardBoardView(history, emptyList(), false, false,
                            pinned.mapTo(mutableSetOf()) { "text:${it.id}" }, false, null,
                            onBack = {}, onQuickSend = {}, onSelectText = { pasted++ }, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                            onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                            onPullRemote = null)
                    }
                }
            }
        }
        rule.waitForIdle()
        val controls = rule.onNodeWithTag("clipboard-controls").fetchSemanticsNode().boundsInRoot
        capture("recent-collapsed.png")
        rule.onNodeWithTag("clipboard-card:text:105").assertDoesNotExist()
        rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("clipboard-recent-toggle").performClick()
        rule.mainClock.advanceTimeBy(80)
        capture("recent-opening.png")
        rule.onNodeWithTag("clipboard-card:text:105").assertExists()
        rule.mainClock.advanceTimeBy(320)
        capture("recent-expanded.png")
        assertEquals(controls, rule.onNodeWithTag("clipboard-controls").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
        rule.onNodeWithTag("clipboard-recent-toggle").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        capture("recent-collapsed-again.png")
        rule.onNodeWithTag("clipboard-card:text:105").assertDoesNotExist()
        rule.onNodeWithTag("clipboard-card:text:1").assertExists()
        assertEquals(controls, rule.onNodeWithTag("clipboard-controls").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals("expansion must not paste any item", 0, pasted) }
    }

    private fun distance(a: Int, b: Int): Int = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )

    private fun capture(name: String): Bitmap {
        val bitmap = rule.onNodeWithTag("clipboard-board").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "clipboard-recent-motion").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }
}