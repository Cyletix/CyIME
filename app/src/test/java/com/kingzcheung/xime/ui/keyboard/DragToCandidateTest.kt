package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.gloss.CandidateGlossary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 九键“下一项”拖到候选：拖出键盘区一半高后松手在候选上就选它（按住 Shift 的规则），键自己的左滑、点按不触发；
 * 没拖那么远，键原来的手势照旧。键的手势用与 ExclusiveKeyGesture 相同的“松手时判定、被消费就放弃”写法模拟。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class DragToCandidateTest {
    @get:Rule val rule = createComposeRule()

    private val targets = CandidateDropTargets()
    private val selected = mutableListOf<Int>()
    private var swapWhileHovering = false
    private var leftSwipes = 0
    private var taps = 0

    private fun setContent() {
        rule.setContent {
            // 键盘区 400×200dp：拖动超过半宽 200dp 或半高 100dp 才算拖动选词。
            val area = with(androidx.compose.ui.platform.LocalDensity.current) {
                androidx.compose.ui.geometry.Rect(0f, 0f, 400.dp.toPx(), 200.dp.toPx()) }
            Column {
                Row {
                    repeat(3) { index ->
                        Box(Modifier.size(80.dp, 40.dp).onGloballyPositioned {
                            targets.put("c$index", it.boundsInRoot()) {
                                swapWhileHovering = CandidateGlossary.swapHeld(screenShiftHeld = false)
                                selected += index
                            }
                        })
                    }
                }
                Spacer(Modifier.height(120.dp))
                Row {
                    Spacer(Modifier.width(160.dp))
                    Box(Modifier.size(80.dp, 60.dp).testTag("key").dragToCandidate(true, { area }, targets).pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    change.consume()
                                    val dx = change.position.x - down.position.x
                                    if (dx < -50f) leftSwipes++ else if (kotlin.math.abs(dx) < 10f) taps++
                                    break
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    @Test
    fun `releasing on a candidate selects it with shift semantics and swallows the key gesture`() {
        setContent()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(Offset(center.x - 100.dp.toPx(), center.y - 170.dp.toPx()))   // 落在第 2 个候选（x 80~160dp，y 0~40dp）
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(1), selected)
        assertTrue(swapWhileHovering)
        assertEquals(0, leftSwipes)
        assertFalse(CandidateGlossary.t9DragSwap.value)
    }

    @Test
    fun `left swipe and tap that end outside the candidate bar keep their old meaning`() {
        setContent()
        rule.onNodeWithTag("key").performTouchInput {
            down(center)
            moveTo(Offset(center.x - 100.dp.toPx(), center.y))
            up()
        }
        rule.onNodeWithTag("key").performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(1, leftSwipes)
        assertEquals(1, taps)
        assertTrue(selected.isEmpty())
    }
}
