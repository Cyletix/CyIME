package com.kingzcheung.xime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.keyboard.RejectedCommitOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RejectedCommitOverlayTest {
    @get:Rule val rule = createComposeRule()
    @Test fun shortPanelRetainsTextAndBlocksUnderlyingKeysWhileOfferingExplicitActions() {
        var keys = 0
        var retries = 0
        var cancelled = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 144.dp)) {
                        Box(Modifier.fillMaxSize().clickable { keys++ })
                        RejectedCommitOverlay("待提交文字", Color.Black, Color.White, { retries++ }, { cancelled++ })
                    }
                }
            }
        }
        rule.onNodeWithText("待提交文字").assertIsDisplayed()
        rule.onNodeWithTag("rejected-commit").performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(2f, 2f)) }
        rule.onNodeWithText("重试").performClick()
        rule.onNodeWithText("取消本次输入").performClick()
        rule.runOnIdle { assertEquals(0, keys); assertEquals(1, retries); assertEquals(1, cancelled) }
    }
}
