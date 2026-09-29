package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.ui.keyboard.SplitWordsView
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClipboardSplitWordsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun installedDictionaryAndSelectionOnlyCommitOnConfirmation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val committed = mutableListOf<String>()
        var closed = false
        rule.setContent { XimeTheme {
            SplitWordsView("今天天气不错", Color.Black, KeyboardViewModel(app),
                onBack = { closed = true }, onConfirmText = { committed.add(it) },
                modifier = Modifier.size(360.dp, 260.dp))
        } }
        rule.waitUntil(30000) { rule.onAllNodesWithText("今天").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("今天").performTouchInput { click() }
        rule.runOnIdle { assertTrue(committed.isEmpty()) }
        java.io.File(app.filesDir, "clipboard-split-test.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithText("今天").performTouchInput { click() }
        rule.runOnIdle { assertTrue(committed.isEmpty()) }
        rule.onNodeWithText("确定").assertIsNotEnabled()
        rule.onNodeWithText("今天").performTouchInput { click() }
        rule.onNodeWithText("确定").performClick()
        rule.runOnIdle { assertEquals(listOf("今天"), committed); assertTrue(closed) }
    }
}
