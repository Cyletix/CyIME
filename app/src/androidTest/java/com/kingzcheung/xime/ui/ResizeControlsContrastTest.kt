package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ResizeControlsContrastTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun phoneControlsRemainOpaqueWhileKeyboardOpacityChanges() = checkControls(360, 300, false, 1f)
    @Test fun floatingControlsRemainReadableAndCanSwitchWithoutClosing() = checkControls(640, 360, true, 1f)
    @Test fun tabletControlsHaveLargerTextAndClearButtonBorders() = checkControls(760, 420, false, 1f)
    @Test fun narrowLowControlsDoNotOverlapWithLargeFont() = checkControls(280, 180, false, 1.3f)

    private fun checkControls(width: Int, height: Int, floating: Boolean, font: Float) {
        val vm = KeyboardViewModel(app)
        val opacity = mutableFloatStateOf(1f)
        val floatingState = mutableStateOf(floating)
        var modeChanges = 0
        var confirmed = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = width; screenHeightDp = 900 }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, font)) {
                MaterialTheme {
                    Box(Modifier.size(width.dp, height.dp).background(Color.White).testTag("resize-contrast-root")) {
                        KeyboardView(vm, KeyboardUiState(isDarkTheme = true, isFloatingMode = floatingState.value,
                            keyboardOpacity = opacity.floatValue, keyboardHeightDp = height,
                            floatingOffsetY = if (floatingState.value) 24 else 0, floatingMinOffsetY = 24,
                            floatingScreenHeightDp = 900),
                            KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.fillMaxSize(),
                            resizeOverlay = {
                                KeyboardResizeOverlay(height, height, 0, floatingState.value,
                                    initialOpacity = opacity.floatValue, onHeightChange = {}, onBottomPaddingChange = {},
                                    onOpacityChange = { opacity.floatValue = it }, onReset = {},
                                    onConfirm = { _, _, _, _ -> confirmed = true }, onCancel = {},
                                    onFloatingModeChange = { floatingState.value = it; modeChanges++ },
                                    modifier = Modifier.fillMaxSize())
                            })
                    }
                }
            }
        }
        rule.waitForIdle()
        save("resize-controls-before-$width-$height-$floating")
        val button = rule.onNodeWithTag("keyboard-resize-floating-button")
        val before = button.captureToImage().asAndroidBitmap()
        rule.onNodeWithTag("keyboard-opacity-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
        rule.waitForIdle()
        val after = button.captureToImage().asAndroidBitmap()
        // Save the complete layout before assertions so a failure always has visual evidence.
        save("resize-controls-$width-$height-$floating")
        saveBitmap("resize-mode-before-$width-$height-$floating", before)
        saveBitmap("resize-mode-after-$width-$height-$floating", after)
        rule.runOnIdle { assertEquals(0.3f, opacity.floatValue, 0.01f) }
        // The rectangular capture includes transparent pixels outside the rounded button.
        // Those should reveal the changing keyboard; compare its opaque center, including text.
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        val inset = minOf(26, before.width / 3)
        var changedInteriorPixels = 0
        for (y in 3 until before.height - 3) for (x in inset until before.width - inset) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changedInteriorPixels++
        }
        assertEquals("opaque control interior inherited keyboard transparency", 0, changedInteriorPixels)
        val image = button.captureToImage().toPixelMap()
        val panelPixel = image[image.width / 2, 5]
        assertTrue("mode button needs its own opaque dark fill: $panelPixel", panelPixel.red < 0.15f && panelPixel.green < 0.15f && panelPixel.blue < 0.15f)
        val border = image[image.width / 2, 0]
        assertTrue("mode button border missing", border.red > panelPixel.red + 0.15f)
        val reset = rule.onNodeWithContentDescription("重置").fetchSemanticsNode().boundsInRoot
        val mode = button.fetchSemanticsNode().boundsInRoot
        val confirm = rule.onNodeWithContentDescription("确认").fetchSemanticsNode().boundsInRoot
        val slider = rule.onNodeWithTag("keyboard-opacity-slider").fetchSemanticsNode().boundsInRoot
        assertTrue(slider.bottom <= reset.top)
        assertTrue(reset.right < mode.left && mode.right < confirm.left)
        assertEquals(reset.center.y, mode.center.y, 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("透明度 70%", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("opacity label overflow at $width x $height, floating=$floating, font=$font: " +
            "size=${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, " +
            "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}", layout.hasVisualOverflow)
        assertTrue(layout.layoutInput.style.fontSize.value >= 16f)
        if (floating) {
            rule.onNodeWithTag("floating-drag-bar").assertIsDisplayed()
            assertTrue(confirm.bottom <= height - 24)
        }
        rule.onNodeWithContentDescription("悬浮键盘").performClick()
        rule.runOnIdle { assertEquals(1, modeChanges); assertEquals(!floating, floatingState.value); assertFalse(confirmed) }
        rule.onNodeWithContentDescription("确认").assertIsDisplayed()
        rule.onNodeWithTag("keyboard-opacity-slider").assertIsDisplayed()
    }

    private fun save(name: String) = saveBitmap(name,
        rule.onNodeWithTag("resize-contrast-root").captureToImage().asAndroidBitmap())

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val directory = File(app.getExternalFilesDir(null), "cyime").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
