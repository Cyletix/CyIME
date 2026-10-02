package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FixedKeyboardResizeTest {
    @get:Rule val rule = createComposeRule()
    @Test fun innerCornerTargetResizesBothAxesInsteadOfMovingTheKeyboard() {
        val initial = ResizeRect(50f, 100f, 350f, 440f)
        var rect by mutableStateOf(initial)
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f),
                    LocalKeyboardResizePreviewState provides KeyboardResizePreviewState(
                        rect = rect, initialRect = initial, onRectChange = { rect = it },
                        bounds = ResizeRect(0f, 0f, 500f, 640f), fixedHeightRange = 160..600,
                    )) {
                    Box(Modifier.size(500.dp, 640.dp)) {
                        KeyboardResizeOverlay(initialHeightDp = 340, defaultHeightDp = 340,
                            currentBottomPaddingDp = 0, isFloatingMode = false,
                            onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {},
                            onReset = {}, onConfirm = { _, _, _, _ -> }, onCancel = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("keyboard-resize-frame").performTouchInput {
            // 32dp inward was outside the former 26dp target and under the move strip.
            swipe(Offset(initial.left + 32f, initial.top + 32f),
                Offset(initial.left - 8f, initial.top - 8f), durationMillis = 250)
        }
        rule.runOnIdle {
            assertTrue(rect.left < initial.left)
            assertTrue(rect.top < initial.top)
            assertEquals(initial.right, rect.right, 0.01f)
            assertEquals(initial.bottom, rect.bottom, 0.01f)
        }
    }

    @Test fun phoneCanNarrowAndDockOnEitherSide() = checkResize(360)
    @Test fun tabletCanNarrowAndDockOnEitherSide() = checkResize(900)

    @Test fun fixedMoveSnapsAtCenterThenSmallStepsBreakFreeAndConfirmSavesZeroOffset() {
        var rect by mutableStateOf(ResizeRect(0f, 200f, 280f, 540f))
        var slop = 0f
        var geometry: ResizeGeometry? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                slop = LocalViewConfiguration.current.touchSlop
                MaterialTheme {
                    Box(Modifier.size(360.dp, 540.dp)) {
                        CompositionLocalProvider(LocalKeyboardResizePreviewState provides
                            KeyboardResizePreviewState(rect = rect, onRectChange = { rect = it },
                                bounds = ResizeRect(0f, 0f, 360f, 540f))) {
                            KeyboardResizeOverlay(340, 340, 0, false,
                                onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                                onGeometryChange = { geometry = it }, onConfirm = { _, _, _, _ -> }, onCancel = {})
                        }
                    }
                }
            }
        }
        val bar = rule.onNodeWithTag("keyboard-resize-fixed-move-bar", useUnmergedTree = true)
        bar.performTouchInput { down(center); moveBy(Offset(40f + slop, 0f)) }
        rule.runOnIdle { assertEquals(180f, rect.centerX, 0.01f) }
        bar.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已居中"))
        bar.performTouchInput { repeat(30) { moveBy(Offset(0.5f, 0f)) } }
        rule.runOnIdle { assertEquals(195f, rect.centerX, 0.1f) }
        bar.performTouchInput { repeat(30) { moveBy(Offset(-0.5f, 0f)) }; up() }
        rule.onNodeWithContentDescription("确认").performClick()
        rule.runOnIdle {
            assertEquals(0, geometry!!.horizontalOffsetDp)
            assertEquals(280, geometry!!.widthDp)
            assertEquals(340, geometry!!.heightDp)
        }
    }

    @Test fun dragPreviewDoesNotRemeasureKeyboardUntilRelease() {
        val initial = ResizeRect(0f, 200f, 360f, 540f)
        val visual = mutableStateOf(initial)
        var settled by mutableStateOf(initial)
        var measures = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(360.dp, 540.dp)) {
                    FloatingKeyboardContainer(false, 1f, offsetX = 0, offsetY = 0,
                        contentHeightDp = 340, previewRect = settled, previewTransformRect = visual,
                        onDrag = { _, _ -> }, onDragEnd = {}) {
                        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                            measures++
                            val child = measurable.measure(constraints)
                            layout(child.width, child.height) { child.place(0, 0) }
                        })
                    }
                }
            }
        }
        var baseline = 0
        rule.runOnIdle { baseline = measures }
        repeat(10) { step ->
            rule.runOnIdle { visual.value = ResizeRect(0f, 200f - step * 4f, 360f, 540f) }
        }
        rule.runOnIdle { assertEquals(baseline, measures) }
        rule.runOnIdle { settled = visual.value }
        rule.runOnIdle { assertTrue(measures > baseline) }
    }

    @Test fun narrowedCardLeavesBothGuttersTransparentAndReportsItsActualBounds() {
        var offset by mutableStateOf(0)
        var reported: android.graphics.Rect? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp, 300.dp).background(Color.Magenta).testTag("host")) {
                    FloatingKeyboardContainer(false, 1f, offsetX = 0, offsetY = 0,
                        contentHeightDp = 300, fixedWidthDp = 400, fixedOffsetX = offset,
                        onDrag = { _, _ -> }, onDragEnd = {},
                        onCardPositioned = { l,t,r,b -> reported = android.graphics.Rect(l,t,r,b) }) {
                        Box(Modifier.fillMaxSize().background(Color.Blue))
                    }
                }
            }
        }
        for (x in listOf(0, -200, 200)) {
            rule.runOnIdle { offset = x }
            val node = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode()
            val host = rule.onNodeWithTag("host").fetchSemanticsNode()
            val left = (node.boundsInRoot.left - host.boundsInRoot.left).toInt()
            val right = (node.boundsInRoot.right - host.boundsInRoot.left).toInt()
            assertEquals(400, reported!!.width())
            val image = rule.onNodeWithTag("host").captureToImage().asAndroidBitmap()
            for (px in 0 until image.width) {
                val expected = if (px in left until right) android.graphics.Color.BLUE else android.graphics.Color.MAGENTA
                val actual = image.getPixel(px, image.height / 2)
                // Capturing a wide-gamut surface can round an sRGB channel by one.
                for (shift in listOf(0, 8, 16, 24)) assertTrue("pixel $px channel $shift",
                    kotlin.math.abs(((actual ushr shift) and 255) - ((expected ushr shift) and 255)) <= 2)
            }
        }
    }

    private fun checkResize(width: Int) {
        var rect by mutableStateOf(ResizeRect(0f, 200f, width.toFloat(), 540f))
        var resizing by mutableStateOf(true)
        var geometry: ResizeGeometry? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(width.dp, 540.dp)) {
                        FloatingKeyboardContainer(false, 1f, offsetX = 0, offsetY = 0,
                            contentHeightDp = 340, fixedWidthDp = geometry?.widthDp ?: 0,
                            fixedOffsetX = geometry?.horizontalOffsetDp ?: 0,
                            onDrag = { _, _ -> }, onDragEnd = {},
                            previewRect = if (resizing) rect else null) {
                            Box(Modifier.fillMaxSize().testTag("fixed-content"))
                        }
                        if (resizing) CompositionLocalProvider(LocalKeyboardResizePreviewState provides
                            KeyboardResizePreviewState(rect = rect, initialRect = rect, onRectChange = { rect = it },
                                bounds = ResizeRect(0f, 0f, width.toFloat(), 540f), fixedHeightRange = 180..500)) {
                            KeyboardResizeOverlay(340, 340, 0, false,
                                onHeightChange = {}, onBottomPaddingChange = {}, onOpacityChange = {}, onReset = {},
                                onGeometryChange = { geometry = it }, onConfirm = { _, _, floating, _ ->
                                    assertFalse(floating); resizing = false
                                }, onCancel = {})
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("keyboard-resize-frame", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width - 10f, 365f), Offset(width - 210f, 365f), 600)
        }
        rule.runOnIdle {
            val minimumWidth = floatingResizeMinWidthDp(width)
            if (width <= minimumWidth) assertEquals(width.toFloat(), rect.width, 1f)
            else {
                // Drag recognition consumes platform touch slop before resizing begins.
                assertTrue("Right edge narrows without crossing the shared minimum: $rect", rect.width < width - 40)
                assertTrue(rect.width >= minimumWidth)
            }
            assertEquals(0f, rect.left, 1f)
            assertEquals(340f, rect.height, 1f)
        }
        rule.onNodeWithTag("keyboard-resize-fixed-move-bar", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(500f, 0f), 350) }
        rule.runOnIdle { assertEquals(width.toFloat(), rect.right, 1f) }
        rule.onNodeWithTag("keyboard-resize-fixed-move-bar", useUnmergedTree = true).performTouchInput { swipe(center, center - Offset(500f, 0f), 350) }
        rule.runOnIdle { assertEquals(0f, rect.left, 1f) }
        rule.onNodeWithTag("keyboard-resize-fixed-move-bar", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(500f, 0f), 350) }
        rule.onNodeWithContentDescription("确认").performClick()
        val rendered = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode().boundsInRoot
        assertEquals(rect.width, rendered.width, 1f)
        assertEquals(rect.left, rendered.left, 1f)
        assertEquals(rect.right, rendered.right, 1f)
    }
}
