package com.kingzcheung.xime.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.menubar.SchemaListView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchemaGridLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val schemas = listOf("中文九键", "小鹤双拼", "英文", "日语26键", "日语九宫格")
        .mapIndexed { index, name -> SchemaInfo("mode$index", name, "", "", "") }

    @Test fun fiveModesDoNotOverlapInShortTransparentPanelAndAllRemainReachable() = checkGrid(360, 176, false, 1f)
    @Test fun floatingPanelWithLargerTextStillFits() = checkGrid(280, 184, false, 1.3f)
    @Test fun landscapeModesFitInsteadOfSqueezingEveryModeIntoOneRow() = checkGrid(640, 180, true, 1.3f)

    @Test fun pageBoundaryKeepsTheSameGapWhileDragging() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    SchemaListView(schemas, "mode2", Color.Black, Color.Blue, Color.White, Color.DarkGray,
                        onSelectSchema = {}, onReorderSchemas = {}, modifier = Modifier.size(360.dp, 176.dp))
                }
            }
        }
        rule.onNodeWithTag("schema-pages", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(-100f, 0f))
        }
        val last = rule.onNodeWithTag("schema-tile:mode3").fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithTag("schema-tile:mode4").fetchSemanticsNode().boundsInRoot
        assertEquals(8f, next.left - last.right, 1f)
        rule.onNodeWithTag("schema-pages", useUnmergedTree = true).performTouchInput { cancel() }
    }

    private fun checkGrid(width: Int, height: Int, landscape: Boolean, fontScale: Float) {
        var selected = ""
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    SchemaListView(schemas, "mode2", Color.Black, Color.Blue, Color.White, Color.DarkGray,
                        onSelectSchema = { selected = it }, onReorderSchemas = {},
                        modifier = Modifier.size(width.dp, height.dp).graphicsLayer { alpha = 0.5f }.testTag("schema-panel"))
                }
            }
        }
        val panel = rule.onNodeWithTag("schema-panel").fetchSemanticsNode().boundsInRoot
        val firstPageCount = if (landscape) 5 else 4
        val bounds = schemas.take(firstPageCount).map { schema ->
            val card = rule.onNodeWithTag("schema-tile:${schema.schemaId}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(card.top >= panel.top && card.bottom <= panel.bottom)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(schema.name, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("${schema.name}不可垂直裁切", layouts.isNotEmpty() && layouts.none { it.didOverflowHeight })
            card
        }
        bounds.forEachIndexed { index, a -> bounds.drop(index + 1).forEach { b -> assertFalse(a.overlaps(b)) } }
        if (!landscape) rule.onNodeWithTag("schema-pages", useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.onNodeWithTag("schema-tile:mode4").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("mode4", selected) }
        rule.onNodeWithText("调整顺序").performClick()
        rule.onNodeWithTag("input-mode-order:mode4").performScrollTo().assertIsDisplayed()
    }
}
