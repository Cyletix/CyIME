package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Dragging the floating card onto the bottom edge: what the outline preview shows on release is what
 * happens. Released the moment the outline reaches the docked shape, it docks; released while it is
 * still morphing, it stays floating (user, 2026-10-23: the animation had finished but it did not dock).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-mdpi")
class FloatingDockReleaseTest {
    @get:Rule val rule = createComposeRule()
    private var density = 1f
    private var docks = 0

    private fun showFloating(hostHeightDp: Int = 328, initialOffsetY: Int = 0) {
        rule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                var floating by remember { mutableStateOf(true) }
                Box(Modifier.size(340.dp, hostHeightDp.dp)) {
                    FloatingKeyboardContainer(
                        isFloatingMode = floating, scaleFactor = 0.85f, fontScaleFactor = 1f,
                        contentHeightDp = 300, offsetX = 0, offsetY = initialOffsetY, onDrag = { _, _ -> }, onDragEnd = {},
                        onDock = { docks++; floating = false },
                        dockTarget = KeyboardDockTarget(340, 328, 0, 0, 0),
                    ) { Box(Modifier.fillMaxSize().testTag("keyboard-content")) }
                }
            }
        }
    }

    private fun previewState(): String? = rule.onNodeWithTag("floating-dock-preview", useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test fun releasedAsTheOutlineReachesTheDockedShapeItDocks() {
        showFloating()
        // 贴底改按纵向拖动距离判定（横移保留原模式）：在底边上往下拖满一次行程。
        rule.onNodeWithTag("floating-drag-bar").performTouchInput { down(center); moveBy(Offset(0f, 160f * density)) }
        // The first frame the preview shows the docked shape, release.
        rule.waitUntil(3_000) { runCatching { previewState() == "ready" }.getOrDefault(false) }
        rule.onNodeWithTag("floating-drag-bar").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, docks)
    }

    @Test fun releasedWhileTheOutlineIsStillMorphingItStaysFloating() {
        showFloating()
        // Grab, slide onto the edge and let go at once: the outline has barely started.
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center); moveBy(Offset(24f * density, 0f)); advanceEventTime(100); up()
        }
        rule.waitForIdle()
        assertEquals(0, docks)
    }

    @Test fun diagonalDragToBottomSurvivesLiftOffJitterAndDocks() {
        showFloating(hostHeightDp = 440, initialOffsetY = 80)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center)
            // 先跨过触摸阈值，再斜向拖到底边（尚未满 150ms：取消渐变动画，不应提前绘制半成品预览框）。
            moveBy(Offset(0f, 16f * density))
            moveBy(Offset(32f * density, 80f * density))
        }
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("floating-dock-preview", useUnmergedTree = true).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(FLOATING_DOCK_DURATION_MILLIS + 64L)
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("ready", previewState())
        val finalBounds = rule.onNodeWithTag("floating-dock-preview", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(340f, (finalBounds.right - finalBounds.left).value, 1f)
        assertEquals(328f, (finalBounds.bottom - finalBounds.top).value, 1f)
        // 模拟松手瞬间轻微右上回弹抖动后再抬起手指，仍应完成还原。
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            moveBy(Offset(10f * density, -10f * density))
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, docks)
    }

    @Test fun quickDropToBottomWithoutWaitingStaysFloating() {
        showFloating(hostHeightDp = 440, initialOffsetY = 80)
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, 16f * density))
            moveBy(Offset(0f, 80f * density))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, docks)
    }

    @Test fun fastSwipeThroughBottomPastOvershootStillStaysFloating() {
        showFloating(hostHeightDp = 440, initialOffsetY = 80)
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, 16f * density))
            moveBy(Offset(0f, 150f * density))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, docks)
    }

    @Test fun slidingSidewaysAtBottomThenPushingDownArmsAndDocks() {
        showFloating(hostHeightDp = 440, initialOffsetY = 0)
        rule.onNodeWithTag("floating-drag-bar").performTouchInput {
            down(center)
            moveBy(Offset(48f * density, 0f))
            moveBy(Offset(0f, 36f * density))
        }
        rule.waitUntil(3_000) { runCatching { previewState() == "ready" }.getOrDefault(false) }
        rule.onNodeWithTag("floating-drag-bar").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, docks)
    }

    @Test fun draggingToolbarMovesFloatingKeyboardWithoutTriggeringClicks() {
        val drags = mutableListOf<Pair<Float, Float>>()
        var emojiClicks = 0
        rule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                Box(Modifier.size(340.dp, 500.dp)) {
                    FloatingKeyboardContainer(
                        isFloatingMode = true, scaleFactor = 0.85f, fontScaleFactor = 1f,
                        contentHeightDp = 240, offsetX = 0, offsetY = 120,
                        onDrag = { dx, dy -> drags += dx to dy }, onDragEnd = {},
                        onDock = { docks++ },
                        dockTarget = KeyboardDockTarget(340, 280, 0, 0, 0),
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                            CandidateBar(
                                state = CandidateBarState.Idle,
                                toolbarActions = listOf(
                                    ToolbarAction(ToolbarButtonItem.Builtin(ToolbarButton.EMOJI), onClick = { emojiClicks++ }),
                                    ToolbarAction(ToolbarButtonItem.Builtin(ToolbarButton.SETTINGS), onClick = {}),
                                ),
                                visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray),
                                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onLogoClick = {}),
                                isFloatingMode = true,
                            )
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // 轻点工具栏按钮仍正常触发点击，不产生位移。
        rule.onNodeWithTag("toolbar-visual:emoji", useUnmergedTree = true).performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, emojiClicks)
        assertEquals(0, drags.size)

        // 从任务栏按钮按下并拖动，应移动悬浮键盘且不误触发按钮点击。
        rule.onNodeWithTag("toolbar-visual:emoji", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(20f * density, 40f * density))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, emojiClicks)
        assertEquals(1, drags.size)
        assertTrue("toolbar drag shifts horizontally/vertically: $drags", drags.single().second < -20f)
    }
}
