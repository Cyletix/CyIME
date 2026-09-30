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
    @Test fun phoneCanNarrowAndDockOnEitherSide() = checkResize(360)
    @Test fun tabletCanNarrowAndDockOnEitherSide() = checkResize(900)

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
                assertEquals(if (px in left until right) android.graphics.Color.BLUE else android.graphics.Color.MAGENTA,
                    image.getPixel(px, image.height / 2))
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
            if (width <= FLOATING_RESIZE_MIN_WIDTH_DP) assertEquals(width.toFloat(), rect.width, 1f)
            else {
                // Drag recognition consumes platform touch slop before resizing begins.
                assertTrue("Right edge narrows without crossing the shared minimum: $rect", rect.width < width - 40)
                assertTrue(rect.width >= FLOATING_RESIZE_MIN_WIDTH_DP)
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
