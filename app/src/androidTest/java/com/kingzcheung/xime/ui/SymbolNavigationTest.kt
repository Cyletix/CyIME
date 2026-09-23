package com.kingzcheung.xime.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.SymbolKeyboardLayout
import com.kingzcheung.xime.ui.keyboard.CommonSymbolKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SymbolNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun returnStaysAtBottomLeftBeforeTheRecentCategoryStrip() {
        var returns = 0
        rule.setContent {
            MaterialTheme {
                SymbolKeyboardLayout(onSelect = {}, onBack = { returns++ },
                    backgroundColor = Color.White, textColor = Color.Black,
                    accentColor = Color.Blue, keyBgColor = Color.LightGray,
                    modifier = Modifier.size(360.dp, 240.dp).testTag("symbols"))
            }
        }
        val panel = rule.onNodeWithTag("symbols").fetchSemanticsNode().boundsInRoot
        val backNode = rule.onNodeWithContentDescription("返回键盘")
        val back = backNode.fetchSemanticsNode().boundsInRoot
        val recent = rule.onNodeWithText("最近使用").fetchSemanticsNode().boundsInRoot
        assertTrue(back.center.x < panel.left + panel.width * 0.2f)
        assertTrue(back.center.y > panel.top + panel.height * 0.8f)
        assertTrue(recent.left > back.right)
        backNode.performClick()
        rule.runOnIdle { assertEquals(1, returns) }
    }

    @Test fun commonSymbolPortraitKeepsReturnLeftAndEnterRight() = assertCommonSymbolNavigation(false)

    @Test fun commonSymbolLandscapeKeepsReturnLeftAndEnterRight() = assertCommonSymbolNavigation(true)

    private fun assertCommonSymbolNavigation(landscape: Boolean) {
        val actions = mutableListOf<String>()
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (landscape) 800 else 360
                screenHeightDp = if (landscape) 360 else 800
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    CommonSymbolKeyboardLayout(onKeyPress = { actions += it }, isAsciiMode = false,
                        keyBackgroundColor = Color.White, keyTextColor = Color.Black,
                        specialKeyBackgroundColor = Color.LightGray, specialKeyTextColor = Color.Black,
                        shadowEnabled = false, modifier = Modifier.size(360.dp, 240.dp).testTag("common-symbols"))
                }
            }
        }
        val panel = rule.onNodeWithTag("common-symbols").fetchSemanticsNode().boundsInRoot
        val backNode = rule.onNodeWithContentDescription("返回键盘")
        val enterNode = rule.onNodeWithContentDescription("回车")
        val back = backNode.fetchSemanticsNode().boundsInRoot
        val enter = enterNode.fetchSemanticsNode().boundsInRoot
        assertTrue(back.center.x < panel.left + panel.width * 0.3f)
        assertTrue(enter.center.x > panel.left + panel.width * 0.7f)
        assertTrue(back.center.y > panel.top + panel.height * 0.8f)
        assertEquals(back.center.y, enter.center.y, 1f)
        backNode.performTouchInput { down(center); up() }
        enterNode.performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(listOf("abc", "enter"), actions) }
    }
}
