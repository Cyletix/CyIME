package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class T9GlideInputTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val controller = mock<T9InputController> {
        on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
    }
    private var editor = 1L
    private var input by mutableStateOf("")

    private fun show(enabled: Boolean, inPreeditEditor: Boolean = true) {
        AppFonts.initialize(rule.activity.application)
        val prefs = KeyboardInputPreferences(t9GlideTyping = enabled, t9FlickLetters = true)
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
            inputAdmissionTicket = { editor }, onPreeditKeyInput = {
                if (inPreeditEditor) input += it
                inPreeditEditor
            })
        rule.setContent {
            CompositionLocalProvider(LocalKeyboardInputPreferences provides prefs) {
                Column(Modifier.width(380.dp).testTag("keyboard")) {
                    CandidateBar(if (input.isEmpty()) CandidateBarState.Idle else
                        CandidateBarState.ChineseCandidates(listOf("来", "赖", "莱"), inputText = input),
                        visuals = CandidateBarVisuals(Color.DarkGray, Color.White, Color.Gray),
                        callbacks = CandidateBarCallbacks(onCandidateSelect = {}))
                    T9KeyboardLayout({}, callbacks, KeyboardUiState(currentSchemaId = "t9_pinyin"),
                        controller, Color.DarkGray, Color.White, Color.Gray, modifier = Modifier.height(250.dp))
                }
            }
        }
    }
    private val board get() = rule.onNodeWithTag("keyboard")
    private fun centerOf(text: String) = rule.onNodeWithText(text, ignoreCase = true, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.center - board.fetchSemanticsNode().boundsInRoot.topLeft

    @Test fun tapAndCrossKeyStrokeComposeExactlyOnce() {
        show(true)
        val five = centerOf("JKL"); val two = centerOf("ABC"); val four = centerOf("GHI")
        board.performTouchInput { down(five); moveTo(two); moveTo(four) }
        assertEquals("", input)
        System.getenv("CYIME_RENDER_OUT")?.let { dir ->
            val root = rule.activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(image)) }
            val bounds = board.fetchSemanticsNode().boundsInWindow
            File(dir, "nine-glide.png").outputStream().use {
                Bitmap.createBitmap(image, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(),
                    bounds.height.toInt()).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        board.performTouchInput { up() }
        // 抬手后在后台按词表比对整条线（来 lai = 524），再按点按同一路径送进去。
        rule.waitUntil(5000) { input.isNotEmpty() }
        assertEquals("524", input)
        board.performTouchInput { click(four) }
        assertEquals("5244", input)
        // 别的用户 11-03：从 9 直线划到 4 会路过 8 或 5，以前都算进去；现在只出 94（xi、yi、zi）。
        val nine = centerOf("WXYZ")
        board.performTouchInput { down(nine); moveTo(lerp(nine, four, 0.5f)); moveTo(four); up() }
        rule.waitUntil(5000) { input.length > 4 }
        assertEquals("524494", input)
    }

    @Test fun leavingDigitsCancellingOrChangingEditorDropsTheStroke() {
        show(true)
        val five = centerOf("JKL"); val six = centerOf("MNO")
        val rail = rule.onNodeWithTag("t9-left-rail").fetchSemanticsNode().boundsInRoot.center -
            board.fetchSemanticsNode().boundsInRoot.topLeft
        board.performTouchInput { down(five); moveTo(six); moveTo(rail); up() }
        assertEquals("left digit grid", "", input)
        board.performTouchInput { down(five); moveTo(six); cancel() }
        assertEquals("cancelled", "", input)
        board.performTouchInput { down(five); moveTo(six) }
        rule.runOnIdle { editor++ }
        board.performTouchInput { up() }
        assertEquals("editor changed", "", input)
        board.performTouchInput { down(five); moveTo(six); down(1, five + Offset(1f, 0f)); up(1); up() }
        assertEquals("", input)
    }

    @Test fun disabledGlideDoesNotFeedCrossedDigits() {
        show(false)
        val five = centerOf("JKL"); val six = centerOf("MNO")
        board.performTouchInput { down(five); moveTo(six); up() }
        org.junit.Assert.assertFalse(input.contains("56"))
    }

    @Test fun ordinaryFieldUsesNormalDigitQueueAndSingleKeyFlick() {
        show(true, inPreeditEditor = false)
        val five = centerOf("JKL"); val two = centerOf("ABC"); val four = centerOf("GHI")
        board.performTouchInput { down(five); moveTo(two); moveTo(four); up() }
        rule.waitUntil(5000) { org.mockito.Mockito.mockingDetails(controller).invocations.count { it.method.name == "onDigitPressed" } >= 3 }
        inOrder(controller) {
            verify(controller).onDigitPressed("5")
            verify(controller).onDigitPressed("2")
            verify(controller).onDigitPressed("4")
        }
        board.performTouchInput { down(five); moveTo(five + Offset(28f, 0f)); up() }
        verify(controller).onLetterFlicked("L")
    }

    @Test fun nineKeySettingsAreIndependentOfLetterKeyboardGlide() {
        rule.setContent { com.kingzcheung.xime.ui.settings.LayoutInputSettingsContent(
            com.kingzcheung.xime.ui.settings.LayoutInputCategory.Nine, onBack = {}) }
        val row = rule.onNodeWithText("九键滑行输入", useUnmergedTree = true).performScrollTo()
        org.junit.Assert.assertFalse(KeyboardInputPreferences.read(rule.activity).t9GlideTyping)
        row.performTouchInput { click() }
        rule.runOnIdle {
            val prefs = KeyboardInputPreferences.read(rule.activity)
            org.junit.Assert.assertTrue(prefs.t9GlideTyping)
            org.junit.Assert.assertFalse(prefs.chineseGlideTyping)
            org.junit.Assert.assertNotNull(InputGestureLocks.lockFor(prefs, InputGestureLock.Option.CURSOR_GESTURE,
                CursorGestureMode.KEYBOARD))
        }
    }
}
