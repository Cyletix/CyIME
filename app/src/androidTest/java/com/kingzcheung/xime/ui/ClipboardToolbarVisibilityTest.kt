package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClipboardToolbarVisibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allClipboardTabsKeepNavigationAboveContentIncludingExpandedHandwriting() {
        val vm = KeyboardViewModel(ApplicationProvider.getApplicationContext<Application>())
        val handwriting = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                MaterialTheme {
                    KeyboardView(vm, KeyboardUiState(handwritingExpanded = handwriting.value),
                        KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                        modifier = Modifier.size(280.dp, 220.dp).testTag("clipboard-keyboard"))
                }
            }
        }
        for (expanded in listOf(false, true)) for (tab in 0..2) {
            rule.runOnIdle { handwriting.value = expanded; vm.showOverlay(OverlayRoute.Clipboard(tab)) }
            val toolbar = rule.onNodeWithTag("toolbar-order-row").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val panel = rule.onNodeWithTag("keyboard-overlay").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            rule.onNodeWithContentDescription("剪贴板").assertIsSelected()
            assertTrue("Clipboard must leave the shared toolbar visible", panel.top >= toolbar.bottom)
            rule.onNodeWithContentDescription("表情").performClick()
            rule.runOnIdle { assertEquals(OverlayRoute.Emoji, (vm.page.value as KeyboardPage.Overlay).route) }
            rule.onNodeWithContentDescription("返回").performClick()
            rule.runOnIdle { assertFalse(vm.page.value is KeyboardPage.Overlay) }
            if (!expanded) rule.onNodeWithContentDescription("剪贴板").assertIsNotSelected()
            rule.runOnIdle { vm.showOverlay(OverlayRoute.Clipboard(tab)) }
            rule.onNodeWithContentDescription("剪贴板").performClick()
            rule.runOnIdle { assertFalse("same tool closes from every category", vm.page.value is KeyboardPage.Overlay) }
        }
    }
}
