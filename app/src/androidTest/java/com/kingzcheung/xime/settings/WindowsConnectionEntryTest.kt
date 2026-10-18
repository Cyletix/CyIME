package com.kingzcheung.xime.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.sync.WindowsPeer
import com.kingzcheung.xime.clipboard.sync.WindowsPairingConfirmation
import com.kingzcheung.xime.ui.saveUiEvidence
import com.kingzcheung.xime.ui.settings.WindowsDevicesPanelContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WindowsConnectionEntryTest {
    @get:Rule val rule = createComposeRule()
    @Test fun scanRemainsVisibleWhileUnpairedDisconnectedAndConnected() {
        var stage by mutableIntStateOf(0)
        var scans = 0
        val peer = WindowsPeer("test", "我的电脑", "https://192.0.2.1:18740", "test")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.size(400.dp, 720.dp).testTag("connection-page")) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            WindowsDevicesPanelContent(
                                listOf("正在寻找附近电脑", "我的电脑 暂未连通", "已连接 我的电脑")[stage],
                                if (stage == 0) null else peer, emptyList(), true, null, null,
                                { scans++ }, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        for (s in 0..2) {
            rule.runOnIdle { stage = s }
            rule.onNodeWithTag("windows-scan-connect").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(s + 1, scans) }
            if (s == 1) saveUiEvidence("windows-connection-offline", rule.onNodeWithTag("connection-page"))
        }
    }

    @Test fun nearbyResponseShowsLocalCodeAndExplicitConfirmOrCancel() {
        var pending by mutableStateOf<WindowsPairingConfirmation?>(WindowsPairingConfirmation(17, "测试电脑", "01234567"))
        var confirmedId = -1L
        var cancelled = false
        rule.setContent {
            MaterialTheme {
                Surface(Modifier.width(360.dp).testTag("pairing-confirmation-page")) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        WindowsDevicesPanelContent("等待核对", null, emptyList(), false, pending, null,
                            {}, {}, {}, {}, {}, { confirmedId = it }, { cancelled = true; pending = null })
                    }
                }
            }
        }
        rule.onNodeWithTag("windows-pairing-code").assertTextEquals("01234567")
        rule.onNodeWithTag("windows-confirm-pairing").assertDoesNotExist()
        rule.onNodeWithTag("windows-cancel-pairing").assertIsDisplayed()
        rule.runOnIdle { pending = pending!!.copy(ready = true) }
        rule.onNodeWithTag("windows-confirm-pairing").assertIsDisplayed()
        rule.runOnIdle { assertEquals(-1L, confirmedId) }
        saveUiEvidence("windows-nearby-confirmation", rule.onNodeWithTag("pairing-confirmation-page"))
        rule.onNodeWithTag("windows-confirm-pairing").performClick()
        rule.runOnIdle { assertEquals(17L, confirmedId) }
        rule.onNodeWithTag("windows-cancel-pairing").performClick()
        rule.runOnIdle { assertEquals(true, cancelled) }
        rule.onNodeWithTag("windows-pairing-code").assertDoesNotExist()
        rule.onNodeWithTag("windows-confirm-pairing").assertDoesNotExist()
    }
}
