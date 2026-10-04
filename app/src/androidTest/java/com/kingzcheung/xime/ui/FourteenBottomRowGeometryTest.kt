package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FourteenBottomRowGeometryTest {
    @get:Rule val rule = createComposeRule()
    @After fun restoreLayout() { KeysConfigHelper.setActiveKeyboardSchema("rime_ice") }

    @Test fun bottomRowMatchesQwertyAcrossSchemasSizesFontsAndGlass() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val vm = KeyboardViewModel(app)
        var schema by mutableStateOf("rime_ice")
        var width by mutableIntStateOf(360)
        var fontScale by mutableFloatStateOf(1f)
        var floating by mutableStateOf(false)
        var glass by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = FrostedGlassConfig(enabled = glass))) {
                MaterialTheme {
                    KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                        KeyboardUiState(currentSchemaId = schema, isFloatingMode = floating), false,
                        Modifier.requiredSize(width.dp, 260.dp).testTag("layout-body"))
                }
            }
        }
        val tags = listOf("mode-slot-1", "mode-slot-2", "qwerty-punctuation-key", "space-key", "qwerty-enter-key")
        fun bounds(): Map<String, Rect> {
            val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
            return tags.associateWith { tag ->
                val rect = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
                assertTrue("$schema $tag must fit $body: $rect", rect.left >= body.left - 1f && rect.right <= body.right + 1f &&
                    rect.top >= body.top - 1f && rect.bottom <= body.bottom + 1f && rect.height > 0f && rect.width > 0f)
                rect.translate(-body.topLeft)
            }
        }
        for (size in listOf(280, 360, 800)) for (large in listOf(false, true)) {
            rule.runOnIdle {
                width = size; fontScale = if (large) 1.5f else 1f; glass = large; floating = size == 280
                KeysConfigHelper.setActiveKeyboardSchema("rime_ice"); schema = "rime_ice"
            }
            rule.waitForIdle()
            val expected = bounds()
            for (target in listOf("pinyin_14jian", "double_pinyin_flypy_14jian")) {
                rule.runOnIdle { KeysConfigHelper.setActiveKeyboardSchema(target); schema = target }
                rule.waitForIdle()
                for ((tag, actual) in bounds()) {
                    val reference = expected.getValue(tag)
                    assertEquals("$target $size $tag left", reference.left, actual.left, 1f)
                    assertEquals("$target $size $tag top", reference.top, actual.top, 1f)
                    assertEquals("$target $size $tag width", reference.width, actual.width, 1f)
                    assertEquals("$target $size $tag height", reference.height, actual.height, 1f)
                }
            }
        }
    }
}
