package com.kingzcheung.xime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditKeyboardLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val panelHeight = mutableStateOf(260.dp)
    private val actions = mutableListOf<String>()

    private fun setPanel() {
        rule.setContent {
            Box(Modifier.fillMaxSize().clickable { }, contentAlignment = Alignment.Center) {
                EditKeyboardLayout(
                    onAction = { actions += it }, onBack = {},
                    backgroundColor = Color.Black, textColor = Color.White,
                    accentColor = Color.Blue, keyBgColor = Color.DarkGray,
                    modifier = Modifier.size(320.dp, panelHeight.value).testTag("edit-panel"),
                    shadowEnabled = false,
                )
            }
        }
    }

    @Test fun nineGridKeepsSquareCellsAndTheRequestedCornerActions() {
        setPanel()
        val rows = listOf(listOf("段首", "向上", "段尾"), listOf("向左", "选择", "向右"), listOf("复制", "向下", "粘贴"))
        val before = rows.map { row -> row.map { rule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot } }
        before.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, bounds ->
                assertEquals(bounds.width, bounds.height, 1f)
                assertEquals(before[0][columnIndex].center.x, bounds.center.x, 1f)
                assertEquals(row[0].center.y, bounds.center.y, 1f)
                if (rowIndex > 0) assertTrue(bounds.top >= before[rowIndex - 1][columnIndex].bottom)
                if (columnIndex > 0) assertTrue(bounds.left >= row[columnIndex - 1].right)
            }
        }
        rule.runOnIdle { panelHeight.value = 440.dp }
        rows.flatten().forEachIndexed { index, label ->
            val bounds = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.width, bounds.height, 1f)
            assertEquals(before[index / 3][index % 3].width, bounds.width, 1f)
        }
    }

    @Test fun sideActionsStayAtPanelEdgesAtBothShortAndTallHeights() {
        setPanel()
        listOf(180.dp, 440.dp).forEach { height ->
            rule.runOnIdle { panelHeight.value = height }
            val panel = rule.onNodeWithTag("edit-panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val delete = rule.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
            val enter = rule.onNodeWithContentDescription("回车").fetchSemanticsNode().boundsInRoot
            val all = rule.onNodeWithContentDescription("全选").fetchSemanticsNode().boundsInRoot
            val cut = rule.onNodeWithContentDescription("剪切").fetchSemanticsNode().boundsInRoot
            val back = rule.onNodeWithContentDescription("返回键盘").fetchSemanticsNode().boundsInRoot
            val unit = panel.width / 320f
            assertEquals(panel.right - 4 * unit, delete.right, 1f)
            assertEquals(panel.top + 2 * unit, delete.top, 1f)
            assertEquals(delete.right, enter.right, 1f)
            assertEquals(panel.bottom - 2 * unit, enter.bottom, 1f)
            assertEquals(panel.left + 4 * unit, all.left, 1f)
            assertEquals(all.left, cut.left, 1f)
            assertEquals(all.left, back.left, 1f)
            assertEquals(panel.bottom - 2 * unit, back.bottom, 1f)
            val left = rule.onNodeWithContentDescription("向左").fetchSemanticsNode().boundsInRoot
            val right = rule.onNodeWithContentDescription("向右").fetchSemanticsNode().boundsInRoot
            assertTrue(cut.right <= left.left)
            assertTrue(right.right <= delete.left)
        }
    }

    @Test fun selectionModeChangesBoundaryActionsAndSecondTapCancelsIt() {
        setPanel()
        rule.onNodeWithContentDescription("选择").performClick()
        rule.onNodeWithContentDescription("段首").performClick()
        rule.onNodeWithContentDescription("段尾").performClick()
        rule.onNodeWithContentDescription("向左").performClick()
        rule.onNodeWithContentDescription("取消选择").performClick()
        rule.onNodeWithContentDescription("段首").performClick()
        rule.runOnIdle {
            assertEquals(listOf("select_begin", "select_paragraph_start", "select_paragraph_end", "select_arrow_left", "select_end", "home"), actions)
        }
    }

    @Test fun copyPasteAndAuxiliaryEditingActionsRemainAvailable() {
        setPanel()
        listOf("复制", "粘贴", "全选", "剪切", "删除", "回车").forEach {
            rule.onNodeWithContentDescription(it).performClick()
        }
        rule.runOnIdle { assertEquals(listOf("copy", "paste", "select_all", "cut", "delete", "enter"), actions) }
    }
}
