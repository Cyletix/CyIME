package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JapaneseLayoutActionsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun fourEqualRowsWithConversionFlickAndContextModifier() {
        val actions = mutableListOf<String>()
        var composing by mutableStateOf(false)
        rule.setContent { MaterialTheme { JapaneseKanaKeyboardLayout(
            onKanaAction = { if (it is JapaneseKanaAction.Modify) actions += "modify" },
            onKeyPress = { actions += it }, keyBackgroundColor = Color.DarkGray,
            keyTextColor = Color.White, specialKeyBackgroundColor = Color.Blue,
            modifier = Modifier.size(360.dp, 260.dp), hasKanaInput = composing,
        ) } }
        val left = listOf(rule.onNodeWithTag("kana-convert"), rule.onNodeWithTag("kana-left"), rule.onNodeWithTag("kana-number"), rule.onNodeWithTag("kana-symbol"))
        val right = listOf(rule.onNodeWithTag("kana-delete"), rule.onNodeWithTag("kana-right"), rule.onNodeWithTag("kana-space"), rule.onNodeWithTag("kana-enter"))
        left.zip(right).forEach { (a, b) ->
            val aa = a.fetchSemanticsNode().boundsInRoot
            val bb = b.fetchSemanticsNode().boundsInRoot
            assertEquals(aa.center.y, bb.center.y, 1f)
        }
        val punct = rule.onNodeWithTag("kana-punctuation").fetchSemanticsNode().boundsInRoot
        val wa = rule.onNodeWithTag("kana-key:wa").fetchSemanticsNode().boundsInRoot
        assertEquals(wa.height, punct.height, 1f)
        assertTrue(punct.right <= wa.left)
        rule.onNodeWithTag("kana-convert").performClick()
        assertEquals("japanese_convert", actions.last())
        rule.onNodeWithTag("kana-convert").performTouchInput { down(center); moveTo(center + Offset(100f, 0f)); up() }
        assertEquals("japanese_undo", actions.last())
        rule.onNodeWithTag("kana-left").performTouchInput { longClick(durationMillis = 850) }
        assertTrue(actions.count { it == "japanese_left" } > 1)
        rule.runOnIdle { composing = true }
        rule.onNodeWithTag("kana-punctuation").assertDoesNotExist()
        rule.onNodeWithTag("kana-modifier").performClick()
        assertEquals("modify", actions.last())
    }
}
