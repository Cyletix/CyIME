package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.settings.CellDictionarySettingsPanel
import com.kingzcheung.xime.viewmodel.CellDictionaryUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CellDictionarySettingsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun independentSwitchAndApplyRemainReachableAtNarrowWidthsAndLargeFonts() {
        val offer = CellDictionaryCatalog.offers.first()
        val installed = InstalledCellDictionary(offer.id, offer.name, offer.category, 2595, sha256 = "test")
        var state by mutableStateOf(CellDictionaryUiState(CellDictionaryState(installed = listOf(installed)), busy = false))
        var fontScale by mutableStateOf(1f)
        var applied = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(280.dp).fillMaxHeight()) {
                        CellDictionarySettingsPanel(state, {}, {}, { id, enabled ->
                            state = state.copy(dictionaries = state.dictionaries.copy(selected = if (enabled) setOf(id) else emptySet()))
                        }, { applied++ }, {}, {})
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { fontScale = scale; state = state.copy(dictionaries = state.dictionaries.copy(selected = emptySet())) }
            rule.onNodeWithText("词库已同步").assertIsNotEnabled()
            rule.onNodeWithText("搜索词库或分类").performScrollTo().performTextReplacement(offer.name)
            rule.onNodeWithContentDescription("启用${offer.name}").performScrollTo().assertIsOff().performClick().assertIsOn()
            rule.onNodeWithText("应用词库更改").assertIsDisplayed().assertIsEnabled().performClick()
            rule.runOnIdle { assertTrue(state.dictionaries.applied.isEmpty()) }
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(offer.name).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
        }
        rule.runOnIdle { assertEquals(3, applied) }
    }
}
