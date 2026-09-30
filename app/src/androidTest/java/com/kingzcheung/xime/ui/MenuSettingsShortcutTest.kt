package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.settings.SettingsRoutes
import com.kingzcheung.xime.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MenuSettingsShortcutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun themeShortcutOpensRealPageAndBackReturnsToSettingsHome() {
        rule.setContent {
            androidx.compose.material3.MaterialTheme { SettingsScreen(initialRoute = SettingsRoutes.Theme) }
        }
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("CyIME 设置").assertIsDisplayed()
    }

    @Test fun shortcutsStayReachableAcrossMinimumPhoneAndTabletPanels() {
        val viewport = mutableStateOf(Triple(320, 144, 1f))
        val opened = mutableListOf<String>()
        rule.setContent {
            val (width, height, font) = viewport.value
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("menu-host")) {
                    MenuBar(MenuBarState(true, true, backgroundColor = Color.Black),
                        MenuBarCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, onSettingsPage = { opened += it }))
                }
            }
        }
        val entries = listOf("主题与定制" to SettingsRoutes.Theme, "智能联想" to SettingsRoutes.SmartPrediction,
            "语音转文本" to SettingsRoutes.SpeechToText, "模型管理" to SettingsRoutes.ModelLocal)
        for (size in listOf(Triple(320, 144, 1f), Triple(280, 180, 2f), Triple(640, 140, 1f))) {
            rule.runOnIdle { viewport.value = size }
            for ((label, route) in entries) {
                // A compact panel paginates instead of squeezing or dropping entries.
                rule.onNodeWithTag("menu-pages").performScrollToIndex(0)
                var page = 0
                while (rule.onAllNodesWithTag("menu-item:$label").fetchSemanticsNodes()
                        .none { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 } && page < 7) {
                    rule.onNodeWithTag("menu-pages").performScrollToIndex(++page)
                }
                rule.onNodeWithTag("menu-item:$label").assertIsDisplayed().performClick()
                rule.runOnIdle { assertEquals(route, opened.last()) }
                rule.assertGeometry("menu-host", "menu $size $label")
            }
        }
    }
}
