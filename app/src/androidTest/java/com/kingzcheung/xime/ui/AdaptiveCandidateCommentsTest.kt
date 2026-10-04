package com.kingzcheung.xime.ui

import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdaptiveCandidateCommentsTest {
    @get:Rule val rule = createComposeRule()
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("adaptive_comments_$name", mode)
    }
    private val width = mutableStateOf(320)
    private val fontScale = mutableStateOf(1f)
    private val visuals = CandidateBarVisuals(Color.Black, Color.White, Color.Gray)

    @Before fun setup() { SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }
    @After fun teardown() { SettingsPreferences.getPrefsPublic(context).edit().clear().commit() }

    @Composable private fun Host(content: @Composable () -> Unit) {
        val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = 1600 }
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
            LocalDensity provides Density(.5f, fontScale.value)) {
            MaterialTheme { content() }
        }
    }

    @Test fun stripUsesActualWidthAndUpdatesAfterResizeFontAndPreferenceChanges() {
        var selected = -1
        rule.setContent { Host {
            CandidateBar(CandidateBarState.ChineseCandidates(listOf("你好", "你"),
                comments = listOf("ni hao", "ni"), inputText = "nihao"),
                visuals = visuals, callbacks = CandidateBarCallbacks(onCandidateSelect = { selected = it }),
                modifier = Modifier.requiredWidth(width.value.dp))
        } }
        rule.onNodeWithText("ni hao").assertDoesNotExist()
        rule.onNodeWithText("nihao").assertExists() // Editing preedit is not an optional annotation.
        rule.onNodeWithText("你好").performClick()
        rule.runOnIdle { assertEquals(0, selected); width.value = 720 }
        rule.onNodeWithText("ni hao").assertIsDisplayed()
        rule.runOnIdle { fontScale.value = 2f }
        rule.onNodeWithText("ni hao").assertDoesNotExist()
        rule.runOnIdle { fontScale.value = 1f; SettingsPreferences.setShowCandidateComments(context, false) }
        rule.onNodeWithText("ni hao").assertDoesNotExist()
        rule.runOnIdle { SettingsPreferences.setShowCandidateComments(context, true) }
        rule.onNodeWithText("ni hao").assertIsDisplayed()
        rule.runOnIdle { width.value = 320 }
        rule.onNodeWithText("ni hao").assertDoesNotExist()
    }

    @Test fun expandedPageReflowsWithoutStrippingSelectionReadingOrRailSyllables() {
        val entries = List(24) { CandidateEntry("候选$it", "hou xuan xiang mu de pin yin", 100 + it) }
        var selected: CandidateEntry? = null
        width.value = 720
        rule.setContent { Host {
            CandidatePage(CandidatePageState(candidates = entries, backgroundColor = Color.Black,
                textColor = Color.White, railPinyinOptions = listOf("hou")),
                CandidatePageCallbacks(onCandidateSelect = { selected = it }),
                modifier = Modifier.requiredSize(width.value.dp, 300.dp))
        } }
        rule.onNodeWithText("候选0 hou xuan xiang mu de pin yin").assertIsDisplayed()
        val firstWithComments = rule.onNodeWithTag("expanded-candidate:100").fetchSemanticsNode().boundsInRoot
        val thirdWithComments = rule.onNodeWithTag("expanded-candidate:102").fetchSemanticsNode().boundsInRoot
        assertTrue("long annotations take additional rows", thirdWithComments.top > firstWithComments.top)
        rule.runOnIdle { SettingsPreferences.setShowCandidateComments(context, false) }
        val firstWithout = rule.onNodeWithTag("expanded-candidate:100").fetchSemanticsNode().boundsInRoot
        val thirdWithout = rule.onNodeWithTag("expanded-candidate:102").fetchSemanticsNode().boundsInRoot
        assertEquals("hidden annotations free room in the same row", firstWithout.top, thirdWithout.top, 1f)
        rule.runOnIdle { width.value = 320; SettingsPreferences.setShowCandidateComments(context, true) }
        rule.onNodeWithText("候选0 hou xuan xiang mu de pin yin").assertDoesNotExist()
        rule.onNodeWithText("hou").assertIsDisplayed()
        rule.onNodeWithTag("expanded-candidate:100").performClick()
        rule.runOnIdle { assertEquals(entries.first(), selected) }
    }

    @Test fun hardwareRowsKeepNumbersAndSelectionWhenAnnotationsDisappear() {
        var selected = -1
        rule.setContent { Host {
            androidx.compose.foundation.layout.Box(Modifier.requiredWidth(width.value.dp)) {
                HardwareCandidateRow(listOf("你好"), listOf("ni hao"), 0, false, false, visuals,
                    { selected = it }, null, null)
            }
        } }
        rule.onNodeWithText("ni hao").assertDoesNotExist()
        rule.onNodeWithText("1 你好").performClick()
        rule.runOnIdle { assertEquals(0, selected); width.value = 720 }
        rule.onNodeWithText("ni hao").assertIsDisplayed()
    }
}
