package com.kingzcheung.xime.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.*
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuLayoutRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val firstPage = listOf("剪贴板", "快捷发送", "输入方案", "表情", "定制工具栏", "键盘调节", "浅色模式", "部署方案")

    @Test fun compactPortraitGridDoesNotClipOrOverlapAndSettingsRemainReachable() {
        assertGrid(width = 360, height = 180, landscape = false, fontScale = 1f, expected = firstPage)
    }

    @Test fun floatingLargeTextUsesPagesInsteadOfClippingTwoRows() {
        assertGrid(width = 280, height = 150, landscape = false, fontScale = 1.5f, expected = firstPage.take(4))
    }

    @Test fun landscapeGridFitsTheAvailableHeight() {
        assertGrid(width = 800, height = 120, landscape = true, fontScale = 1.2f, expected = firstPage)
    }

    private fun assertGrid(width: Int, height: Int, landscape: Boolean, fontScale: Float, expected: List<String>) {
        var settings = 0
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            // 固定逻辑密度便于横屏尺寸在测试窗口内显示；字体缩放仍独立覆盖。
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    MenuBar(MenuBarState(true, true, 1, Color.Black, Color.DarkGray, Color.White),
                        MenuBarCallbacks({}, {}, {}, {}, {}, {}, { settings++ }, {}, {}),
                        Modifier.size(width.dp, height.dp).testTag("menu-panel"))
                }
            }
        }
        rule.onNodeWithContentDescription("关闭菜单").assertDoesNotExist()
        val panel = rule.onNodeWithTag("menu-panel").fetchSemanticsNode().boundsInRoot
        val cards = expected.map { label ->
            val card = rule.onNodeWithTag("menu-item:$label").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$label 必须在菜单内部", card.top >= panel.top && card.bottom <= panel.bottom && card.left >= panel.left && card.right <= panel.right)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label 文字不可垂直裁切", layouts.isNotEmpty() && layouts.none { it.didOverflowHeight })
            card
        }
        cards.forEachIndexed { i, first -> cards.drop(i + 1).forEach { second ->
            assertFalse("菜单格不可重叠", first.overlaps(second))
        } }
        repeat(if (expected.size == 4) 2 else 1) { rule.onNodeWithTag("menu-pages").performTouchInput { swipeLeft() } }
        rule.onNodeWithTag("menu-item:设置").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, settings) }
    }

    @Test fun menuAndItsSchemaChildUseTheSameToolbarReturnSlot() {
        val vm = KeyboardViewModel(ApplicationProvider.getApplicationContext<Application>())
        val feedback = mutableListOf<String>()
        rule.setContent { MaterialTheme {
            KeyboardView(vm, KeyboardUiState(), KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                onKeyPressDown = { feedback += it }), Modifier.width(360.dp).height(224.dp))
        } }
        val initial = rule.onNodeWithTag("toolbar-leading").fetchSemanticsNode().boundsInRoot
        val tools = rule.onNodeWithTag("toolbar-order-row").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("Xime-CyletixFork Logo").performTouchInput { down(center); up() }
        rule.onNodeWithContentDescription("Xime-CyletixFork Logo").assertDoesNotExist()
        rule.onNodeWithContentDescription("关闭菜单").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("返回").assertCountEquals(1)
        assertEquals(initial, rule.onNodeWithTag("toolbar-leading").fetchSemanticsNode().boundsInRoot)
        assertEquals(tools, rule.onNodeWithTag("toolbar-order-row").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("menu-item:输入方案").performClick()
        rule.runOnIdle { assertEquals(OverlayRoute.SchemaList, (vm.page.value as KeyboardPage.Overlay).route) }
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("menu-item:输入方案").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithContentDescription("Xime-CyletixFork Logo").assertIsDisplayed()
        rule.onNodeWithTag("keyboard-overlay").assertDoesNotExist()
        rule.runOnIdle { assertTrue("菜单与返回需走统一按键反馈", feedback.size >= 3) }
    }
}
