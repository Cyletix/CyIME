package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.InputLayout
import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.settings.InputProfileSelectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class InputProfileSelectorsTest {
    @get:Rule val rule = createComposeRule()
    private val entries = listOf(
        SchemaInfo("rime_ice", "雾凇拼音", "", "", ""),
        SchemaInfo("t9_pinyin", "九键拼音", "", "", ""),
        SchemaInfo("double_pinyin_flypy", "小鹤双拼", "", "", ""),
    )

    private fun selectChoice(cardTag: String, choiceId: String) {
        rule.onNodeWithTag(cardTag).performScrollTo()
        rule.onNodeWithTag("$cardTag:list").performScrollToKey(choiceId)
        rule.onNodeWithTag("$cardTag:option:$choiceId").assertIsDisplayed().performClick()
    }

    @Test fun wideLightSettingsKeepLayoutLeftAndSchemeRightAndSelectRealCombinations() {
        var current by mutableStateOf(entries.first())
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    InputProfileSelectors(entries, current, Modifier.size(640.dp, 360.dp)) { current = it }
                }
            }
        }
        val layout = rule.onNodeWithTag("layout-choice:zh").fetchSemanticsNode().boundsInRoot
        val scheme = rule.onNodeWithTag("scheme-choice:zh").fetchSemanticsNode().boundsInRoot
        assertTrue(layout.right < scheme.left)
        assertTrue(abs(layout.width - scheme.width) < 1f)
        val selected = rule.onNodeWithTag("layout-choice:zh:option:qwerty").assertIsSelected().fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("layout-choice:zh:list").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(selected.center.y - viewport.center.y) < 2f)
        selectChoice("layout-choice:zh", "t9")
        rule.runOnIdle { assertEquals("t9_pinyin", current.schemaId) }
        selectChoice("scheme-choice:zh", "double_pinyin")
        rule.runOnIdle {
            assertEquals("double_pinyin_flypy", current.schemaId)
            assertEquals(InputLayout.QWERTY.id, current.profile.layout.id)
            assertEquals(InputScheme.DOUBLE_PINYIN, current.profile.scheme)
        }
        rule.onNodeWithTag("layout-choice:zh:option:t9").assertDoesNotExist()
    }

    @Test fun narrowDarkLargeTextStacksCardsAndKeepsBothListsReachable() {
        var current by mutableStateOf(entries.first())
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    InputProfileSelectors(entries, current, Modifier.size(280.dp, 360.dp)) { current = it }
                }
            }
        }
        val layout = rule.onNodeWithTag("layout-choice:zh").getUnclippedBoundsInRoot()
        val scheme = rule.onNodeWithTag("scheme-choice:zh").getUnclippedBoundsInRoot()
        assertTrue(layout.bottom < scheme.top)
        selectChoice("layout-choice:zh", "t9")
        rule.runOnIdle { assertEquals("t9_pinyin", current.schemaId) }
        selectChoice("scheme-choice:zh", "double_pinyin")
        rule.runOnIdle { assertEquals("double_pinyin_flypy", current.schemaId) }
        rule.onAllNodes(isPopup()).assertCountEquals(0)
    }

    @Test fun unboundedLanguageListParentKeepsOptionScrollAreasFinite() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme {
                    LazyColumn(Modifier.size(360.dp, 480.dp)) {
                        item {
                            InputProfileSelectors(entries, entries.first(), Modifier.fillMaxWidth()) {}
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("layout-choice:zh:list").assertIsDisplayed()
        rule.onNodeWithTag("scheme-choice:zh:list").assertIsDisplayed()
        val selector = rule.onNodeWithTag("profile-settings-selectors").getUnclippedBoundsInRoot()
        assertTrue("Selector must not expand to an unbounded height", selector.bottom - selector.top < 480.dp)
    }
}
