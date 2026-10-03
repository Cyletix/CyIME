package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.settings.InputProfileSelectors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchemaGridLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val schemas = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "double_pinyin_flypy", "japanese")
        .map { SchemaInfo(it, it, "", "", "") }

    @Test fun shortPanelKeepsIndependentChoicesAndApplyReachable() = checkChoices(360, 176, 1f)
    @Test fun narrowLargeTextChoicesCanScrollWithoutHidingApply() = checkChoices(280, 184, 2f)
    @Test fun landscapePanelUsesTheSameChoices() = checkChoices(640, 180, 1.3f)

    private fun checkChoices(width: Int, height: Int, fontScale: Float) {
        val selected = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    SchemaListView(schemas, "rime_ice", Color.White, Color.Blue, Color.Black, Color.LightGray,
                        onSelectSchema = { selected += it }, modifier = Modifier.size(width.dp, height.dp).testTag("schema-panel"))
                }
            }
        }
        rule.onAllNodesWithTag("profile-language").assertCountEquals(0)
        rule.onNodeWithTag("panel-scheme:double_pinyin_flypy").performScrollTo().performClick()
        rule.onNodeWithTag("panel-layout:t9").performScrollTo().performClick()
        rule.onNodeWithTag("apply-input-profile").assertIsNotEnabled()
        rule.onNodeWithTag("panel-scheme:t9_pinyin").performScrollTo().performClick()
        assertTrue("Browsing must not change the active input session", selected.isEmpty())
        val apply = rule.onNodeWithTag("apply-input-profile").assertIsDisplayed()
        val applyBounds = apply.fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("schema-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(applyBounds.top >= panel.top && applyBounds.bottom <= panel.bottom)
        apply.performClick()
        rule.runOnIdle { assertEquals(listOf("t9_pinyin"), selected) }
        rule.onAllNodesWithTag("schema-tile:t9_pinyin").assertCountEquals(0)
    }

    @Test fun currentCombinationAndExternalChangesNeverDefaultToFirst() {
        val current = mutableStateOf("t9_pinyin")
        rule.setContent { MaterialTheme {
            SchemaListView(schemas, current.value, Color.White, Color.Blue, Color.Black, Color.LightGray,
                {}, modifier = Modifier.size(360.dp, 240.dp))
        } }
        rule.onNodeWithTag("panel-layout:t9").assertIsSelected()
        rule.onNodeWithTag("panel-scheme:t9_pinyin").assertIsSelected()
        rule.runOnIdle { current.value = "double_pinyin_flypy" }
        rule.onNodeWithTag("panel-scheme:double_pinyin_flypy").assertIsSelected()
        rule.runOnIdle { current.value = "not_available" }
        rule.onAllNodesWithTag("apply-input-profile").assertCountEquals(0)
    }

    @Test fun backendVariantsDoNotDuplicateTheLayoutChoice() {
        val variants = listOf("double_pinyin_flypy", "double_pinyin_abc", "rime_ice")
            .map { SchemaInfo(it, it, "", "", "") }
        var selected by mutableStateOf(variants.first())
        rule.setContent { MaterialTheme {
            InputProfileSelectors(variants, selected, Modifier.size(360.dp, 300.dp)) { selected = it }
        } }
        rule.onNodeWithTag("layout-choice:zh").assertIsNotEnabled()
        rule.onNodeWithTag("variant-choice:zh").performClick()
        rule.onNodeWithTag("variant-choice:zh:option:double_pinyin_abc").performClick()
        rule.runOnIdle {
            assertEquals("double_pinyin_abc", selected.schemaId)
            assertEquals(InputScheme.DOUBLE_PINYIN, selected.profile.scheme)
            assertEquals(InputLayout.QWERTY.id, selected.profile.layout.id)
        }
    }
}
