package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.ClipboardImage
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks visible pixels as well as semantics: an exiting lazy item can outlive its semantics. */
class ClipboardCategoryVisualIsolationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rapidlySwitchingTextAndImageCategoriesDoesNotLeaveOutgoingCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("clipboard-category-ghosting-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(255, 12, 12))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val image = ClipboardImage(Uri.fromFile(file).toString(), "image/png", 100L)
        val imageTag = "clipboard-card:image:${image.uri}"
        val textTag = "clipboard-card:text:1"
        val sentinel = "TEXT_CATEGORY_ONLY_SENTINEL"

        try {
            rule.setContent {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 450.dp).testTag("fixture")) {
                        ClipboardBoardView(
                            textItems = listOf(ClipboardItem(1L, sentinel, timestamp = 200L)),
                            images = listOf(image),
                            initialImages = false, expanded = true, pins = emptySet(),
                            photoAccess = false, failure = null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {},
                            onRemove = {}, onPhotoAccess = {}, onPick = {},
                            onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null,
                        )
                    }
                }
            }

            rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
            // Ensure the synthetic thumbnail has decoded, not merely been composed.
            rule.waitUntil(10_000) { redPixelCount() > 100 }
            rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertExists()

            // Capture the first transition frames; waiting for idle after each switch
            // would hide the regression by letting the exiting fade finish.
            rule.mainClock.autoAdvance = false
            repeat(3) {
                rule.onNodeWithTag("clipboard-filter:TEXT").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(textTag, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertDoesNotExist()
                assertTrue("旧图片像素不能残留到文本分类", redPixelCount() == 0)

                rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(textTag, useUnmergedTree = true).assertDoesNotExist()
                rule.onNodeWithText(sentinel).assertDoesNotExist()
            }
        } finally {
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            file.delete()
        }
    }

    private fun redPixelCount(): Int {
        val snapshot = rule.onNodeWithTag("clipboard-records-region").captureToImage().asAndroidBitmap()
        var count = 0
        for (y in 0 until snapshot.height step 4) {
            for (x in 0 until snapshot.width step 4) {
                val color = snapshot.getPixel(x, y)
                if (Color.red(color) > 205 && Color.green(color) < 90 && Color.blue(color) < 90) count++
            }
        }
        return count
    }
}
