package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w620dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RotatedShiftDragTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun oneLineFollowsShiftToTheTouchedLetterAndCandidate() {
        val app = rule.activity.application
        val commits = mutableListOf<String>()
        rule.setContent { RotatedShiftKeyboard(app, commits::add) }
        val host = rule.onNodeWithTag("shift-drag-host")
        val offset = host.fetchSemanticsNode().boundsInRoot.topLeft
        fun center(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot.center - offset
        val shift = center("shift-key")
        val letter = center("qwerty-key:l")
        val candidate = center("bar-candidate:1")
        val accent = KeyboardThemes.getAccentColor("soft_blue", true).toArgb()
        fun colorAt(point: Offset): Int {
            val position = host.fetchSemanticsNode().boundsInWindow.topLeft + point
            val root = rule.activity.window.decorView
            val pixels = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(Canvas(pixels)) }
            return pixels.getPixel(position.x.roundToInt(), position.y.roundToInt())
        }
        val mid = (shift + letter) / 2f
        host.performTouchInput { down(shift); moveTo(letter) }
        rule.waitForIdle()
        assertEquals("only the letter area paints here", accent, colorAt(mid))
        assertTrue(commits.isEmpty())
        host.performTouchInput { up() }
        assertEquals(listOf("L"), commits)
        assertNotEquals("line disappears on release", accent, colorAt(mid))
        host.performTouchInput { down(shift); moveTo(candidate) }
        rule.waitForIdle()
        assertEquals("candidate segment reaches the fingertip", Color.Magenta.toArgb(), colorAt(candidate))
        assertEquals("candidate ink stays in the bar", accent, colorAt((shift + candidate) / 2f))
        host.performTouchInput { up() }
        assertEquals(listOf("L", "candidate:1"), commits)
    }
}

@Composable
internal fun RotatedShiftKeyboard(app: Application, commit: (String) -> Unit, rotationDegrees: Float = -24f) {
    val vm = remember(app) {
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        KeyboardViewModel(app)
    }
    Box(Modifier.fillMaxSize().background(Color.DarkGray).testTag("shift-drag-host")) {
        FloatingKeyboardContainer(isFloatingMode = true, scaleFactor = .75f, resolvedWidthDp = 350,
            contentHeightDp = 225, offsetX = 0, offsetY = 100, rotationDegrees = rotationDegrees,
            onCornerResize = { commit("resize") }, onDrag = { _, _ -> commit("move") }, onDragEnd = {}) {
            Column {
                CandidateBar(state = CandidateBarState.ChineseCandidates(listOf("一", "二", "三"), inputText = "yi"),
                    visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray, Color.Magenta),
                    callbacks = CandidateBarCallbacks(onCandidateSelect = { commit("candidate:$it") }),
                    isFloatingMode = true, showPreeditPreview = false)
                KeyboardLayout({ commit(it); vm.onCharacterTyped() }, vm,
                    KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                    KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = true, isDarkTheme = true, themeId = "soft_blue"),
                    isAsciiMode = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
