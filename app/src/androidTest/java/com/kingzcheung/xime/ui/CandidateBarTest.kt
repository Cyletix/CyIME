package com.kingzcheung.xime.ui

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.service.CandidateFocus
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.service.hasSameSelectionSource
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.CandidateItem
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.theme.DividerColor
import com.kingzcheung.xime.ui.theme.KeyTextColor
import com.kingzcheung.xime.ui.theme.KeyboardBackground
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandidateBarTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun candidateBarDisplaysCandidates() {
        composeTestRule.setContent {
            CandidateBar(
                state = CandidateBarState.ChineseCandidates(
                    candidates = listOf("你好", "世界", "测试"),
                    inputText = "nihao",
                ),
                visuals = CandidateBarVisuals(
                    backgroundColor = KeyboardBackground,
                    textColor = KeyTextColor,
                    dividerColor = DividerColor
                ),
                callbacks = CandidateBarCallbacks(
                    onCandidateSelect = {}
                )
            )
        }
        
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
        composeTestRule.onNodeWithText("世界").assertIsDisplayed()
        composeTestRule.onNodeWithText("测试").assertIsDisplayed()
    }
    
    @Test
    fun candidateBarHandlesEmptyCandidates() {
        composeTestRule.setContent {
            CandidateBar(
                state = CandidateBarState.Idle,
                visuals = CandidateBarVisuals(
                    backgroundColor = KeyboardBackground,
                    textColor = KeyTextColor,
                    dividerColor = DividerColor
                ),
                callbacks = CandidateBarCallbacks(
                    onCandidateSelect = {}
                )
            )
        }
    }
    
    @Test
    fun candidateBarDisplaysInputTextWhenComposing() {
        composeTestRule.setContent {
            CandidateBar(
                state = CandidateBarState.ChineseCandidates(
                    candidates = listOf("你好"),
                    inputText = "nihao",
                ),
                visuals = CandidateBarVisuals(
                    backgroundColor = KeyboardBackground,
                    textColor = KeyTextColor,
                    dividerColor = DividerColor
                ),
                callbacks = CandidateBarCallbacks(
                    onCandidateSelect = {}
                )
            )
        }
        
        composeTestRule.onNodeWithText("nihao").assertIsDisplayed()
    }
    
    @Test
    fun candidateBarDisplaysComments() {
        composeTestRule.setContent {
            CandidateBar(
                state = CandidateBarState.ChineseCandidates(
                    candidates = listOf("你好"),
                    comments = listOf("wubi"),
                    inputText = "nihao",
                ),
                visuals = CandidateBarVisuals(
                    backgroundColor = KeyboardBackground,
                    textColor = KeyTextColor,
                    dividerColor = DividerColor
                ),
                callbacks = CandidateBarCallbacks(
                    onCandidateSelect = {}
                )
            )
        }
        
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
    }
    
    @Test
    fun candidateBarDisplaysAssociationCandidates() {
        composeTestRule.setContent {
            CandidateBar(
                state = CandidateBarState.ChineseCandidates(
                    candidates = listOf("你好"),
                    associationCandidates = listOf("世界", "吗"),
                    inputText = "nihao",
                ),
                visuals = CandidateBarVisuals(
                    backgroundColor = KeyboardBackground,
                    textColor = KeyTextColor,
                    dividerColor = DividerColor
                ),
                callbacks = CandidateBarCallbacks(
                    onCandidateSelect = {}
                )
            )
        }
        
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
        composeTestRule.onNodeWithText("世界").assertIsDisplayed()
    }
    
    @Test
    fun candidateItemDisplaysText() {
        composeTestRule.setContent {
            CandidateItem(
                text = "测试候选词",
                index = 0,
                onClick = {},
                textColor = KeyTextColor
            )
        }
        
        composeTestRule.onNodeWithText("测试候选词").assertIsDisplayed()
    }
    
    @Test
    fun candidateItemDisplaysComment() {
        composeTestRule.setContent {
            CandidateItem(
                text = "你好",
                index = 0,
                onClick = {},
                textColor = KeyTextColor,
                comment = "aaaa"
            )
        }
        
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
        composeTestRule.onNodeWithText("aaaa").assertIsDisplayed()
    }

    @Test
    fun fullKeyboardUsesHardwareFocusAndRestoresTheTouchscreenDefault() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val viewModel = KeyboardViewModel(app)
        viewModel.setKeyboardState(KeyboardLayoutState.T9Pinyin)
        val original = CandidateState(
            candidates = List(15) { "词条$it" }, inputText = "746", preeditText = "pin", isComposing = true,
        )
        val candidates = mutableStateOf(original.copy(candidateFocus = CandidateFocus.at(1, original)))
        val hardwareFocus = mutableStateOf<Int?>(null)
        val ui = mutableStateOf(KeyboardUiState(currentSchemaId = "t9_pinyin"))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardView(
                        viewModel, ui.value, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                        modifier = Modifier.size(360.dp, 240.dp), candidateState = candidates,
                        hardwareCandidateHighlight = hardwareFocus.value,
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("bar-candidate:1").assertIsSelected()
        composeTestRule.runOnIdle { hardwareFocus.value = 8 }
        composeTestRule.onNodeWithTag("bar-candidate:8").assertIsDisplayed().assertIsSelected()
        composeTestRule.runOnIdle { hardwareFocus.value = null }
        composeTestRule.onNodeWithTag("bar-candidate:1").assertIsDisplayed().assertIsSelected()

        composeTestRule.runOnIdle {
            viewModel.setKeyboardState(KeyboardLayoutState.Chinese)
            ui.value = KeyboardUiState(currentSchemaId = "rime_ice")
        }
        composeTestRule.onNodeWithTag("bar-candidate:0").assertIsDisplayed().assertIsSelected()
        composeTestRule.runOnIdle { hardwareFocus.value = 9 }
        composeTestRule.onNodeWithTag("bar-candidate:9").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun fullEnglishKeyboardScrollsExplicitAssociationFocusWithoutSelectingIdleSuggestions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val viewModel = KeyboardViewModel(app)
        viewModel.setKeyboardState(KeyboardLayoutState.English)
        val candidates = mutableStateOf(CandidateState(
            pendingEnglishText = "hel", englishReplaceSupported = true,
            associationCandidates = List(20) { "hello-extension-$it" },
        ))
        val hardwareFocus = mutableStateOf<Int?>(null)
        var selected = -1
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardView(
                        viewModel, KeyboardUiState(currentSchemaId = "rime_ice", isAsciiMode = true),
                        KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            onAssociationSelect = { selected = it }),
                        modifier = Modifier.size(360.dp, 240.dp), candidateState = candidates,
                        hardwareCandidateHighlight = hardwareFocus.value,
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("bar-association:0").assertIsDisplayed().assertIsNotSelected()
        // The original typed word occupies index 0, followed by all twenty suggestions.
        composeTestRule.runOnIdle { hardwareFocus.value = 20 }
        composeTestRule.onNodeWithTag("bar-association:20").assertIsDisplayed().assertIsSelected().performClick()
        composeTestRule.runOnIdle { assertEquals(20, selected); hardwareFocus.value = 0 }
        composeTestRule.onNodeWithTag("bar-association:0").assertIsDisplayed().assertIsSelected()
        composeTestRule.runOnIdle { hardwareFocus.value = null }
        composeTestRule.onNodeWithTag("bar-association:0").assertIsNotSelected()
    }

    @Test
    fun expandedKeyboardFocusBeyondFirstPageUsesTheVisibleFilteredIndexForGlobalSelection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        val viewModel = KeyboardViewModel(app)
        viewModel.setKeyboardState(KeyboardLayoutState.T9Pinyin)
        val words = List(60) { if (it % 2 == 0) "词条$it" else (0x4e00 + it).toChar().toString() }
        val candidates = mutableStateOf(CandidateState(
            candidates = words.take(15), inputText = "746", preeditText = "pin", isComposing = true,
            expandedCandidatesLoaded = true, expandedCandidates = words.map { RimeCandidate(it, "") },
        ))
        val hardwareFocus = mutableStateOf<Int?>(null)
        var selected = -1
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    KeyboardView(
                        viewModel, KeyboardUiState(currentSchemaId = "t9_pinyin"),
                        KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {},
                            isCandidateSnapshotCurrent = { it.hasSameSelectionSource(candidates.value) },
                            onGlobalCandidateSelect = { selected = it }),
                        modifier = Modifier.size(360.dp, 240.dp), candidateState = candidates,
                        hardwareCandidateHighlight = hardwareFocus.value,
                    )
                }
            }
        }
        composeTestRule.runOnIdle { viewModel.setCandidatePageExpanded(true); hardwareFocus.value = 35 }
        composeTestRule.onNodeWithTag("bar-candidate:35").assertIsDisplayed().assertIsSelected()
        composeTestRule.runOnIdle { viewModel.toggleSingleCharFilter(); hardwareFocus.value = 21 }
        composeTestRule.onNodeWithTag("bar-candidate:21").assertIsDisplayed().assertIsSelected().performClick()
        composeTestRule.runOnIdle { assertEquals(43, selected) }
    }
}
