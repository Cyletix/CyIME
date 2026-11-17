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

/**
 * PR-only coverage for the source flag that invalidates the clipboard grid.
 * Base intentionally does not compile this file because the parameter is PR-only.
 */
class ClipboardGallerySourceVisualIsolationPrTest {
    @get:Rule val rule = createComposeRule()

    @Test fun gallerySourceSwitchInImageCategoryClearsOutgoingPhoto() {
        runSourceSwitchScenario(initialImages = true)
    }

    @Test fun gallerySourceSwitchInAllCategoryClearsOutgoingPhoto() {
        runSourceSwitchScenario(initialImages = false)
    }

    private fun runSourceSwitchScenario(initialImages: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotFile = File.createTempFile("clipboard-source-screenshot-", ".png", context.cacheDir)
        val galleryFile = File.createTempFile("clipboard-source-gallery-", ".png", context.cacheDir)
        val screenshot = imageFixture(screenshotFile, Color.rgb(255, 12, 12), 400L, "截图")
        val gallery = imageFixture(galleryFile, Color.rgb(12, 12, 255), 500L, "照片")
        val galleryPhotosEnabled = mutableStateOf(true)
        val images = mutableStateOf(listOf(gallery, screenshot))
        val galleryTag = "clipboard-card:image:${gallery.uri}"

        try {
            rule.setContent {
                MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 450.dp)) {
                        ClipboardBoardView(
                            textItems = listOf(ClipboardItem(101L, "SOURCE_FLAG_TEXT", timestamp = 200L)),
                            images = images.value, initialImages = initialImages, expanded = true,
                            pins = emptySet(), photoAccess = true, failure = null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {},
                            onRemove = {}, onPhotoAccess = {}, onPick = {},
                            onSystemPaste = {}, onShare = { _, _ -> }, onPullRemote = null,
                            galleryPhotosEnabled = galleryPhotosEnabled.value,
                        )
                    }
                }
            }

            rule.waitUntil(10_000) { bluePixelCount() > 100 }
            rule.onNodeWithTag(galleryTag, useUnmergedTree = true).assertExists()

            rule.mainClock.autoAdvance = false
            rule.runOnIdle {
                galleryPhotosEnabled.value = false
                images.value = listOf(screenshot)
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag(galleryTag, useUnmergedTree = true).assertDoesNotExist()
            assertTrue("图库来源切换后的第一帧不能残留蓝色照片", bluePixelCount() == 0)
            rule.mainClock.advanceTimeBy(450L)
            assertTrue("图库来源切换后 450ms 不能残留蓝色照片", bluePixelCount() == 0)
            rule.mainClock.advanceTimeBy(1_550L)
            assertTrue("图库来源切换后 2s 不能残留蓝色照片", bluePixelCount() == 0)
        } finally {
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            screenshotFile.delete()
            galleryFile.delete()
        }
    }

    private fun imageFixture(file: File, color: Int, timestamp: Long, label: String): ClipboardImage {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return ClipboardImage(Uri.fromFile(file).toString(), "image/png", timestamp, label)
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
}
