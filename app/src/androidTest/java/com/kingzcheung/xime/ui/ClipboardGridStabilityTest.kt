package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.CachePolicy
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Size
import com.kingzcheung.xime.clipboard.ClipboardImage
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.clipboard.ClipboardRecentRows
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic files only: never accesses clipboard storage, photo media, or saved geometry. */
class ClipboardGridStabilityTest {
    @get:Rule val rule = createComposeRule()
    private val width = mutableStateOf(360)
    private val expanded = mutableStateOf(false)
    private val visible = mutableStateOf(true)
    private var panel: ClipboardPanelExpansion? = null
    private val fixtureFiles = mutableListOf<File>()
    private val images = mutableListOf<ClipboardImage>()
    private val requestedSizes = ConcurrentHashMap<String, Size>()
    private val loaded = ConcurrentHashMap.newKeySet<String>()
    private val decodeGate = CompletableDeferred<Unit>()
    private var previousLoader: ImageLoader? = null
    private var fixtureLoader: ImageLoader? = null

    @After fun restoreLoaderAndRemoveOnlySyntheticImages() {
        decodeGate.complete(Unit)
        rule.runOnIdle { visible.value = false; panel?.dispose() }
        rule.waitForIdle()
        previousLoader?.let { Coil.setImageLoader(it) }
        fixtureLoader?.shutdown()
        fixtureFiles.forEach { if (it.exists()) assertTrue(it.delete()) }
    }

    @Test fun pinsFollowFoldedRecentPicturesWithOneBoundedPreviewHeight() {
        createImageFixtures(80)
        decodeGate.complete(Unit)
        val text = (1L..6L).map { id ->
            ClipboardItem(id, if (id % 2L == 1L) "短文 $id" else "很长的固定文字 $id，固定区不能把它换成另一种卡片。".repeat(12),
                timestamp = 20_000L - id)
        }
        val pins = text.mapTo(mutableSetOf()) { "text:${it.id}" } + images.take(2).map { "image:${it.uri}" }
        showBoard(text, pins)
        rule.runOnIdle { panel!!.setExpanded(true) }
        rule.waitForIdle()
        for (boardWidth in listOf(360, 720)) {
            rule.runOnIdle { width.value = boardWidth }
            rule.waitForIdle()
            val columns = boardWidth / 160
            val shownRecent = images.drop(2).take(columns * ClipboardRecentRows)
            val hiddenRecent = images.drop(2 + columns * ClipboardRecentRows).first()
            rule.onNodeWithTag(imageTag(hiddenRecent)).assertDoesNotExist()
            val slot = relativeBounds(imageTag(shownRecent.first())).height
            assertTrue("previews remain readable and bounded", slot in 1f..112f)
            (shownRecent.map { "clipboard-card:image:${it.uri}" } + pins.map { "clipboard-card:$it" }).forEach { tag ->
                rule.onNodeWithTag("clipboard-records").performScrollToKey(tag.removePrefix("clipboard-card:"))
                assertEquals("recent pictures and pins share one preview height: $tag", slot, relativeBounds(tag).height, 1f)
            }
            saveEvidence("clipboard-stable-pins-$boardWidth.png")

            rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
            rule.onNodeWithTag("clipboard-recent-toggle").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("clipboard-records").performScrollToKey("image:${hiddenRecent.uri}")
            rule.waitForIdle()
            val region = relativeBounds("clipboard-records-region")
            val shown = relativeBounds(imageTag(hiddenRecent))
            assertTrue("opened older picture is on screen: $shown in $region (width $boardWidth)",
                shown.bottom > region.top && shown.top < region.bottom)
            rule.onNodeWithTag("clipboard-records").performScrollToKey("pins-end")
            rule.onNodeWithTag("clipboard-recent-toggle").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(imageTag(hiddenRecent)).assertDoesNotExist()
            rule.onNodeWithTag("clipboard-records").performScrollToIndex(0)
        }
    }

