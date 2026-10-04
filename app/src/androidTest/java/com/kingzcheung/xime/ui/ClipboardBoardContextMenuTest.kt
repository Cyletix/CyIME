package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.ClipboardCard
import com.kingzcheung.xime.clipboard.ClipboardImage
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.menubar.ClipboardBoardView
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Runs the production board long-press path, including its real action callbacks. */
class ClipboardBoardContextMenuTest {
    @get:Rule val rule = createComposeRule()
    private val text = ClipboardItem(12, "保留完整的剪贴板文字", timestamp = 20)
    private lateinit var image: ClipboardImage
    private lateinit var imageFile: File
    private val darkAppearance = mutableStateOf(true)
    private val glassEnabled = mutableStateOf(true)
    private val pins = mutableStateOf(emptySet<String>())
    private var split: Pair<String, Long>? = null
    private var edited: Triple<Long, String, String>? = null
    private var removed = emptyList<ClipboardCard>()

    @Before fun createLoadableImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        imageFile = File.createTempFile("clipboard-menu-fixture-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = IMAGE_RED
            canvas.drawRect(0f, 0f, 80f, 120f, paint)
            paint.color = IMAGE_TEAL
            canvas.drawRect(80f, 0f, 160f, 120f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("CyIME", 16f, 42f, paint)
            canvas.drawText("IMAGE", 16f, 70f, paint)
            imageFile.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        image = ClipboardImage(Uri.fromFile(imageFile).toString(), "image/png", 10, "测试图片")
    }

    @After fun removeOnlyTheTestImage() {
        if (::imageFile.isInitialized && imageFile.exists()) {
            assertTrue("test image must be removed", imageFile.delete())
        }
    }

    @Test fun textPreviewAndOriginalActionsKeepTheToolbarAndRecordPositions() {
        showBoard()
        val toolbar = rule.onNodeWithTag("clipboard-more").fetchSemanticsNode().boundsInRoot
        val record = rule.onNodeWithTag("clipboard-card:text:12").fetchSemanticsNode().boundsInRoot
        open("text:12")
        rule.onNodeWithText("记录操作").assertDoesNotExist()
        val preview = rule.onNodeWithTag("clipboard-item-preview").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("clipboard-item-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("left record preview stays to the left of its actions", preview.right <= actions.left)
        assertEquals(toolbar, rule.onNodeWithTag("clipboard-more").fetchSemanticsNode().boundsInRoot)
        assertEquals(record, rule.onNodeWithTag("clipboard-card:text:12").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("分词").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(text.text to text.id, split) }
        rule.onNodeWithTag("clipboard-item-menu").assertDoesNotExist()
        open("text:12")
        rule.onNodeWithText("快捷").assertDoesNotExist()
        rule.onNodeWithText("编辑").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Triple(text.id, text.text, text.code), edited) }
    }

    @Test fun imagePreviewUsesSameMenuAndPreservesPinAndDeleteActions() {
        showBoard(dark = false)
        val key = "image:${image.uri}"
        open(key)
        val preview = rule.onNodeWithTag("clipboard-item-preview").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("clipboard-item-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("right image preview stays to the right of its actions", actions.right <= preview.left)
        rule.onNodeWithText("分词").assertDoesNotExist()
        rule.onNodeWithText("快捷").assertDoesNotExist()
        rule.onNodeWithText("编辑").assertDoesNotExist()
        rule.onNodeWithText("固定").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(setOf(key), pins.value) }
        open(key)
        rule.onNodeWithText("取消固定").assertExists()
        rule.onNodeWithText("删除").performScrollTo().performClick()
        rule.runOnIdle { assertTrue("delete still requires confirmation", removed.isEmpty()) }
        rule.onNodeWithTag("clipboard-confirm-delete").performClick()
        rule.runOnIdle {
            assertEquals(listOf(ClipboardCard.Image(image)), removed)
            assertTrue(pins.value.isEmpty())
        }
    }

    @Test fun shortLargeTextPanelKeepsEveryActionReachableInsideRecords() {
        showBoard(width = 240, height = 280, fontScale = 2f)
        open("text:12")
        val region = rule.onNodeWithTag("clipboard-records-region").fetchSemanticsNode().boundsInRoot
        for (label in listOf("固定", "分词", "编辑", "多选", "删除")) {
            val action = rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$label extends above records", action.top >= region.top - 1f)
            assertTrue("$label extends below records", action.bottom <= region.bottom + 1f)
        }
        rule.onNodeWithText("多选").performScrollTo().performClick()
        rule.onNodeWithText("已选 1").assertIsDisplayed()
        rule.onNodeWithTag("clipboard-item-menu").assertDoesNotExist()
    }

