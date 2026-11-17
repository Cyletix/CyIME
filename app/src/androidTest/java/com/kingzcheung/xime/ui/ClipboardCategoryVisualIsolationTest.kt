package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
import kotlin.math.abs
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

    /**
     * Models the first opt-in: text-only history, then screenshots, then gallery photos.
     * The synthetic feeds avoid touching the device's real clipboard or photo library.
     */
    @Test fun enablingScreenshotAndGalleryFeedsDoesNotCarryOldPixelsIntoTextCategory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun imageFixture(prefix: String, color: Int, timestamp: Long, label: String): Pair<File, ClipboardImage> {
            val file = File.createTempFile(prefix, ".png", context.cacheDir)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            return file to ClipboardImage(Uri.fromFile(file).toString(), "image/png", timestamp, label)
        }

        val (screenshotFile, screenshot) = imageFixture(
            "clipboard-screenshot-", Color.rgb(255, 12, 12), 400L, "截图")
        val (galleryFile, galleryPhoto) = imageFixture(
            "clipboard-gallery-", Color.rgb(12, 12, 255), 500L, "照片")
        val screenshotsEnabled = mutableStateOf(false)
        val feed = mutableStateOf<List<ClipboardImage>>(emptyList())
        val textTag = "clipboard-card:text:81"
        val screenshotTag = "clipboard-card:image:${screenshot.uri}"
        val galleryTag = "clipboard-card:image:${galleryPhoto.uri}"

        try {
            rule.setContent {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 450.dp)) {
                        ClipboardBoardView(
                            textItems = listOf(ClipboardItem(81L, "TEXT_ONLY_START", timestamp = 200L)),
                            images = feed.value, initialImages = false, expanded = true,
                            pins = emptySet(), photoAccess = screenshotsEnabled.value, failure = null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {},
                            onRemove = {}, onPhotoAccess = {}, onPick = {},
                            onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null,
                            // In list mode text is one column, while IMAGE remains a masonry grid.
                            listLayout = true,
                        )
                    }
                }
            }
            rule.onNodeWithTag(textTag, useUnmergedTree = true).assertExists()
            rule.runOnIdle {
                screenshotsEnabled.value = true
                feed.value = listOf(screenshot)
            }
            rule.waitUntil(10_000) { redPixelCount() > 100 }
            rule.runOnIdle {
                feed.value = listOf(galleryPhoto, screenshot)
            }
            rule.waitUntil(10_000) { bluePixelCount() > 100 }
            rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
            rule.onNodeWithTag(screenshotTag, useUnmergedTree = true).assertExists()
            rule.onNodeWithTag(galleryTag, useUnmergedTree = true).assertExists()

            // The real failure could persist longer than a normal 180 ms exit transition.
            rule.mainClock.autoAdvance = false
            repeat(3) {
                rule.onNodeWithTag("clipboard-filter:TEXT").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(textTag, useUnmergedTree = true).assertExists()
                assertTrue("图片离场第一帧不能覆盖文本", redPixelCount() == 0 && bluePixelCount() == 0)
                rule.mainClock.advanceTimeBy(450L)
                assertTrue("图片残影不能持续到动画结束后", redPixelCount() == 0 && bluePixelCount() == 0)

                rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(screenshotTag, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(galleryTag, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(textTag, useUnmergedTree = true).assertDoesNotExist()
            }

            // A source switch inside the same category must drop the outgoing photo too.
            rule.runOnIdle {
                feed.value = listOf(screenshot)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag(galleryTag, useUnmergedTree = true).assertDoesNotExist()
            rule.mainClock.advanceTimeBy(450L)
            assertTrue("关闭相册来源后蓝色照片不能残留到 450ms", bluePixelCount() == 0)
            rule.mainClock.advanceTimeBy(1_550L)
            assertTrue("关闭相册来源后蓝色照片不能残留到 2s", bluePixelCount() == 0)
        } finally {
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            screenshotFile.delete()
            galleryFile.delete()
        }
    }

    @Test fun bidirectionalSwitchesClearTextPixelsAndImageHatchAfter450msAnd2s() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("clipboard-pixel-baseline-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(12, 12, 255))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val image = ClipboardImage(Uri.fromFile(file).toString(), "image/png", 700L)
        val imageTag = "clipboard-card:image:${image.uri}"
        val textTag = "clipboard-card:text:91"
        val sentinel = "PIXEL_BASELINE_TEXT_SENTINEL"

        try {
            rule.setContent {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 450.dp)) {
                        ClipboardBoardView(
                            textItems = listOf(ClipboardItem(91L, sentinel, timestamp = 600L)),
                            images = listOf(image), initialImages = false, expanded = true,
                            pins = emptySet(), photoAccess = false, failure = null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {},
                            onRemove = {}, onPhotoAccess = {}, onPick = {},
                            onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null,
                        )
                    }
                }
            }

            rule.onNodeWithTag("clipboard-filter:TEXT").performScrollTo().performClick()
            rule.onNodeWithTag(textTag, useUnmergedTree = true).assertExists()
            rule.waitForIdle()
            val textBaseline = captureRecordsPixels()

            rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
            rule.waitUntil(10_000) { redPixelCount() == 0 && bluePixelCount() > 100 }
            rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertExists()
            val imageBaseline = captureRecordsPixels()
            assertTrue("合成纯色图片必须能观察到异色像素代理", differentPixelCountFromSolidBaseline(imageTag, 12, 12, 255) > 20)

            rule.mainClock.autoAdvance = false
            repeat(2) {
                rule.onNodeWithTag("clipboard-filter:TEXT").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(textTag, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertDoesNotExist()
                rule.mainClock.advanceTimeBy(450L)
                assertPixelBaseline(textBaseline, "图片→文字 450ms 不能残留图片或斜线")
                rule.mainClock.advanceTimeBy(1_550L)
                assertPixelBaseline(textBaseline, "图片→文字 2s 不能残留图片或斜线")

                rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag(imageTag, useUnmergedTree = true).assertExists()
                rule.waitUntil(10_000) { bluePixelCount() > 100 }
                rule.mainClock.advanceTimeBy(450L)
                assertPixelBaseline(imageBaseline, "文字→图片 450ms 不能残留文字像素")
                rule.mainClock.advanceTimeBy(1_550L)
                assertPixelBaseline(imageBaseline, "文字→图片 2s 不能残留文字像素")
            }
        } finally {
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            file.delete()
        }
    }

    private data class PixelSnapshot(val width: Int, val height: Int, val pixels: IntArray)

    private fun captureRecordsPixels(): PixelSnapshot {
        val bitmap = rule.onNodeWithTag("clipboard-records-region").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PixelSnapshot(bitmap.width, bitmap.height, pixels)
    }

    private fun assertPixelBaseline(expected: PixelSnapshot, message: String) {
        val actual = captureRecordsPixels()
        assertTrue("$message: size changed", expected.width == actual.width && expected.height == actual.height)
        var different = 0
        for (index in expected.pixels.indices) {
            val old = expected.pixels[index]
            val now = actual.pixels[index]
            if (abs(Color.red(old) - Color.red(now)) > 12 ||
                abs(Color.green(old) - Color.green(now)) > 12 ||
                abs(Color.blue(old) - Color.blue(now)) > 12) {
                different++
            }
        }
        assertTrue("$message: $different pixels differ from the stable category baseline", different <= 200)
    }

    private fun differentPixelCountFromSolidBaseline(tag: String, baseRed: Int, baseGreen: Int, baseBlue: Int): Int {
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var different = 0
        for (y in 4 until (bitmap.height - 4) step 2) {
            for (x in 4 until (bitmap.width - 4) step 2) {
                val color = bitmap.getPixel(x, y)
                if (abs(Color.red(color) - baseRed) > 12 ||
                    abs(Color.green(color) - baseGreen) > 12 ||
                    abs(Color.blue(color) - baseBlue) > 12) {
                    different++
                }
            }
        }
        return different
    }

    private fun bluePixelCount(): Int {
        val snapshot = rule.onNodeWithTag("clipboard-records-region").captureToImage().asAndroidBitmap()
        var count = 0
        for (y in 0 until snapshot.height step 4) {
            for (x in 0 until snapshot.width step 4) {
                val color = snapshot.getPixel(x, y)
                if (Color.blue(color) > 205 && Color.red(color) < 90 && Color.green(color) < 90) count++
            }
        }
        return count
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
