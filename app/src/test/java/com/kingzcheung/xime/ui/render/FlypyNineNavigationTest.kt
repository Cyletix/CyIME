package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.widget.EditText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.gloss.CandidateGlossary
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.service.*
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "sw800dp-w1024dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlypyNineNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun tabletZeroSwitchesToNavigationAndDropsOnTheSecondWord() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        SettingsPreferences.setCandidateCommentMode(app, CandidateCommentMode.SHOW)
        SettingsPreferences.setCandidateCommentContent(app, CandidateCommentContent.GLOSS)
        CandidateGlossary.file(app).apply { parentFile!!.mkdirs(); writeText("电脑\tcomputer\n") }
        CandidateGlossary.prepare(app)
        rule.waitUntil(5_000) { CandidateGlossary.englishFor("电脑") == "computer" }
        val id = DoublePinyinScheme.t9Id("double_pinyin_flypy")
        val profile = InputProfiles.current(id)
        assertEquals(InputScheme.DOUBLE_PINYIN, profile.scheme)
        val candidate = mutableStateOf(CandidateState())
        val editor = EditText(rule.activity)
        val connection = editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        val vm = spy(KeyboardViewModel(app))
        val globalSelections = mutableListOf<Int>()
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> },
            onCommitText = { connection.commitText(it, 1) },
            onNextCandidate = { candidate.value = it.advanceT9Candidate(profile) },
            onPreviousCandidate = { candidate.value = it.retreatT9Candidate(profile) },
            onCandidateSelect = { index ->
                val word = candidate.value.candidates[index]
                val text = if (CandidateGlossary.swapHeld(false)) candidate.value.englishForHeldSelection(index, false) ?: word else word
                connection.commitText(text, 1)
            }, onGlobalCandidateSelect = { index ->
                globalSelections += index
                val word = candidate.value.expandedCandidates[index].text
                val text = if (CandidateGlossary.swapHeld(false)) candidate.value.englishForHeldSelection(index, true) ?: word else word
                connection.commitText(text, 1)
            })
        val t9 = mock<T9InputController> {
            on { leftPanelState } doReturn T9InputController.LeftPanelState.IDLE
            on { firstOptions } doReturn emptyList()
        }
        doReturn(t9).whenever(vm).bindT9Controller(callbacks)
        vm.dispatch(KeyboardDispatchAction.InputSessionStarted(false, id))
        rule.setContent {
            KeyboardView(vm, KeyboardUiState(currentSchemaId = id, keyboardHeightDp = 300),
                callbacks, Modifier.size(740.dp, 300.dp).testTag("keyboard"),
                candidateState = candidate, inputSessionManagedByHost = true)
        }
        val zero = rule.onNodeWithTag("t9-zero-key", true)
        val differentPages = CandidateState(candidates = listOf("电"),
            expandedCandidates = listOf(RimeCandidate("电脑", "")))
        assertNull(differentPages.englishForHeldSelection(0, false))
        assertEquals("computer", differentPages.englishForHeldSelection(0, true))
        assertNull(differentPages.englishForHeldSelection(1, true))
        zero.performTouchInput { click(center) }
        assertEquals("0", editor.text.toString())
        rule.runOnIdle {
            editor.setText("")
            candidate.value = CandidateState(inputText = "dm'nc", preeditText = "dm'nc", isComposing = true,
                candidates = listOf("电", "电脑", "点"), engineRevision = 1)
        }
        rule.onNodeWithContentDescription("下一项", true).assertIsDisplayed()
        zero.performTouchInput { click(center) }
        assertEquals(1, candidate.value.highlightedCandidateIndex)
        assertEquals("", editor.text.toString())
        fun drop(index: Int) {
            val hit = rule.onNodeWithTag("bar-candidate:$index", true).fetchSemanticsNode().boundsInRoot
            val origin = zero.fetchSemanticsNode().boundsInRoot.topLeft
            zero.performTouchInput { down(center); moveTo(hit.center - origin); up() }
        }
        drop(1)
        assertEquals("computer", editor.text.toString())
        assertFalse(CandidateGlossary.t9DragSwap.value)
        save()
        rule.runOnIdle {
            candidate.value = candidate.value.copy(expandedCandidatesLoaded = true,
                expandedCandidates = listOf("电", "银河", "电脑").map { RimeCandidate(it, "") })
            vm.setCandidateStripExtended(true)
        }
        drop(2)
        assertEquals(listOf(2), globalSelections)
        assertEquals("computercomputer", editor.text.toString())
        drop(0)
        assertEquals("computercomputer电", editor.text.toString())
        rule.runOnIdle { vm.setCandidateStripExtended(false); candidate.value = CandidateState() }
        rule.onNodeWithContentDescription("下一项", true).assertDoesNotExist()
        zero.performTouchInput { click(center) }
        assertEquals("computercomputer电0", editor.text.toString())
    }

    private fun save() {
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        val r = rule.onNodeWithTag("keyboard").fetchSemanticsNode().boundsInWindow
        File(out, "flypy-nine-next.png").outputStream().use {
            Bitmap.createBitmap(bitmap, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
