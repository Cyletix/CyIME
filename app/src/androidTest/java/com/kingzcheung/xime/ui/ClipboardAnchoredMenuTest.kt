package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The long-press card opens right beside the pressed record (left, middle and right columns, top
 * and bottom rows, phone and tablet widths), and the delete confirmation replaces it in place.
 */
class ClipboardAnchoredMenuTest {
    @get:Rule val rule = createComposeRule()

    @Test fun phoneWidth() = check(360, 300, "phone")

    @Test fun tabletWidth() = check(800, 360, "tablet")

    private fun check(width: Int, height: Int, name: String) {
        val records = (1L..30L).map { ClipboardItem(it, "剪贴板记录 $it：Answer the following question", timestamp = 100 - it) }
        val size = mutableStateOf(width to height)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences()) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.requiredSize(size.value.first.dp, size.value.second.dp)) {
                        ClipboardBoardView(records, emptyList(), false, false, emptySet(), false, null,
                            onBack = {}, onQuickSend = {}, onSelectText = {}, onSelectImage = {},
                            onSplit = { _, _ -> }, onAddQuick = {}, onPinsChange = {}, onRemove = {},
                            onPhotoAccess = {}, onPick = {}, onSystemPaste = {}, onShare = { _, _ -> },
                            onPullRemote = null)
                    }
                }
            }
        }
        rule.waitForIdle()
        val board = bounds("clipboard-board")
        val cards = records.mapNotNull { item ->
            val tag = "clipboard-card:text:${item.id}"
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { tag to it.boundsInRoot }
        }.filter { (_, r) -> r.top >= board.top && r.bottom <= board.bottom }
        val top = cards.minOf { it.second.top }
        val bottom = cards.maxOf { it.second.top }
        val picks = listOf("top" to top, "bottom" to bottom).flatMap { (row, y) ->
            val inRow = cards.filter { kotlin.math.abs(it.second.top - y) < 2f }.sortedBy { it.second.left }
            listOf("left" to inRow.first(), "middle" to inRow[inRow.size / 2], "right" to inRow.last())
                .distinctBy { it.second.first }.map { (column, card) -> "$row-$column" to card }
        }
        for ((label, card) in picks) {
            val (tag, record) = card
            rule.onNodeWithTag(tag).performTouchInput { longClick() }
            rule.waitForIdle()
            val menu = bounds("clipboard-item-menu")
            assertInside(board, menu, "$name $label menu stays inside the panel")
            assertTrue("$name $label menu sits beside its record, not across the panel: menu=$menu record=$record",
                kotlin.math.abs(menu.left - record.right) <= 12f || kotlin.math.abs(record.left - menu.right) <= 12f ||
                    menu.overlaps(record))
            assertTrue("$name $label menu starts level with its record", menu.top <= record.bottom)
            save("$name-$label")
            rule.onNodeWithTag("clipboard-item-menu-dismiss").performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, height - 4f)) }
            rule.waitForIdle()
        }
        // The delete confirmation takes the menu's place at the same size class.
        val (tag, record) = picks.first().second
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
        val menu = bounds("clipboard-item-menu")
        rule.onNodeWithText("删除").performClick()
        rule.waitForIdle()
        val confirm = bounds("clipboard-delete-menu")
        assertTrue("$name confirmation opens where the menu was", kotlin.math.abs(confirm.top - menu.top) <= 24f &&
            (kotlin.math.abs(confirm.left - menu.left) <= 80f || kotlin.math.abs(confirm.right - menu.right) <= 80f))
        assertTrue("$name confirmation is a normal touch size", confirm.width >= 200f && confirm.height >= 120f)
        rule.onNodeWithTag("clipboard-confirm-delete").assertIsDisplayed()
        save("$name-confirm")
        assertTrue(record.width > 0f)
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertInside(outer: Rect, inner: Rect, message: String) = assertTrue("$message: $inner in $outer",
        inner.left >= outer.left - 1f && inner.top >= outer.top - 1f && inner.right <= outer.right + 1f && inner.bottom <= outer.bottom + 1f)

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("clipboard-board").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "clipboard-anchored-menu").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
