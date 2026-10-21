package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Long-press highlight matches the pressed card; deleting a pin keeps the view where it was. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w800dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardLongPressTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    // Pins are old records; recent history is newer, so an unpinned pin would sort to the bottom.
    private val records = mutableStateOf(
        (1L..4L).map { ClipboardItem(it, "固定内容 $it", timestamp = 5 - it, isPinned = true) } +
            (11L..40L).map { ClipboardItem(it, "最近记录 $it", timestamp = 1000 - it) })
    private val pins = mutableStateOf((1L..4L).mapTo(mutableSetOf()) { "text:$it" } as Set<String>)

    private fun show(width: Int = 360, height: Int = 300) = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f),
            LocalKeyboardInputPreferences provides KeyboardInputPreferences()) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    ClipboardBoardView(records.value, emptyList(), false, false, pins.value, false, null,
                        onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                        onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = { pins.value = it },
                        // The database delete lands later, as on the device.
                        onRemove = {},
                        onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                        onPullRemote = null)
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun near(a: Rect, b: Rect) = listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom)
        .all { kotlin.math.abs(it) <= 1f }

    @Test fun highlightIsExactlyThePressedCard() {
        show()
        rule.waitForIdle()
        for (key in listOf("text:1", "text:2", "text:11", "text:12", "text:13")) {
            val card = bounds("clipboard-card:$key")
            rule.onNodeWithTag("clipboard-card:$key").performTouchInput { longClick() }
            rule.waitForIdle()
            val highlight = bounds("clipboard-item-menu-highlight")
            assertTrue("$key highlight=$highlight card=$card", near(highlight, card))
            System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() }?.let { out ->
                val root = rule.activity.window.decorView
                val full = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
                val board = bounds("clipboard-board")
                val image = android.graphics.Bitmap.createBitmap(full, board.left.toInt().coerceAtLeast(0),
                    board.top.toInt().coerceAtLeast(0), board.width.toInt(), board.height.toInt())
                java.io.File(out).mkdirs()
                java.io.File(out, "clipboard-longpress-${key.replace(':', '-')}.png").outputStream()
                    .use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            rule.onNodeWithTag("clipboard-item-menu-dismiss").performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, 296f)) }
            rule.waitForIdle()
        }
    }

    @Test fun deletingAPinDoesNotScrollTheBoard() {
        show()
        rule.waitForIdle()
        val firstRecent = bounds("clipboard-card:text:11")
        rule.onNodeWithTag("clipboard-card:text:1").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("删除").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("clipboard-confirm-delete").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onAllNodesWithTag("clipboard-card:text:1").assertCountEquals(0)
        rule.onNodeWithTag("clipboard-card:text:2").assertIsDisplayed()
        assertEquals("recent history stays in place", firstRecent.top, bounds("clipboard-card:text:11").top, 1f)
        // The database delete arrives afterwards: still no jump.
        records.value = records.value.filterNot { it.id == 1L }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onNodeWithTag("clipboard-card:text:2").assertIsDisplayed()
        assertEquals(firstRecent.top, bounds("clipboard-card:text:11").top, 1f)
    }
}
