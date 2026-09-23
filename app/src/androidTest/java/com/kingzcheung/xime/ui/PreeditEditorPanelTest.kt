package com.kingzcheung.xime.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.rime.PinyinEditSession
import com.kingzcheung.xime.ui.keyboard.PreeditEditorPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreeditEditorPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun smallFloatingLargeFontScrollsWithoutFlatteningKeysAndFeedbackFiresOnce() {
        var feedback = 0
        var applied = ""
        var closed = false
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                Box(Modifier.width(280.dp).height(180.dp)) {
                    PreeditEditorPanel(PinyinEditSession(1, "rime_ice", "nihao", "", "", "nihao", 5, false),
                        Color(0xff191c22), Color(0xff2e323a), Color.White, Color(0xffb4cafa),
                        onClose = { closed = true }, onApply = { text, _, reply -> applied = text; reply(true) },
                        onFeedback = { feedback++ }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        rule.onNodeWithTag("preedit-letter:a").performScrollTo().assertHeightIsAtLeast(42.dp)
        rule.onNodeWithTag("preedit-separator").performScrollTo().performClick()
        assertEquals("分隔符只反馈一次", 1, feedback)
        rule.onNodeWithTag("preedit-apply").performClick()
        assertEquals("nihao'", applied); assertTrue(closed)
    }
}
