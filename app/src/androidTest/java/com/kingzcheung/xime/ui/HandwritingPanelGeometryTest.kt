package com.kingzcheung.xime.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HandwritingPanelGeometryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun sideKeysAndBottomRowShareFiveRowGridAtSmallAndLargeSizes() {
        val height = mutableStateOf(220.dp)
        val width = mutableStateOf(280.dp)
        rule.setContent { MaterialTheme {
            HandwritingKeyboardLayout(bottomPaddingDp = 0, modifier = Modifier.width(width.value).height(height.value))
        } }
        for ((w, h) in listOf(280.dp to 180.dp, 360.dp to 300.dp, 360.dp to 220.dp, 1000.dp to 300.dp)) {
            rule.runOnIdle { width.value = w; height.value = h }
            rule.waitForIdle()
            val footer = rule.onNodeWithTag("handwriting-bottom-row").fetchSemanticsNode().boundsInRoot
            val side = rule.onNodeWithTag("handwriting-side-keys").fetchSemanticsNode().boundsInRoot
            assertEquals(side.height / 4f, footer.height, 1f)
            val enter = rule.onNodeWithTag("handwriting-key:enter").fetchSemanticsNode().boundsInRoot
            for (key in listOf("delete", "？", "，", "。")) {
                val bounds = rule.onNodeWithTag("handwriting-key:$key").fetchSemanticsNode().boundsInRoot
                assertEquals(footer.height, bounds.height, 1f)
                assertEquals(enter.left, bounds.left, 1f)
                assertEquals(enter.right, bounds.right, 1f)
            }
            for (key in listOf("symbol", "number", "space", "expand", "ime_switch", "enter")) {
                val bounds = rule.onNodeWithTag("handwriting-key:$key").fetchSemanticsNode().boundsInRoot
                assertEquals(footer.top, bounds.top, 1f)
                assertEquals(footer.bottom, bounds.bottom, 1f)
            }
        }
    }

    @Test fun expandedPanelHasTwoAlignedRowsAndCanRestoreNormalControls() {
        val expanded = mutableStateOf(false)
        rule.setContent { MaterialTheme {
            HandwritingKeyboardLayout(expanded = expanded.value, bottomPaddingDp = 0,
                onKeyPress = { if (it == "expand") expanded.value = true else if (it == "collapse") expanded.value = false },
                modifier = Modifier.width(1000.dp).height(if (expanded.value) 650.dp else 300.dp))
        } }
        val original = rule.onNodeWithTag("handwriting-canvas").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("handwriting-key:expand").performClick()
        rule.onNodeWithTag("handwriting-side-keys").assertDoesNotExist()
        rule.onNodeWithTag("handwriting-key:number").assertExists()
        val canvas = rule.onNodeWithTag("handwriting-canvas").fetchSemanticsNode().boundsInRoot
        assertTrue(canvas.height > original.height * 1.5f)
        val bottom = rule.onNodeWithTag("handwriting-bottom-row").fetchSemanticsNode().boundsInRoot
        val symbols = rule.onNodeWithTag("handwriting-symbol-row").fetchSemanticsNode().boundsInRoot
        assertEquals(symbols.bottom, bottom.top, 1f)
        assertEquals(symbols.height, bottom.height, 1f)
        assertEquals(symbols.left, bottom.left, 1f)
        assertEquals(symbols.right, bottom.right, 1f)
        for (key in listOf("；", "：", "！", "？", "，", "。", "delete")) {
            val bounds = rule.onNodeWithTag("handwriting-key:$key").fetchSemanticsNode().boundsInRoot
            assertEquals(symbols.top, bounds.top, 1f)
            assertEquals(symbols.bottom, bounds.bottom, 1f)
        }
        val delete = rule.onNodeWithTag("handwriting-key:delete").fetchSemanticsNode().boundsInRoot
        val enter = rule.onNodeWithTag("handwriting-key:enter").fetchSemanticsNode().boundsInRoot
        assertEquals(delete.left, enter.left, 1f)
        assertEquals(delete.right, enter.right, 1f)
        for (key in listOf("symbol", "number", "space", "collapse", "ime_switch", "enter")) {
            val bounds = rule.onNodeWithTag("handwriting-key:$key").fetchSemanticsNode().boundsInRoot
            assertEquals(bottom.top, bounds.top, 1f)
            assertEquals(bottom.bottom, bounds.bottom, 1f)
        }
        rule.onNodeWithTag("handwriting-key:collapse").performClick()
        rule.onNodeWithTag("handwriting-key:number").assertExists()
        assertEquals(original, rule.onNodeWithTag("handwriting-canvas").fetchSemanticsNode().boundsInRoot)
    }
}