    @Test fun imageDecodeViewportMotionAndOffscreenReturnKeepSizesColumnsAndLazyRequestBounds() {
        createImageFixtures(96)
        showBoard(emptyList(), emptySet())
        val first = images.take(2)
        rule.waitUntil(10_000) { first.all { requestedSizes.containsKey(it.uri) } }
        assertTrue("the decode gate must leave real images unloaded for the before measurement", loaded.isEmpty())
        val unloaded = first.associate { imageTag(it) to relativeBounds(imageTag(it)) }
        val slotHeight = unloaded.values.first().height
        assertTrue("pictures use the bounded preview height, not the original ratio", slotHeight in 1f..112f)
        unloaded.values.forEach { assertEquals("every picture preview has the same height", slotHeight, it.height, 1f) }
        assertLazyWindowOnly()
        decodeGate.complete(Unit)
        awaitLoaded(first)
        unloaded.forEach { (tag, original) -> assertSameRect("decoding cannot change $tag or its lane", original, relativeBounds(tag)) }
        assertThumbnailRequestsMatchSlots(first)

        rule.runOnIdle { panel!!.beginDrag(700f) }
        for (travel in 20..320 step 20) {
            rule.runOnIdle { panel!!.dragTo(700f - travel) }
            unloaded.forEach { (tag, original) -> assertSameRect("dragging cannot reflow $tag", original, relativeBounds(tag)) }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { panel!!.finishDrag() }
        for (time in listOf(32L, 64L, 240L)) {
            rule.mainClock.advanceTimeBy(time)
            unloaded.forEach { (tag, original) -> assertSameRect("snapping cannot reflow $tag", original, relativeBounds(tag)) }
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.waitUntil(10_000) { loaded.containsAll(requestedSizes.keys) }
        assertLazyWindowOnly()
        val viewportBeforeScroll = first.associate { imageTag(it) to relativeBounds(imageTag(it)) }
        saveEvidence("clipboard-stable-images-expanded.png")

        // A direct jump must not decode every intervening/full-history image.
        rule.onNodeWithTag("clipboard-records").performScrollToIndex(40)
        rule.waitUntil(10_000) { images[40].uri in loaded }
        rule.onNodeWithTag(imageTag(first[0]), true).assertIsNotDisplayed()
        assertTrue("scrolling one window must not decode the complete history", requestedSizes.size < images.size / 2)
        rule.onNodeWithTag("clipboard-records").performScrollToIndex(0)
        awaitLoaded(first)
        viewportBeforeScroll.forEach { (tag, original) -> assertSameRect("returning from offscreen must preserve size and lane: $tag", original, relativeBounds(tag)) }
        assertThumbnailRequestsMatchSlots(first)

        rule.runOnIdle { panel!!.beginDrag(400f) }
        for (travel in 20..320 step 20) {
            rule.runOnIdle { panel!!.dragTo(400f + travel) }
            unloaded.forEach { (tag, original) -> assertSameRect("collapsing cannot reflow $tag", original, relativeBounds(tag)) }
        }
        rule.runOnIdle { panel!!.finishDrag() }
        rule.waitForIdle()
        unloaded.forEach { (tag, original) -> assertSameRect("collapsed endpoint retains the original geometry: $tag", original, relativeBounds(tag)) }
        rule.runOnIdle { assertFalse(panel!!.isInMotion) }
    }

    private fun createImageFixtures(count: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Different original ratios, all intentionally unlike the 0.8 preview slot.
        val dimensions = listOf(1600 to 200, 200 to 1600, 1200 to 1200, 1000 to 400, 400 to 1000)
        val encoded = dimensions.map { (w, h) ->
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(IMAGE_COLOR)
                java.io.ByteArrayOutputStream().use { bytes ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                    bytes.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
        repeat(count) { index ->
            val file = File.createTempFile("clipboard-stability-fixture-", ".png", context.cacheDir)
            fixtureFiles += file
            file.writeBytes(encoded[index % encoded.size])
            images += ClipboardImage(Uri.fromFile(file).toString(), "image/png", 10_000L - index, "合成图片 $index")
        }
        val fixtureUris = images.mapTo(mutableSetOf()) { it.uri }
        previousLoader = Coil.imageLoader(context)
        fixtureLoader = ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED)
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val uri = chain.request.data.toString()
                        if (uri in fixtureUris) {
                            requestedSizes[uri] = chain.request.sizeResolver.size()
                            decodeGate.await()
                        }
                        return chain.proceed(chain.request).also { result ->
                            if (uri in fixtureUris && result is SuccessResult) loaded += uri
                        }
                    }
                })
            }.build()
        Coil.setImageLoader(fixtureLoader!!)
    }

    private fun showBoard(text: List<ClipboardItem>, pins: Set<String>) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    if (visible.value) {
                        val expansion = rememberClipboardPanelExpansion(true, 0L, ClipboardPanelBounds(320, 680),
                            expanded.value) { expanded.value = it }!!
                        panel = expansion
                        CompositionLocalProvider(LocalClipboardPanelExpansion provides expansion) {
                            Box(Modifier.requiredSize(width.value.dp, 760.dp), contentAlignment = Alignment.BottomCenter) {
                                Column(Modifier.fillMaxWidth().height(expansion.height.dp)) {
                                    Spacer(Modifier.height(44.dp))
                                    ClipboardBoardView(text, images, false, expanded.value, pins,
                                        photoAccess = false, failure = null, onBack = {}, onQuickSend = {},
                                        onSelectText = { fail("geometry operations must not paste") },
                                        onSelectImage = { fail("geometry operations must not paste") },
                                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                                        onPullRemote = null, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun relativeBounds(tag: String): Rect {
        val box = rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot()
        val viewport = rule.onNodeWithTag("clipboard-records-region", true).getUnclippedBoundsInRoot()
        return Rect(box.left.value - viewport.left.value, box.top.value - viewport.top.value,
            box.right.value - viewport.left.value, box.bottom.value - viewport.top.value)
    }

    private fun assertSameRect(message: String, expected: Rect, actual: Rect) {
        assertEquals("$message: left", expected.left, actual.left, 1f)
        assertEquals("$message: top", expected.top, actual.top, 1f)
        assertEquals("$message: width", expected.width, actual.width, 1f)
        assertEquals("$message: height", expected.height, actual.height, 1f)
    }

    private fun assertLazyWindowOnly() {
        val viewport = rule.onNodeWithTag("clipboard-records-region").getUnclippedBoundsInRoot()
        val height = relativeBounds(imageTag(images.first())).height
        // Permit two extra rows for lazy prefetch/beyond-bounds, never the complete history.
        val maximumWindow = (ceil((viewport.bottom - viewport.top).value / height).toInt() + 2) * 2
        val cards = rule.onAllNodes(SemanticsMatcher("clipboard image card") { node ->
            node.config.contains(SemanticsProperties.TestTag) &&
                node.config[SemanticsProperties.TestTag].startsWith("clipboard-card:image:")
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("only the visible/prefetched lazy window is composed: ${cards.size}/$maximumWindow", cards.size <= maximumWindow)
        assertTrue("only the visible/prefetched lazy window requests decoding: ${requestedSizes.size}/$maximumWindow",
            requestedSizes.size <= maximumWindow)
        assertTrue("the last history image remains unrequested", !requestedSizes.containsKey(images.last().uri))
    }

    private fun assertThumbnailRequestsMatchSlots(records: List<ClipboardImage>) {
        records.forEach { image ->
            val size = requestedSizes.getValue(image.uri)
            val bounds = relativeBounds(imageTag(image))
            assertTrue("thumbnail requests need explicit pixel width", size.width is Dimension.Pixels)
            assertTrue("thumbnail requests need explicit pixel height", size.height is Dimension.Pixels)
            assertEquals("decode width follows the actual slot", bounds.width, (size.width as Dimension.Pixels).px.toFloat(), 1f)
            assertEquals("decode height follows the actual slot", bounds.height, (size.height as Dimension.Pixels).px.toFloat(), 1f)
        }
    }

    private fun awaitLoaded(records: List<ClipboardImage>) {
        rule.waitUntil(10_000) { records.all { it.uri in loaded } }
        rule.waitUntil(10_000) {
            records.all { image ->
                val bitmap = rule.onNodeWithTag(imageTag(image), true).captureToImage().asAndroidBitmap()
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(IMAGE_COLOR)) < 35 &&
                    abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(IMAGE_COLOR)) < 35 &&
                    abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(IMAGE_COLOR)) < 35
            }
        }
    }

    private fun saveEvidence(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(rule.onNodeWithTag("clipboard-board").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun imageTag(image: ClipboardImage) = "clipboard-card:image:${image.uri}"
    private companion object { val IMAGE_COLOR = android.graphics.Color.rgb(20, 160, 190) }
}