    @Test fun textImageAndDeleteConfirmationRenderGlassInBothAppearances() {
        showBoard(width = 480, height = 400)
        for (dark in listOf(true, false)) {
            val appearance = if (dark) "dark" else "light"
            rule.runOnIdle { darkAppearance.value = dark; glassEnabled.value = true }

            open("text:12")
            rule.onNode(hasText(text.text) and hasAnyAncestor(hasTestTag("clipboard-item-preview")),
                useUnmergedTree = true).assertIsDisplayed()
            assertGlassMaterial(rule.onNodeWithTag("clipboard-item-menu"), "$appearance text menu")
            saveBoard("clipboard-context-$appearance-text.png")
            rule.onNodeWithTag("clipboard-item-menu-dismiss").performTouchInput { click(Offset(2f, 2f)) }

            open("image:${image.uri}")
            val preview = rule.onNodeWithTag("clipboard-item-preview")
            awaitImage(preview) { containsFixtureColors(it) }
            assertGlassMaterial(rule.onNodeWithTag("clipboard-item-menu"), "$appearance image menu")
            assertTrue("the actual image remains visible after changing glass", containsFixtureColors(
                preview.captureToImage().asAndroidBitmap()))
            saveBoard("clipboard-context-$appearance-image.png")

            rule.onNodeWithText("删除").performScrollTo().performClick()
            rule.runOnIdle { assertTrue("opening confirmation cannot remove the record", removed.isEmpty()) }
            // The production dialog's scrollable content is the card itself, not the dark scrim behind it.
            val confirmation = rule.onNode(hasScrollAction() and
                hasAnyDescendant(hasTestTag("clipboard-confirm-delete")))
            confirmation.assertIsDisplayed()
            assertGlassMaterial(confirmation, "$appearance delete confirmation")
            saveBoard("clipboard-context-$appearance-delete.png")
            rule.onNodeWithText("取消").performScrollTo().performClick()
            rule.onNodeWithTag("clipboard-confirm-delete").assertDoesNotExist()
            rule.runOnIdle { assertTrue("cancel must leave the fixture records untouched", removed.isEmpty()) }
        }
    }

    private fun open(key: String) {
        rule.onNodeWithTag("clipboard-records").performScrollToNode(hasTestTag("clipboard-card:$key"))
        rule.onNodeWithTag("clipboard-card:$key").performTouchInput { longClick() }
        rule.onNodeWithTag("clipboard-item-menu").assertIsDisplayed()
    }

    private fun showBoard(width: Int = 360, height: Int = 400, fontScale: Float = 1f, dark: Boolean = true) {
        darkAppearance.value = dark
        glassEnabled.value = true
        rule.setContent {
            val isDark = darkAppearance.value
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    frostedGlass = FrostedGlassConfig.defaults(isDark, enabled = glassEnabled.value)),
            ) {
                MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        ClipboardBoardView(
                            textItems = listOf(text), images = listOf(image), initialImages = false,
                            expanded = false, pins = pins.value, photoAccess = false, failure = null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { value, id -> split = value to id }, onAddQuick = {},
                            onPinsChange = { pins.value = it }, onRemove = { removed = it },
                            onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                            onPullRemote = null,
                            onEditText = { id, value, code -> edited = Triple(id, value, code) },
                        )
                    }
                }
            }
        }
    }

    private fun assertGlassMaterial(node: SemanticsNodeInteraction, description: String) {
        rule.runOnIdle { glassEnabled.value = false }
        val plain = node.captureToImage().asAndroidBitmap()
        rule.runOnIdle { glassEnabled.value = true }
        val glass = awaitImage(node) { candidate ->
            candidate.width == plain.width && candidate.height == plain.height &&
                changedBackgroundFraction(plain, candidate) > 0.75f
        }
        assertEquals("$description must retain its width", plain.width, glass.width)
        assertEquals("$description must retain its height", plain.height, glass.height)
        assertTrue("$description must replace its solid background, not just recolor its labels",
            changedBackgroundFraction(plain, glass) > 0.75f)
    }

    /** Right padding is empty in both the long-press overlay and the confirmation card. */
    private fun changedBackgroundFraction(plain: Bitmap, glass: Bitmap): Float {
        var changed = 0
        var samples = 0
        for (y in plain.height / 4 until plain.height * 3 / 4 step 3) {
            for (x in plain.width - 8 until plain.width - 3) {
                if (distance(plain.getPixel(x, y), glass.getPixel(x, y)) > 6) changed++
                samples++
            }
        }
        return if (samples == 0) 0f else changed.toFloat() / samples
    }

    private fun containsFixtureColors(bitmap: Bitmap): Boolean {
        var red = 0
        var teal = 0
        for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
            val pixel = bitmap.getPixel(x, y)
            if (distance(pixel, IMAGE_RED) < 15) red++
            if (distance(pixel, IMAGE_TEAL) < 15) teal++
        }
        return red >= 20 && teal >= 20
    }

    private fun distance(a: Int, b: Int) = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )

    private fun awaitImage(node: SemanticsNodeInteraction, condition: (Bitmap) -> Boolean): Bitmap {
        var result: Bitmap? = null
        rule.waitUntil(timeoutMillis = 15_000) {
            val current = node.captureToImage().asAndroidBitmap()
            if (condition(current)) { result = current; true } else false
        }
        return checkNotNull(result)
    }

    private fun saveBoard(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "clipboard-context-glass").apply { mkdirs() }
        File(directory, name).outputStream().use {
            assertTrue(rule.onNodeWithTag("clipboard-board").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private companion object {
        val IMAGE_RED: Int = android.graphics.Color.rgb(232, 48, 80)
        val IMAGE_TEAL: Int = android.graphics.Color.rgb(16, 184, 160)
    }
}
