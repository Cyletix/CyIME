package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 用户 2026-10-30：拖完悬浮键盘亮出的四角角标看着像调大小的把手，拖了却没反应。
 * 现在角标亮着时拖角就调大小（对角不动，和调节界面同一套上下限），点角打开调节界面。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w600dp-h800dp-mdpi")
class FloatingCornerResizeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun handleCoversTheBracketOutsideAndOnlyACornerInside() {
        val card = ResizeRect(100f, 200f, 500f, 500f)
        assertEquals(ResizeRect(76f, 176f, 112f, 212f), card.cornerHandleRect(ResizeHandle.TOP_LEFT, 24f, 12f))
        assertEquals(ResizeRect(488f, 176f, 524f, 212f), card.cornerHandleRect(ResizeHandle.TOP_RIGHT, 24f, 12f))
        assertEquals(ResizeRect(76f, 488f, 112f, 524f), card.cornerHandleRect(ResizeHandle.BOTTOM_LEFT, 24f, 12f))
        assertEquals(ResizeRect(488f, 488f, 524f, 524f), card.cornerHandleRect(ResizeHandle.BOTTOM_RIGHT, 24f, 12f))
        assertEquals(null, card.cornerHandleRect(ResizeHandle.TOP, 24f, 12f))
    }

    private fun limits(start: ResizeRect, density: Float = 1f) = floatingCornerResizeLimits(start,
        600f * density, 800f * density, 0f, 600, false, density)

    @Test fun draggingACornerMovesOnlyThatCornerWithinTheResizeLimits() {
        for (density in listOf(1f, 2.625f)) {
            val start = ResizeRect(100f, 300f, 520f, 600f).let {
                ResizeRect(it.left * density, it.top * density, it.right * density, it.bottom * density) }
            val l = limits(start, density)
            val (_, grown) = floatingCornerResizeStep(start, start, ResizeHandle.TOP_LEFT, -40f * density, -30f * density,
                l, null, false, density)
            assertEquals(start.right, grown.right, 0.01f)
            assertEquals(start.bottom, grown.bottom, 0.01f)
            assertEquals(start.left - 40f * density, grown.left, 0.01f)
            assertEquals(start.top - 30f * density, grown.top, 0.01f)
            // 往里缩到底停在下限：宽不小于悬浮最小宽，内容高不小于悬浮最小高。
            val (_, shrunk) = floatingCornerResizeStep(start, start, ResizeHandle.BOTTOM_RIGHT, -1000f * density,
                -1000f * density, l, null, false, density)
            assertEquals(start.left, shrunk.left, 0.01f)
            assertEquals(start.top, shrunk.top, 0.01f)
            assertEquals(floatingResizeMinWidthDp(600) * density, shrunk.width, 0.5f)
            assertEquals((FLOATING_RESIZE_MIN_HEIGHT_DP + FLOATING_DRAG_BAR_HEIGHT_DP) * density, shrunk.height, 0.5f)
        }
    }

    @Test fun aKeyboardAlreadySmallerThanTheFloorIsNotPushedBigger() {
        val start = ResizeRect(200f, 400f, 460f, 600f)
        val l = limits(start)
        assertTrue(l.minWidth <= start.width && l.minHeight <= start.height)
        val (_, same) = floatingCornerResizeStep(start, start, ResizeHandle.TOP_RIGHT, -5f, 5f, l, null, false, 1f)
        assertEquals(start, same)
    }

    @Test fun splitKeyboardCornersOnlyChangeTheHeight() {
        val start = ResizeRect(0f, 300f, 600f, 600f)
        val (_, shown) = floatingCornerResizeStep(start, start, ResizeHandle.TOP_LEFT, 80f, -40f, limits(start), null, true, 1f)
        assertEquals(start.left, shown.left, 0.01f)
        assertEquals(start.right, shown.right, 0.01f)
        assertEquals(start.top - 40f, shown.top, 0.01f)
    }

    private var density = 1f
    private val resized = mutableListOf<ResizeGeometry>()
    private var taps = 0
    private var dragEnds = 0

    private fun showFloating() {
        rule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                // 宿主比窗口小，底下的角标把手也在窗口里，点得到。
                Box(Modifier.size(600.dp, 600.dp)) {
                    // 宽 0.6 × 600 = 360dp，内容高 250dp + 28dp 拖动条，底边离宿主底 60dp，水平居中：卡片 120..480 × 262..540。
                    FloatingKeyboardContainer(
                        isFloatingMode = true, scaleFactor = 0.6f, contentHeightDp = 250,
                        offsetX = 0, offsetY = 60, onDrag = { _, _ -> }, onDragEnd = { dragEnds++ },
                        onCornerResize = { resized += it }, onCornerTap = { taps++ },
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        rule.waitForIdle()
    }

    /**
     * 停住时钟时往前走 [ms]：触摸回调里改的状态要先送出快照通知，重组器才会等下一帧；
     * 只调 advanceTimeBy 不会送，节点一直是旧的。走完再送一次、走一帧，让这段时间里动画结束时改的状态也落到节点上。
     */
    private fun advance(ms: Long) {
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(ms)
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
    }

    /** 用户截图里的路径：拖一下底部拖动条移动键盘，松手四角亮起。角标只亮一会儿，先停住时钟，停在淡出之前看把手。 */
    private fun lightUpCorners() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center)
            for (step in 1..5) moveTo(center + Offset(4f * step * density, 0f))
            up()
        }
        // 先确认拖动条确实收到了这次拖动，再看角标：分得清是手势没到，还是到了没亮。
        assertEquals("drag bar released once", 1, dragEnds)
        advance(300)
        assertCornersLit()
    }

    /** 不经过手势，直接亮起：亮着时节点是 lit，淡出完回到 off。 */
    @Test fun triggeringTheHighlightShowsLitUntilItFades() {
        lateinit var highlight: KeyboardPositionHighlight
        rule.setContent {
            highlight = rememberKeyboardPositionHighlight()
            val lit = highlight.lit || highlight.held
            Box(Modifier.size(10.dp).testTag("probe").semantics { stateDescription = if (lit) "lit" else "off" })
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { highlight.trigger() }
        advance(300)
        rule.onNodeWithTag("probe").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "lit"))
        advance(3_000)
        rule.onNodeWithTag("probe").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off"))
    }

    /** 先看角标本身亮没亮，再找把手：分得清是没亮起来，还是亮了没出把手。 */
    private fun assertCornersLit() {
        rule.onNodeWithTag("floating-position-corners", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "lit"))
    }

    @Test fun tappingTheDragBarLightsTheCorners() {
        showFloating()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("floating-drag-bar").performTouchInput { click(center) }
        advance(300)
        assertCornersLit()
        rule.onNodeWithTag("floating-corner-handle-top_left").assertExists()
    }

    @Test fun cornersAreOnlyHandlesWhileTheyAreLit() {
        showFloating()
        rule.onNodeWithTag("floating-corner-handle-top_left").assertDoesNotExist()
        rule.onNodeWithTag("floating-position-corners", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off"))
        lightUpCorners()
        for (corner in listOf("top_left", "top_right", "bottom_left", "bottom_right")) {
            rule.onNodeWithTag("floating-corner-handle-$corner").assertExists()
        }
        advance(3_000)
        rule.onNodeWithTag("floating-corner-handle-top_left").assertDoesNotExist()
    }

    @Test fun draggingALitCornerResizesWithTheOppositeCornerFixed() {
        showFloating()
        lightUpCorners()
        rule.onNodeWithTag("floating-corner-handle-top_left").performTouchInput {
            down(center)
            for (step in 1..10) moveTo(center + Offset(-6f * step * density, -6f * step * density))
            up()
        }
        advance(100)
        assertEquals(1, resized.size)
        val g = resized.single()
        // 右边和底边不动：右边 = 宿主中线 + 水平偏移 + 半宽 = 480dp，底边离宿主底 60dp。
        assertEquals(480f, 300f + g.horizontalOffsetDp + g.widthDp / 2f, 1f)
        assertEquals(60, g.bottomOffsetDp)
        assertTrue("wider: ${g.widthDp}", g.widthDp in 400..420)
        assertTrue("taller: ${g.heightDp}", g.heightDp in 290..310)
        assertEquals(0, taps)
    }

    @Test fun tappingALitCornerOpensTheAdjustScreen() {
        showFloating()
        lightUpCorners()
        rule.onNodeWithTag("floating-corner-handle-bottom_right").performTouchInput { click(center) }
        advance(100)
        assertEquals(1, taps)
        assertEquals(0, resized.size)
    }
}
