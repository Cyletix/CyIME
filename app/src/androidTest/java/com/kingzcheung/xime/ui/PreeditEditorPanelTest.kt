package com.kingzcheung.xime.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.rime.PinyinEditSession
import com.kingzcheung.xime.ui.keyboard.PreeditEditorBar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreeditEditorPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun editorIsAboveKeyboardAndNeverChangesItsBounds() {
        var show by mutableStateOf(false)
        var frame by mutableStateOf(PinyinEditSession(1, "rime_ice", "nihao", "", "", "nihao", 5, false))
        rule.setContent {
            Column { Spacer(Modifier.height(140.dp)); Column(Modifier.width(320.dp).height(240.dp).testTag("keyboard-body")) {
                if (show) PreeditEditorBar(frame, Color.DarkGray, Color.White, Color.Cyan, { show = false },
                    { frame = frame.copy(caret = it) }, { frame = frame.copy(text = it, caret = it.length) })
                Box(Modifier.height(40.dp).fillMaxWidth().testTag("candidate-row"))
                Box(Modifier.weight(1f).fillMaxWidth().testTag("original-nine-key"))
            } }
        }
        val before = rule.onNodeWithTag("original-nine-key").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { show = true }
        assertEquals(before, rule.onNodeWithTag("original-nine-key").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("preedit-editor").assertHeightIsEqualTo(44.dp)
        rule.onNodeWithTag("preedit-apply").assertDoesNotExist()
        rule.onNodeWithTag("preedit-letter:q").assertDoesNotExist()
        rule.onNodeWithTag("preedit-editor-code").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("zenm")) }
        rule.onNodeWithTag("preedit-editor-code").assertTextEquals("zenm")
        rule.onNodeWithTag("preedit-editor-close").performClick()
        rule.onNodeWithTag("preedit-editor").assertDoesNotExist()
        assertEquals(before, rule.onNodeWithTag("original-nine-key").fetchSemanticsNode().boundsInRoot)
    }
}
