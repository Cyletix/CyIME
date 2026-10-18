package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.menubar.LongPressMenuEntry
import com.kingzcheung.xime.ui.menubar.LongPressMenuOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 剪贴板/快捷发送词条长按操作菜单的布局回归。
 *
 * 菜单卡片必须被列表区可用高度约束：短键盘（列表区变矮）时整卡改为滚动，
 * 「删除」等最后一项仍能滚动到并点击，任何一项都不允许画到面板之外。
 */
class ClipboardLongPressMenuTest {
    @get:Rule val rule = createComposeRule()

    private val menuLabels = listOf("拆词", "快捷", "多选", "删除")

    @Test fun shortPanelKeepsEveryMenuItemInsidePanel() = assertMenuInsidePanel(panelHeightDp = 120)

    @Test fun regularPanelKeepsEveryMenuItemInsidePanel() = assertMenuInsidePanel(panelHeightDp = 240)

    @Test fun rtlKeepsThePhysicalAnchorOnTheSameSide() = assertMenuInsidePanel(panelHeightDp = 240, rtl = true)

    private fun assertMenuInsidePanel(panelHeightDp: Int, rtl: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MaterialTheme {
                Box(Modifier.size(width = 320.dp, height = panelHeightDp.dp).testTag("menu-panel")) {
                    LongPressMenuOverlay(
                        anchor = Rect(240f, 8f, 310f, 70f),
                        occupiedBounds = listOf(Rect(240f, 8f, 310f, 70f)),
                        onDismiss = {},
                        menuItems = menuLabels.map { label ->
                            LongPressMenuEntry(icon = Icons.Default.Delete, label = label, onClick = {})
                        }
                    )
                }
            }
            }
        }
        val panel = rule.onNodeWithTag("menu-panel").fetchSemanticsNode().boundsInRoot
        val menu = rule.onNodeWithTag("clipboard-item-menu").fetchSemanticsNode().boundsInRoot
        assertTrue("physical anchor must not be mirrored in RTL", menu.right <= panel.left + 240f)
        menuLabels.forEach { label ->
            val item = rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$label 越出面板上边界", item.top >= panel.top - 1f)
            assertTrue("$label 越出面板下边界（最后一项点不到）", item.bottom <= panel.bottom + 1f)
        }
    }
}
