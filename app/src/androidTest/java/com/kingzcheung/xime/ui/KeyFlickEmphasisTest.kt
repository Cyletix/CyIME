package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyFlickEmphasisTest {
    @get:Rule val rule = createComposeRule()

    @Test fun confirmedFlickIsLargerAndStillFitsSmallKeysAndLongLabels() {
        var width by mutableStateOf(46.dp)
        var height by mutableStateOf(56.dp)
        var label by mutableStateOf("1")
        var fontScale by mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                Row(Modifier.background(Color(0xff20212b)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width, height).background(Color(0xff4a455b), RoundedCornerShape(6.dp)).testTag("flick-key")) {
                        KeyFlickLabel(KeyFlickPreview(label, 1f, true), Color.White,
                            width.value, height.value, 10f, 18f)
                    }
                    Box(Modifier.size(width, height).background(Color(0xff383541), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("q", color = Color.White, fontSize = 18.sp, modifier = Modifier.testTag("normal"))
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("key-flick-preview").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(20.7f, layouts.single().layoutInput.style.fontSize.value, .01f)
        saveUiEvidence("flick-larger-character", rule.onRoot())
        rule.runOnIdle { width = 28.dp; height = 30.dp; label = "清空"; fontScale = 2f }
        layouts.clear()
        rule.onNodeWithTag("key-flick-preview").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        val key = rule.onNodeWithTag("flick-key").fetchSemanticsNode().boundsInRoot
        val preview = rule.onNodeWithTag("key-flick-preview").fetchSemanticsNode().boundsInRoot
        assertTrue(preview.left >= key.left && preview.right <= key.right && preview.top >= key.top && preview.bottom <= key.bottom)
    }
}
