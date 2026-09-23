package com.kingzcheung.xime.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class TabletSplitResizeTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("split_resize_test_$name", mode)
    }
    @Before fun before() {
        SettingsPreferences.getPrefsPublic(context).edit().clear().commit()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.reload(app)
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
    }
    @After fun after() { SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }

    @Test fun realFloatingKeyboardKeepsSystemFontScaleAndReadableKeycaps() {
        val vm = KeyboardViewModel(app)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 1200; screenHeightDp = 800; orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(1f, 1.15f)) {
                XimeTheme(darkTheme = true, themeId = "lavender_purple") {
                    // Keep the existing font-scale stress condition inside the production theme.
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1.15f)) {
                    // FloatingKeyboardContainer draws its own themed card, over a dark host surface.
                    Box(Modifier.size(1000.dp, 360.dp).testTag("tablet-real-keyboard")
                        .background(Color(0xFF101010))) {
                        KeyboardView(vm, KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = true,
                            isDarkTheme = true, themeId = "lavender_purple", isFloatingMode = true,
                            keyboardHeightDp = 330, floatingOffsetY = 0, floatingScreenHeightDp = 800),
                            KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.fillMaxSize())
                    }
                    }
                }
            }
        }
        rule.waitForIdle()
        save("tablet-real-floating-complete", "tablet-real-keyboard")
        val layout = textLayout("q", true)
        // Exercises the production KeyboardView caller plus FloatingKeyboardContainer and KeyButton.
        assertEquals("floating screen ratio must not be applied to font scale", 1.15f,
            layout.layoutInput.density.fontScale, 0.01f)
        assertTrue("floating letters are too small: ${layout.layoutInput.style.fontSize}",
            layout.layoutInput.style.fontSize.value >= 16f)
        assertFalse("q overflow: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}", layout.hasVisualOverflow)
        val card = rule.onNodeWithTag("floating-keyboard-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(card.width < 900f)
        rule.onNodeWithTag("split-keyboard", useUnmergedTree = true).assertDoesNotExist()
        save("tablet-real-floating-complete", "tablet-real-keyboard")
    }

    @Test fun landscapeUsesCompleteKeyboardUntilUserExplicitlyEnablesSplit() {
        val vm = KeyboardViewModel(app)
        val keys = mutableListOf<String>()
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 1000; screenHeightDp = 700; orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(1f)) {
                XimeTheme(darkTheme = true, themeId = "soft_blue") {
                    val theme = KeyboardThemes.getThemeById("soft_blue")
                    // Fixed IME background is drawn once by the service behind KeyboardView.
                    Box(Modifier.size(1000.dp, 360.dp).testTag("tablet-real-keyboard")
                        .keyboardBackground(theme.keyboardBackground, true,
                            KeyboardThemes.getKeyboardBackgroundColor(theme.id, true))) {
                        KeyboardView(vm, KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = true,
                            isDarkTheme = true, themeId = "soft_blue", keyboardHeightDp = 300),
                            KeyboardCallbacks(onKeyPress = { key, _ -> keys.add(key) }, onCandidateSelect = {}),
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        rule.onNodeWithTag("split-keyboard", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { SettingsPreferences.setSplitKeyboardEnabled(context, true) }
        rule.onNodeWithTag("split-keyboard-gap", useUnmergedTree = true).assertIsDisplayed()
        val layout = textLayout("q", true)
        assertTrue(layout.layoutInput.style.fontSize.value >= 18f)
        assertFalse("q overflow: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}", layout.hasVisualOverflow)
        rule.onNodeWithText("q", ignoreCase = true).performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("q"), keys) }
        save("tablet-real-manual-split", "tablet-real-keyboard")
        rule.runOnIdle { SettingsPreferences.setSplitKeyboardEnabled(context, false) }
        rule.onNodeWithTag("split-keyboard", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun narrowResizeControlsToggleSplitImmediatelyWithoutClosingOrOverlap() {
        var closes = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                MaterialTheme {
                    KeyboardResizeOverlay(180, 240, 0, false, onHeightChange = {}, onBottomPaddingChange = {},
                        onOpacityChange = {}, onReset = {}, onConfirm = { _, _, _, _ -> closes++ }, onCancel = {},
                        modifier = Modifier.size(280.dp, 180.dp).testTag("resize-test-root"),
                        onSplitKeyboardChange = { SettingsPreferences.setSplitKeyboardEnabled(context, it) })
                }
            }
        }
        rule.onNodeWithTag("keyboard-resize-split-button", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertTrue(SettingsPreferences.isSplitKeyboardEnabled(context)); assertEquals(0, closes) }
        rule.onNodeWithText("完整").assertIsDisplayed()
        save("resize-narrow-split-controls", "resize-test-root")
        val root = rule.onNodeWithTag("resize-test-root", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val floating = rule.onNodeWithTag("keyboard-resize-floating-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val split = rule.onNodeWithTag("keyboard-resize-split-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("keyboard-resize-opacity-panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val heightHandle = rule.onNodeWithTag("keyboard-resize-height-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val positionHandle = rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("compact height handle must remain touchable", heightHandle.height >= 40f)
        assertTrue("compact position handle must remain touchable", positionHandle.height >= 40f)
        assertTrue("opacity must not overlap height handles", panel.top >= heightHandle.bottom)
        assertTrue(panel.bottom <= floating.top)
        assertTrue(floating.bottom < split.top)
        assertTrue(split.bottom <= root.bottom)
        for (label in listOf("完整", "悬浮", "透明度 0%")) {
            val layout = textLayout(label)
            val details = "$label: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, lines=${layout.lineCount}, " +
                "exceededLines=${layout.multiParagraph.didExceedMaxLines}, lineEnd=${layout.getLineEnd(0)}, " +
                "fontScale=${layout.layoutInput.density.fontScale}, constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}"
            assertFalse(details, layout.hasVisualOverflow)
            assertEquals(details, label.length, layout.getLineEnd(0))
            if (label.length == 2) {
                val twoEmsPx = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() } * 2f
                assertTrue("Two glyphs need $twoEmsPx px; $details", layout.layoutInput.constraints.maxWidth >= twoEmsPx)
            }
        }
        save("resize-narrow-split-controls", "resize-test-root")
        rule.onNodeWithTag("keyboard-resize-split-button", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertFalse(SettingsPreferences.isSplitKeyboardEnabled(context)) }
    }

    @Test fun fixedResizeUsesExplicitHandlesAndUpdatesBeforeRelease() {
        val heights = mutableListOf<Int>()
        val gaps = mutableListOf<Int>()
        setResize(heights = heights, gaps = gaps)
        assertTrue(rule.onNodeWithTag("keyboard-resize-height-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height >= 48f)
        assertTrue(rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height >= 48f)
        rule.onNodeWithTag("resize-test-root", useUnmergedTree = true).performTouchInput {
            down(Offset(5f, 190f)); moveBy(Offset(0f, -50f), 80); up()
        }
        rule.runOnIdle { assertTrue(gaps.isEmpty()); assertTrue(heights.isEmpty()) }
        rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -40f), 80)
        }
        rule.runOnIdle { assertTrue("bottom gap must preview before pointer up", gaps.last() > 0) }
        rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).performTouchInput { up() }
        rule.onNodeWithTag("keyboard-resize-height-handle", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -40f), 80)
        }
        rule.runOnIdle { assertTrue("height must preview before pointer up", heights.last() > 240) }
        rule.onNodeWithTag("keyboard-resize-height-handle", useUnmergedTree = true).performTouchInput { up() }
    }

    @Test fun floatingPositionHandleMovesContinuouslyWithoutChangingHeight() {
        val heights = mutableListOf<Int>()
        val gaps = mutableListOf<Int>()
        val positions = mutableListOf<Offset>()
        var ends = 0
        setResize(true, heights, gaps, { x, y -> positions.add(Offset(x, y)) }, { ends++ })
        rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(40f, -30f), 80)
        }
        rule.runOnIdle {
            assertTrue(positions.isNotEmpty()); assertTrue(positions.last().x > 0); assertTrue(positions.last().y > 0)
            assertEquals(0, ends); assertTrue(gaps.isEmpty()); assertTrue(heights.isEmpty())
        }
        rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true).performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, ends) }
    }

    @Test fun repeatedMovesFollowRealOverlayGeometryBeforePointerUp() {
        val gap = mutableIntStateOf(0)
        val height = mutableIntStateOf(240)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 600.dp).testTag("moving-resize-root")) {
                        KeyboardResizeOverlay(240, 240, 0, false,
                            onHeightChange = { height.intValue = it },
                            onBottomPaddingChange = { gap.intValue = it }, onOpacityChange = {}, onReset = {},
                            onConfirm = { _, _, _, _ -> }, onCancel = {},
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                                .offset(y = (-gap.intValue).dp).height(height.intValue.dp))
                    }
                }
            }
        }
        val handle = rule.onNodeWithTag("keyboard-resize-position-handle", useUnmergedTree = true)
        val initial = handle.fetchSemanticsNode().boundsInRoot
        val root = rule.onNodeWithTag("moving-resize-root", useUnmergedTree = true)
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performTouchInput { down(initial.center - origin); moveBy(Offset(0f, -35f), 80) }
        rule.waitForIdle()
        val first = handle.fetchSemanticsNode().boundsInRoot
        assertTrue("position must change during the drag", first.top < initial.top)
        root.performTouchInput { moveBy(Offset(0f, -20f), 80) }
        rule.waitForIdle()
        val second = handle.fetchSemanticsNode().boundsInRoot
        assertEquals("moving coordinate space must not double or discard drag deltas", 20f, first.top - second.top, 2f)
        root.performTouchInput { moveBy(Offset(0f, -10f), 80) }
        rule.waitForIdle()
        val third = handle.fetchSemanticsNode().boundsInRoot
        assertEquals(10f, second.top - third.top, 2f)
        root.performTouchInput { up() }
        val heightHandle = rule.onNodeWithTag("keyboard-resize-height-handle", useUnmergedTree = true)
        val beforeResize = heightHandle.fetchSemanticsNode().boundsInRoot
        root.performTouchInput { down(beforeResize.center - origin); moveBy(Offset(0f, -35f), 80) }
        rule.waitForIdle()
        val resized = heightHandle.fetchSemanticsNode().boundsInRoot
        assertTrue(resized.top < beforeResize.top)
        root.performTouchInput { moveBy(Offset(0f, -20f), 80) }
        rule.waitForIdle()
        assertEquals(20f, resized.top - heightHandle.fetchSemanticsNode().boundsInRoot.top, 2f)
        root.performTouchInput { up() }
    }

    private fun setResize(floating: Boolean = false, heights: MutableList<Int>, gaps: MutableList<Int>,
        onMove: ((Float, Float) -> Unit)? = null, onEnd: (() -> Unit)? = null) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardResizeOverlay(240, 240, 0, floating,
                        onHeightChange = { heights.add(it) }, onBottomPaddingChange = { gaps.add(it) },
                        onOpacityChange = {}, onReset = {}, onConfirm = { _, _, _, _ -> }, onCancel = {},
                        modifier = Modifier.size(360.dp, 300.dp).testTag("resize-test-root"),
                        onPositionDrag = onMove, onPositionDragEnd = onEnd)
                }
            }
        }
    }
    private fun textLayout(text: String, ignoreCase: Boolean = false): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, ignoreCase = ignoreCase, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    private fun save(name: String, tag: String) {
        val folder = File(app.getExternalFilesDir(null), "cyime").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
