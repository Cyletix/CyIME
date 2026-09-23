package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class KeyGlowTest {
    @get:Rule val rule = createComposeRule()
    private fun snapshot(tag: String): Bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
    private fun save(name: String, bitmap: Bitmap) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun quickTapGlowsInsideKeyAndFullyDisappearsWithinHalfSecond() {
        var taps = 0
        rule.setContent { MaterialTheme {
            Box(Modifier.size(160.dp, 110.dp).background(Color.White).testTag("frame").padding(24.dp)) {
                KeyButton("A", { taps++ }, Color.DarkGray, Color.White, Modifier.testTag("key"), shadowEnabled = false)
            }
        } }
        rule.mainClock.autoAdvance = false
        val before = snapshot("frame")
        rule.onNodeWithTag("key").performTouchInput { down(center); up() }
        rule.mainClock.advanceTimeBy(96)
        val glow = snapshot("frame")
        assertFalse(before.sameAs(glow))
        // Outer margin and rounded corners stay unchanged: no particles may escape the cap.
        for (x in 0 until before.width) for (y in 0 until before.height) {
            if (before.getPixel(x, y) == android.graphics.Color.WHITE) assertEquals(before.getPixel(x, y), glow.getPixel(x, y))
        }
        save("key-glow-active.png", glow)
        rule.mainClock.advanceTimeBy(400)
        assertTrue("animation must finish within 500 ms", before.sameAs(snapshot("frame")))
        rule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun repeatedPressesRestartDecorationAndDisableRemovesItImmediately() {
        var enabled by mutableStateOf(true)
        var taps = 0
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalKeyboardInputPreferences provides KeyboardInputPreferences(keyGlowEnabled = enabled)) {
            KeyButton("A", { taps++ }, Color.DarkGray, Color.White, Modifier.size(100.dp, 60.dp).testTag("key"), shadowEnabled = false)
        } } }
        rule.mainClock.autoAdvance = false
        val before = snapshot("key")
        repeat(2) {
            rule.onNodeWithTag("key").performTouchInput { down(center); up() }
            rule.mainClock.advanceTimeBy(112)
            assertFalse(before.sameAs(snapshot("key")))
        }
        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(before.sameAs(snapshot("key")))
        rule.onNodeWithTag("key").performTouchInput { down(center); up() }
        rule.mainClock.advanceTimeBy(112)
        assertTrue(before.sameAs(snapshot("key")))
        rule.runOnIdle { assertEquals(3, taps) }
    }

    @Test fun textIconSwipeKanaAndSpaceKeysAllUseTheSameDecoration() {
        var taps = 0
        rule.setContent { MaterialTheme { Column(Modifier.width(160.dp)) {
            val color = Color.DarkGray
            KeyButton("A", { taps++ }, color, Color.White, Modifier.height(55.dp).testTag("plain"), shadowEnabled = false)
            SwipeableKeyButton("Q", { taps++ }, color, Color.White, Modifier.height(55.dp).testTag("swipe"), shadowEnabled = false)
            IconKeyButton(rememberVectorPainter(Icons.Default.Language), { taps++ }, color, Color.White, Modifier.height(55.dp).testTag("icon"), shadowEnabled = false)
            SwipeableIconKeyButton(rememberVectorPainter(Icons.Default.Language), { taps++ }, color, Color.White, Modifier.height(55.dp).testTag("swipe-icon"), shadowEnabled = false)
            KanaFlickButton(japaneseKanaKeys.first(), { taps++ }, color, Color.White, Modifier.height(55.dp).testTag("kana"), shadowEnabled = false)
            SpaceKeyButton({ taps++ }, color, Color.White, modifier = Modifier.height(55.dp).testTag("space"), shadowEnabled = false)
        } } }
        rule.mainClock.autoAdvance = false
        listOf("plain", "swipe", "icon", "swipe-icon", "kana", "space").forEach { tag ->
            val before = snapshot(tag)
            rule.onNodeWithTag(tag).performTouchInput { down(center); up() }
            rule.mainClock.advanceTimeBy(96)
            assertFalse("$tag missing glow", before.sameAs(snapshot(tag)))
            rule.mainClock.advanceTimeBy(416)
            assertTrue("$tag did not fade", before.sameAs(snapshot(tag)))
        }
        rule.runOnIdle { assertEquals(6, taps) }
    }

    @Test fun japaneseRowsShareCellSizesAndReadableLabelScale() {
        rule.setContent { MaterialTheme { JapaneseKanaKeyboardLayout({}, {}, Color(0xFF2C2D32), Color.White,
            Color(0xFF4A587E), modifier = Modifier.size(360.dp, 260.dp).testTag("japanese")) } }
        val sizes = listOf("kana-convert", "kana-number", "kana-symbol", "kana-key:a", "kana-key:wa", "kana-delete", "kana-space", "kana-enter")
            .map { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        sizes.forEach { assertEquals(sizes[0].width, it.width, 1f); assertEquals(sizes[0].height, it.height, 1f) }
        val fontSizes = listOf("変換", "123", "記号", "あ", "わ").map { label ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.single().layoutInput.style.fontSize.value
        }
        fontSizes.forEach { assertEquals(fontSizes[0], it, 0.1f) }
        save("japanese-key-proportions.png", snapshot("japanese"))
    }
}
