package com.kingzcheung.xime.ui.render

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.service.DistanceBackspace
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

internal class DistanceDeleteScene {
    lateinit var field: EditText
    val value get() = field.text.toString()
    val original = "甲👩‍💻e\u0301乙尾"
    var taps = 0
    var clear = 0
    var undo = 0
    var finishedDrags = 0
    var pixelsPerDp = 1f
    /** The last touch's edit, kept after release like XimeInputMethodService.lastSwipeDelete. */
    var last: DistanceBackspace? = null
    fun reset() { field.setText(original); field.setSelection(original.length - 1) }
    @Composable fun Content() {
        pixelsPerDp = LocalDensity.current.density
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx -> EditText(ctx).also { field = it; reset(); it.showSoftInputOnFocus = false } },
                modifier = Modifier.align(Alignment.TopCenter).width(260.dp).height(64.dp))
            CompositionLocalProvider(LocalDeleteDrag provides {
                // Same contract as ImeDistanceBackspace: the first non-zero sign picks the direction.
                val ic = checkNotNull(field.onCreateInputConnection(EditorInfo()))
                var edit: DistanceBackspace? = null
                val apply: (Int) -> Unit = { signed ->
                    if (edit == null) edit = DistanceBackspace.open(ic, forward = signed < 0).also { last = it }
                    check(edit?.move(kotlin.math.abs(signed)) == true)
                }
                DeleteDragSession(apply) { finishedDrags++ }
            }) {
                Box(Modifier.size(76.dp, 56.dp)) {
                    SwipeableIconKeyButton(rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace), { taps++ },
                        Color.DarkGray, Color.White, Modifier.testTag("delete"), onLongClick = { taps++ },
                        onSwipeUp = { clear++ }, onSwipeDown = { undo++ }, distanceDelete = true)
                }
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class DistanceDeleteGestureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scene = DistanceDeleteScene()
    private fun open() {
        AppFonts.initialize(rule.activity)
        rule.setContent { scene.Content() }; rule.waitForIdle()
    }
    private val key get() = rule.onNodeWithTag("delete")
    private fun drag(x: Int, y: Int = 0) {
        key.performTouchInput { moveTo(center + Offset(x * scene.pixelsPerDp, y * scene.pixelsPerDp)) }
    }
    private fun text(expected: String) = rule.runOnIdle { assertEquals(expected, scene.value) }

    @Test fun editorChangingTheSelectionOrTextRefusesTheOldUndo() {
        open()
        rule.runOnIdle {
            val field = scene.field
            val ic = checkNotNull(field.onCreateInputConnection(EditorInfo()))
            val drag = checkNotNull(DistanceBackspace.open(ic))
            assertTrue(drag.move(1))
            field.setSelection(0)
            assertFalse(drag.move(0))
            field.setSelection(0, 1)
            assertNull(DistanceBackspace.open(ic))
            scene.reset()
            val second = checkNotNull(DistanceBackspace.open(ic))
            assertTrue(second.move(1))
            field.text.replace(0, 1, "另")
            assertFalse(second.move(0))
            assertEquals("另👩‍💻e\u0301尾", scene.value)
        }
    }

    @Test fun retracingRestoresOnlyThisTouchAndNeverSplitsAGrapheme() {
        open()
        // 30dp starts, then one more every 16dp (user 11-01: a little less sensitive).
        key.performTouchInput { down(center) }; drag(-20); text(scene.original)
        drag(-28); text(scene.original)
        drag(-32); text("甲👩‍💻e\u0301尾")
        drag(-30); text("甲👩‍💻e\u0301尾")
        drag(-64); text("甲尾")
        drag(-50); text("甲👩‍💻尾")
        drag(-10); text(scene.original)
        drag(-50); key.performTouchInput { up() }; text("甲👩‍💻尾")
        // Right swipe is the mirrored Delete: removes what follows the caret.
        key.performTouchInput { down(center) }; drag(50); key.performTouchInput { up() }; text("甲👩‍💻")
        rule.runOnIdle { assertEquals(0, scene.taps + scene.clear + scene.undo) }
    }

    @Test fun rightSwipeDeletesAfterTheCaretAndRetracesWithoutMovingIt() {
        open()
        rule.runOnIdle { scene.field.setSelection(1) }
        fun caret() = rule.runOnIdle { assertEquals(1, scene.field.selectionStart); assertEquals(1, scene.field.selectionEnd) }
        key.performTouchInput { down(center) }; drag(20); text(scene.original)
        drag(32); text("甲e\u0301乙尾"); caret()
        drag(64); text("甲尾"); caret()
        drag(50); text("甲乙尾"); caret()
        drag(10); text(scene.original); caret()
        drag(50); key.performTouchInput { up() }; text("甲乙尾"); caret()
        // Cancel restores; moving back past the start never turns into a backward delete.
        key.performTouchInput { down(center) }; drag(50); text("甲"); drag(-64); text("甲乙尾")
        drag(50); text("甲"); key.performTouchInput { cancel() }; text("甲乙尾"); caret()
        rule.runOnIdle {
            scene.reset(); scene.field.setSelection(1)
            val ic = checkNotNull(scene.field.onCreateInputConnection(EditorInfo()))
            val drag = checkNotNull(DistanceBackspace.open(ic, forward = true))
            assertTrue(drag.move(1))
            scene.field.setSelection(0)
            assertFalse(drag.move(0))
            assertEquals("甲e\u0301乙尾", scene.value)
        }
        rule.runOnIdle { assertEquals(0, scene.taps + scene.clear + scene.undo) }
    }

    @Test fun undoAfterReleasePutsTheSwipedTextBackWhereItWas() {
        // 别人 11-05：左滑删字松手后，撤回手势（同清空后的撤回）放回删掉的字。
        open()
        key.performTouchInput { down(center) }; drag(-64); key.performTouchInput { up() }; text("甲尾")
        rule.runOnIdle {
            val edit = checkNotNull(scene.last)
            assertTrue(edit.hasRemoved)
            assertTrue(edit.move(0))
            assertFalse(edit.hasRemoved)
            assertEquals(scene.original, scene.value)
            assertEquals(scene.original.length - 1, scene.field.selectionStart)
            assertEquals(scene.original.length - 1, scene.field.selectionEnd)
        }
        // Right swipe: the text comes back after the caret and the caret does not move.
        rule.runOnIdle { scene.field.setSelection(1) }
        key.performTouchInput { down(center) }; drag(64); key.performTouchInput { up() }; text("甲尾")
        rule.runOnIdle {
            val edit = checkNotNull(scene.last)
            assertTrue(edit.hasRemoved && edit.forward)
            assertTrue(edit.move(0))
            assertEquals(scene.original, scene.value)
            assertEquals(1, scene.field.selectionStart); assertEquals(1, scene.field.selectionEnd)
        }
        // Swiping back to the start leaves nothing to undo.
        rule.runOnIdle { scene.reset() }
        key.performTouchInput { down(center) }; drag(-64); drag(-10); key.performTouchInput { up() }; text(scene.original)
        rule.runOnIdle { assertFalse(checkNotNull(scene.last).hasRemoved) }
        // Typing after the swipe refuses the undo instead of inserting somewhere else.
        key.performTouchInput { down(center) }; drag(-64); key.performTouchInput { up() }; text("甲尾")
        rule.runOnIdle {
            scene.field.text.insert(scene.field.selectionStart, "新")
            assertFalse(checkNotNull(scene.last).move(0))
            assertEquals("甲新尾", scene.value)
        }
        rule.runOnIdle { assertEquals(0, scene.taps + scene.clear + scene.undo) }
    }

    @Test fun cancelRestoresButVerticalSwipeTapAndHoldStillHaveTheirOwnActions() {
        open()
        key.performTouchInput { down(center) }; drag(-64); text("甲尾")
        key.performTouchInput { cancel() }; text(scene.original)
        rule.runOnIdle { assertEquals(1, scene.finishedDrags) }
        key.performTouchInput { down(center) }; drag(-20); key.performTouchInput { up() }
        key.performTouchInput { down(center); up() }
        key.performTouchInput { down(center) }; drag(0, -90); key.performTouchInput { up() }
        key.performTouchInput { down(center) }; drag(0, 90); key.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, scene.taps); assertEquals(1, scene.clear); assertEquals(1, scene.undo) }
        rule.mainClock.autoAdvance = false
        key.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        drag(-54); key.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertTrue(scene.taps > 1); assertEquals(scene.original, scene.value) }
    }

    @Test fun smallSidewaysDriftKeepsTheTapAndTheLongPress() {
        // 用户 11-01：左滑删字打断长按 → 横移 16dp 以内照常点按、长按。
        open()
        key.performTouchInput { down(center) }; drag(-12); drag(12); key.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, scene.taps) }
        rule.mainClock.autoAdvance = false
        key.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100)
        drag(-12); rule.mainClock.advanceTimeBy(100)
        drag(12); rule.mainClock.advanceTimeBy(100)
        drag(-14); rule.mainClock.advanceTimeBy(800)
        key.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { assertTrue("long press kept repeating: ${scene.taps}", scene.taps > 4); assertEquals(scene.original, scene.value) }
    }
}
