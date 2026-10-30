package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 用户 2026-10-30：固定键盘往上拉变悬浮后，键盘不会跟着手指继续走。
 * 现在拉满就变悬浮，悬浮卡片接着跟同一根手指，松手停在手指那里（顶边正中在手指上方 [UNDOCK_FOLLOW_GRAB_DP]）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w600dp-h800dp-mdpi")
// 和 PositionHighlightRenderTest 里同一个上拉把手一样用真实图形：旧图形模式下触摸落在固定卡片（圆角裁切层）里的把手上，把手却收不到。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndockFollowTest {
    @get:Rule val rule = createComposeRule()

    private var density = 1f
    private var floating by mutableStateOf(false)
    private var dx = 0f
    private var dy = 0f
    private var dragEnds = 0
    private val follow = KeyboardUndockFollow()
    /** 固定键盘这一层在把手之后（Main 轮由内往外）看到的触摸：落点和是否已被把手消费。只用于失败信息。 */
    private val seen = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                CompositionLocalProvider(LocalKeyboardUndockFollow provides follow) {
                    // 外层一直在（像 KeyboardView 的根）；固定时宿主只有下面 200dp，悬浮时是整块 600×600。
                    Box(Modifier.size(600.dp, 600.dp).testTag("host").undockFollowTracker(follow)) {
                        Box((if (floating) Modifier.fillMaxSize() else Modifier.padding(top = 400.dp).size(600.dp, 200.dp))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        event.changes.forEach { c ->
                                            seen += "${event.type}@${c.position.x.roundToInt()},${c.position.y.roundToInt()}" +
                                                if (c.isConsumed) "*" else ""
                                        }
                                    }
                                }
                            }) {
                            // 悬浮：宽 0.6 × 600 = 360dp，高 150 + 28 = 178dp，贴宿主底边居中：卡片 120..480 × 422..600。
                            FloatingKeyboardContainer(
                                isFloatingMode = floating, scaleFactor = 0.6f, contentHeightDp = 150,
                                offsetX = 0, offsetY = 0,
                                onDrag = { x, y -> dx += x; dy += y }, onDragEnd = { dragEnds++ },
                                onUndock = { floating = true },
                            ) { Box(Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** 按把手实际位置取起点（宿主坐标）：把手顶边下 7dp、水平正中；应在 (300, 407)dp。 */
    private fun handleStart(): Offset {
        val handle = rule.onNodeWithTag("fixed-undock-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val host = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        return Offset(handle.center.x - host.left, handle.top + 7f * density - host.top)
    }

    @Test fun pullingUpTurnsFloatingAndTheCardKeepsFollowingTheFinger() {
        show()
        val start = handleStart()
        // 上拉 200dp：过了阻力（24 + 96dp）就变悬浮。
        rule.onNodeWithTag("host").performTouchInput {
            down(start)
            for (step in 1..20) moveTo(start + Offset(0f, -10f * step * density))
        }
        rule.waitForIdle()
        // 失败时带上把手的状态：ready 是拉够了却没切，docked 是拉的手势没到把手。
        val handle = runCatching {
            rule.onNodeWithTag("fixed-undock-handle", useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.StateDescription)
        }.getOrElse { "gone" }
        assertTrue("pulled past the threshold should already be floating (handle=$handle, " +
            "following=${follow.pointerId != null}, finger=${follow.finger}, start=$start, " +
            "docked layer saw=${seen.take(24)})", floating)
        // 同一根手指接着往右上走，再松手。
        rule.onNodeWithTag("host").performTouchInput {
            for (step in 1..10) moveTo(start + Offset(10f * step * density, (-200f - 5f * step) * density))
            up()
        }
        rule.waitForIdle()
        // 松手处 (400, 157)dp：卡片顶边正中在它上方 20dp 之下 → 卡片 220..580 × 137..315。
        // 相对原来的悬浮位置（120..480 × 422..600）：右移 100dp，上移 285dp。
        val finger = start / density + Offset(100f, -250f)
        assertEquals(1, dragEnds)
        assertEquals(finger.x - 180f - 120f, dx, 1.5f)
        assertEquals(422f - (finger.y - UNDOCK_FOLLOW_GRAB_DP), dy, 1.5f)
    }

    @Test fun aShortPullStaysDocked() {
        show()
        val start = handleStart()
        rule.onNodeWithTag("host").performTouchInput {
            down(start)
            for (step in 1..6) moveTo(start + Offset(0f, -10f * step * density))
            up()
        }
        rule.waitForIdle()
        assertEquals(false, floating)
        assertEquals(0, dragEnds)
    }
}
