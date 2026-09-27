package com.kingzcheung.xime.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.graphics.Bitmap
import com.kingzcheung.xime.ui.keyboard.LocalTextModeLabel
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
        var numbers = 0
        rule.setContent {
            MaterialTheme {
                SymbolKeyboardLayout(onSelect = {}, onBack = { returns++ }, onNumber = { numbers++ },
                    backgroundColor = Color.White, textColor = Color.Black,
                    accentColor = Color.Blue, keyBgColor = Color.LightGray,
                    modifier = Modifier.size(360.dp, 240.dp).testTag("symbols"))
            }
        }
        val panel = rule.onNodeWithTag("symbols").fetchSemanticsNode().boundsInRoot
        val backNode = rule.onNodeWithText("中文")
        val back = backNode.fetchSemanticsNode().boundsInRoot
        val recent = rule.onNodeWithText("最近").fetchSemanticsNode().boundsInRoot
        assertTrue(back.center.x < panel.left + panel.width * 0.2f)
        assertTrue(back.center.y > panel.top + panel.height * 0.8f)
        assertTrue(recent.left > back.right)
        backNode.performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, returns) }
        rule.onNodeWithTag("mode-slot-2", useUnmergedTree = true).performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, numbers) }
    }

    @Test fun footerKeysAlignOnPhoneAndTabletAndCategoriesStillScroll() {
        val viewport = mutableStateOf(360.dp to 240.dp)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalTextModeLabel provides "あいう") {
                MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                    SymbolKeyboardLayout(onSelect = {}, onBack = {}, onNumber = {},
                        backgroundColor = Color(0xFF191D25), textColor = Color.White,
                        accentColor = Color(0xFFB1C9F5), keyBgColor = Color(0xFF303540),
                        specialKeyBackgroundColor = Color(0xFF475E83),
                        modifier = Modifier.size(viewport.value.first, viewport.value.second).testTag("symbols"))
                }
            }
        }
        for (size in listOf(360.dp to 240.dp, 800.dp to 360.dp)) {
            rule.runOnIdle { viewport.value = size }
            val tags = listOf("mode-slot-1", "mode-slot-2", "symbol-category:common", "symbol-category:recentSymbols", "symbol-delete")
            val bounds = tags.map { rule.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            bounds.drop(1).forEach { assertEquals(bounds[0].top, it.top, 1f); assertEquals(bounds[0].bottom, it.bottom, 1f) }
            rule.onNodeWithText("最近使用").assertDoesNotExist()
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "symbol-footer-${size.first.value.toInt()}.png")
            file.outputStream().use { rule.onNodeWithTag("symbols").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onNodeWithTag("symbol-category:englishSymbols", useUnmergedTree = true).performScrollTo().performTouchInput { down(center); up() }
        rule.onNodeWithTag("symbol-category:englishSymbols", useUnmergedTree = true).assertIsSelected()
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
        val backNode = rule.onNodeWithText("中文")
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
