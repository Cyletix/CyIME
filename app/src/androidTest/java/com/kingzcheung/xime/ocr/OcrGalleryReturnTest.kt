package com.kingzcheung.xime.ocr

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.ui.saveUiEvidence
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual image decoder/panel and ViewModel; system photo picker interaction is checked separately. */
class OcrGalleryReturnTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var originalUri: Uri? = null
    private var testImage: File? = null

    @After fun restore() {
        rule.runOnIdle { OcrGallerySelection.uri.value = originalUri }
        testImage?.delete()
    }

    @Test fun captureIsTheOnlyConfirmationAndPreviewFillsThePanel() {
        originalUri = OcrGallerySelection.uri.value
        val dir = File(app.cacheDir, "document_ocr").apply { mkdirs() }
        val file = File.createTempFile("ocr-confirmation-", ".png", dir).also { testImage = it }
        Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.WHITE)
            Canvas(bitmap).drawText("CONTINUE SCANNING 123", 65f, 320f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = 64f
            })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        var confirmed: String? = null
        var preview by mutableStateOf(true)
        OcrGallerySelection.uri.value = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(240.dp)) {
                    if (preview) OcrCameraPanel(true, Color(0xff182625), Color.White, Color.Cyan,
                        onClose = { preview = false }, onConfirmed = { confirmed = it; preview = false })
                    else Text("进入编辑")
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("移动或缩放图片，将文字放入中间框").fetchSemanticsNodes().isNotEmpty() }
        val mask = rule.onNodeWithTag("ocr-scan-mask").fetchSemanticsNode().boundsInRoot
        assertEquals(rule.onNodeWithTag("ocr-camera-panel").fetchSemanticsNode().boundsInRoot, mask)
        assertEquals(mask, rule.onNodeWithTag("ocr-gallery-image").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("ocr-confirm").assertDoesNotExist()
        assertNull(confirmed)
        rule.onNodeWithTag("ocr-gallery-image").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("ocr-gallery-image").performTouchInput { swipeRight() }
        assertEquals(mask, rule.onNodeWithTag("ocr-scan-mask").fetchSemanticsNode().boundsInRoot)
        saveUiEvidence("ocr-full-preview-floating-controls", rule.onNodeWithTag("ocr-camera-panel"))
        rule.onNodeWithTag("ocr-capture").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("进入编辑").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ocr-camera-panel").assertDoesNotExist()
        rule.onNodeWithText("进入编辑").assertIsDisplayed()
        assertTrue(confirmed.orEmpty().contains("CONTINUE SCANNING"))
    }

    @Test fun blankImageStaysOpenForRetryAndFloatingButtonsAvoidTheScanStripAtMinimumHeight() {
        originalUri = OcrGallerySelection.uri.value
        val file = File.createTempFile("ocr-blank-", ".png", File(app.cacheDir, "document_ocr").apply { mkdirs() })
            .also { testImage = it }
        Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.BLACK)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        var confirmed: String? = null
        OcrGallerySelection.uri.value = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(144.dp)) {
                    OcrCameraPanel(true, Color(0xff182625), Color.White, Color(0xffb5cbcf), {}, { confirmed = it })
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("移动或缩放图片，将文字放入中间框").fetchSemanticsNodes().isNotEmpty() }
        val panel = rule.onNodeWithTag("ocr-camera-panel").fetchSemanticsNode().boundsInRoot
        val capture = rule.onNodeWithTag("ocr-capture").fetchSemanticsNode().boundsInRoot
        assertTrue("Floating capture stays below the recognition strip", capture.top >= panel.top + panel.height * OcrScanGeometry.BOTTOM)
        repeat(2) {
            rule.onNodeWithTag("ocr-capture").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("框内未识别到文字，请移动或缩放图片").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("ocr-capture").assertIsEnabled()
            rule.onNodeWithTag("ocr-gallery-image").assertIsDisplayed()
            assertNull(confirmed)
        }
        rule.onNodeWithTag("ocr-confirm").assertDoesNotExist()
        saveUiEvidence("ocr-minimum-floating-controls", rule.onNodeWithTag("ocr-camera-panel"))
    }

    @Test fun selectedImageRemainsVisibleAfterLateEditorPreparationAndFrameDoesNotMove() {
        originalUri = OcrGallerySelection.uri.value
        val imageDirectory = File(app.cacheDir, "document_ocr").apply { mkdirs() }
        val file = File.createTempFile("ocr-gallery-return-", ".png", imageDirectory).also { testImage = it }
        Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.WHITE)
            Canvas(bitmap).drawText("GALLERY RETURN 123", 80f, 320f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = 62f
            })
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        val selected = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val model = KeyboardViewModel(app)
        rule.runOnIdle {
            model.resetKeyboard(false, "pinyin")
            OcrGallerySelection.uri.value = selected
            model.showOverlay(OverlayRoute.Ocr)
        }
        rule.setContent {
            val page by model.page.collectAsState()
            MaterialTheme {
                Box(Modifier.width(360.dp).height(240.dp)) {
                    if ((page as? KeyboardPage.Overlay)?.route == OverlayRoute.Ocr)
                        OcrCameraPanel(true, Color(0xff182625), Color.White, Color(0xffb5cbcf),
                            onClose = { model.closeOverlay() }, onConfirmed = {})
                    else Text("普通键盘")
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("移动或缩放图片，将文字放入中间框").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("识别", useUnmergedTree = true).assertIsDisplayed()
        val maskBounds = rule.onNodeWithTag("ocr-scan-mask").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            model.applyPreparedEditorKeyboard(false, "pinyin", handwriting = false,
                restarting = false, forceNumberPanel = false)
        }
        rule.onNodeWithTag("ocr-gallery-image").assertIsDisplayed()
        rule.onNodeWithText("普通键盘").assertDoesNotExist()
        rule.onNodeWithTag("ocr-gallery-image").performTouchInput { swipeLeft() }
        assertEquals(maskBounds, rule.onNodeWithTag("ocr-scan-mask").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals(selected, OcrGallerySelection.uri.value) }
        saveUiEvidence("ocr-gallery-after-editor-preparation", rule.onNodeWithTag("ocr-camera-panel"))
        rule.onNodeWithText("取消").performClick()
        rule.runOnIdle {
            model.applyPreparedEditorKeyboard(false, "pinyin", handwriting = false,
                restarting = false, forceNumberPanel = false)
        }
        rule.onNodeWithTag("ocr-gallery-image").assertDoesNotExist()
        rule.onNodeWithText("普通键盘").assertIsDisplayed()
    }
}
