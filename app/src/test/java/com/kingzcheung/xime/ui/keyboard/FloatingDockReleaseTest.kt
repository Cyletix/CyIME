package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
@Config(sdk = [34], application = android.app.Application::class)
class FloatingDockReleaseTest {
    @get:Rule val rule = createComposeRule()
    private var density = 1f
    private var docks = 0

    private fun showFloating() {
        rule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                var floating by remember { mutableStateOf(true) }
                Box(Modifier.size(340.dp, 328.dp)) {
                    FloatingKeyboardContainer(
                        isFloatingMode = floating, scaleFactor = 0.85f, fontScaleFactor = 1f,
                        contentHeightDp = 300, offsetX = 0, offsetY = 0, onDrag = { _, _ -> }, onDragEnd = {},
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
        rule.onNodeWithTag("floating-drag-bar").performTouchInput { down(center); moveBy(Offset(24f * density, 0f)) }
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
}
