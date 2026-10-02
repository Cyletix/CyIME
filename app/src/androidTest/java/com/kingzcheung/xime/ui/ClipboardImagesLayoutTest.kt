package com.kingzcheung.xime.ui

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.clipboard.*
import com.kingzcheung.xime.service.ImagePasteProtocol
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.menubar.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ClipboardImagesLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val image = ClipboardImage("content://fixture/image", "image/png", 1L)
    private data class Viewport(val width: Int, val height: Int, val font: Float)

    @Test fun imagePreviewUsesTextCapsuleAndPastesOriginalImage() {
        val viewport = mutableStateOf(Viewport(240, 90, 1f))
        var selected: ClipboardImage? = null
        var expanded = 0
        rule.setContent {
            val v = viewport.value
            CompositionLocalProvider(LocalDensity provides Density(1f, v.font)) {
                MaterialTheme { Box(Modifier.requiredWidth(v.width.dp).testTag("image-preview-host")) {
                    CandidateBar(CandidateBarState.ClipboardDisplay(image = image),
                        visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray),
                        callbacks = CandidateBarCallbacks(onCandidateSelect = { fail("Must not paste image URI as text") },
                            onClipboardImageSelect = { selected = it }, onOpenClipboard = { expanded++ },
                            onExpandClipboardImages = { fail("Use the same clipboard entry as text") }))
                } }
            }
        }
        for (v in listOf(Viewport(240, 90, 1f), Viewport(280, 100, 2f), Viewport(800, 100, 2f))) {
            rule.runOnIdle { viewport.value = v }
            rule.assertGeometry("image-preview-host", "$v image capsule")
            val host = rule.onNodeWithTag("image-preview-host").fetchSemanticsNode().boundsInRoot
            val pill = rule.onNodeWithTag("clipboard-preview-pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val paste = rule.onNodeWithTag("clipboard-image-paste").fetchSemanticsNode().boundsInRoot
            val thumbnail = rule.onNodeWithTag("clipboard-image-thumbnail", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("capsule stays centered like text", host.center.x, pill.center.x, 1f)
            assertTrue("image must not stretch the candidate bar", pill.height <= 44f)
            assertTrue("thumbnail must remain small", thumbnail.height <= 28f)
            assertEquals(paste.center.x, thumbnail.center.x, 1f)
            assertEquals(paste.center.y, thumbnail.center.y, 1f)
            assertTrue(thumbnail.top > pill.top && thumbnail.bottom < pill.bottom)
            rule.onNodeWithText("粘贴图片").assertDoesNotExist()
            rule.onNodeWithText("更多图片").assertDoesNotExist()
            for (tag in listOf("clipboard-image-paste", "clipboard-image-more")) {
                rule.onNodeWithTag(tag).assertIsDisplayed().performClick()
            }
        }
        assertEquals(image, selected)
        assertEquals(3, expanded)
    }

    @Test fun unifiedBoardShowsTextAndImagesWithReadableActions() {
        val viewport = mutableStateOf(Viewport(240, 240, 2f))
        var pasted = 0
        val images = List(12) { image.copy(uri = "content://fixture/$it") }
        rule.setContent {
            val v = viewport.value
            CompositionLocalProvider(LocalDensity provides Density(1f, v.font)) {
                MaterialTheme { Box(Modifier.requiredSize(v.width.dp, v.height.dp).testTag("images-host")) {
                    ClipboardBoardView(listOf(ClipboardItem(1, "熊本大学", timestamp = 20)), images,
                        initialImages = false, expanded = true, pins = emptySet(), photoAccess = false,
                        failure = ImagePasteFailure(image, "fixture"), onBack = {}, onQuickSend = {},
                        onSelectText = {}, onSelectImage = {}, onSplit = { _, _ -> }, onAddQuick = {},
                        onPinsChange = {}, onRemove = {}, onPhotoAccess = {}, onPick = {},
                        onSystemPaste = { pasted++ }, onShare = { _, _ -> }, onPullRemote = null)
                } }
            }
        }
        for (v in listOf(Viewport(240, 240, 2f), Viewport(360, 500, 1f), Viewport(800, 300, 1.3f))) {
            rule.runOnIdle { viewport.value = v }
            rule.onNodeWithTag("clipboard-more").assertIsDisplayed()
            rule.onNodeWithTag("clipboard-filter:ALL").performScrollTo().assertIsSelected()
            rule.onNodeWithTag("clipboard-records").performScrollToNode(hasTestTag("images-system-paste"))
            rule.onNodeWithTag("images-system-paste").performScrollTo().performClick()
            rule.onNodeWithTag("clipboard-records").performScrollToNode(hasTestTag("clipboard-card:text:1"))
            rule.onNodeWithTag("clipboard-card:text:1").assertExists()
            rule.onNodeWithTag("clipboard-filter:IMAGE").performScrollTo().performClick()
            rule.onNodeWithTag("clipboard-card:text:1").assertDoesNotExist()
            rule.onNodeWithTag("clipboard-filter:ALL").performScrollTo().performClick()
        }
        assertEquals(3, pasted)
    }
    @Test fun pngRemainsReadableAfterOriginalIsRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "emoji_cache/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val store = ClipboardImages.getInstance(context)
        val cached = runBlocking { store.prepare(ClipboardImage(uri.toString(), "image/jpeg", System.currentTimeMillis())) }
        file.delete()
        assertEquals("image/png", cached.mimeType)
        context.contentResolver.openInputStream(Uri.parse(cached.uri))!!.use {
            assertEquals(137, it.read()); assertEquals(80, it.read()); assertEquals(78, it.read()); assertEquals(71, it.read())
        }
        assertEquals(cached, runBlocking { store.prepare(cached) })
    }

    @Test fun imageDeliveryTriesNativeAndAdvertisedLegacyProtocols() {
        val content = InputContentInfo(Uri.parse(image.uri), ClipDescription("图片", arrayOf("image/png")), null)
        val editor = EditorInfo()
        var nativeAccept = true
        var privateCalls = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = object : BaseInputConnection(android.view.View(context), false) {
            override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
                assertEquals(InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, flags)
                return nativeAccept
            }
            override fun performPrivateCommand(action: String, data: Bundle?): Boolean {
                privateCalls++
                assertEquals("android.support.v13.view.inputmethod.InputConnectionCompat.COMMIT_CONTENT", action)
                assertEquals(image.uri, data?.getParcelable<Uri>("android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_URI").toString())
                return true
            }
        }
        assertTrue(ImagePasteProtocol.commit(connection, editor, content)) // No advertised MIME, native accepts.
        nativeAccept = false
        assertFalse(ImagePasteProtocol.commit(connection, editor, content)) // No interface: must offer fallback.
        assertEquals(0, privateCalls)
        editor.extras = Bundle().apply { putStringArray("android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES", arrayOf("image/*")) }
        assertTrue(ImagePasteProtocol.commit(connection, editor, content))
        assertEquals(1, privateCalls)
        assertTrue(ImagePasteProtocol.supports(arrayOf("image/*"), "image/png"))
        assertFalse(ImagePasteProtocol.supports(arrayOf("text/*"), "image/png"))
    }
}
