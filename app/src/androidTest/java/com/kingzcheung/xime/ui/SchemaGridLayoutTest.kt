package com.kingzcheung.xime.ui

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.settings.InputProfileSelectors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class SchemaGridLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val schemas = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "double_pinyin_flypy", "japanese")
        .map { SchemaInfo(it, it, "", "", "") }

    @Test fun shortPanelSwitchesWithoutAnApplyStep() = checkChoices(360, 176, 1f)
    @Test fun narrowLargeTextChoicesRemainReachable() = checkChoices(280, 184, 2f)
    @Test fun landscapePanelUsesTheSameChoices() = checkChoices(640, 180, 1.3f)

    private fun checkChoices(width: Int, height: Int, fontScale: Float) {
        val selected = mutableListOf<String>()
        val current = mutableStateOf("rime_ice")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    SchemaListView(schemas, current.value, Color.White, Color.Blue, Color.Black, Color.LightGray,
                        onSelectSchema = { selected += it; current.value = it }, modifier = Modifier.size(width.dp, height.dp).testTag("schema-panel"))
                }
            }
        }
        rule.onAllNodesWithTag("profile-language").assertCountEquals(0)
        rule.onAllNodesWithTag("apply-input-profile").assertCountEquals(0)
        rule.onNodeWithTag("panel-scheme:double_pinyin_flypy").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("double_pinyin_flypy"), selected) }
        rule.onNodeWithTag("panel-layout:t9").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("A missing layout/scheme pair must not silently change encoding",
            listOf("double_pinyin_flypy"), selected) }
        rule.onNodeWithTag("panel-layout:t9").assertIsNotSelected()
        rule.onNodeWithTag("panel-layout:qwerty").assertIsSelected()
        val target = rule.onNodeWithTag("panel-scheme:t9_pinyin").performScrollTo().assertIsDisplayed()
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithTag("schema-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(targetBounds.top >= panel.top && targetBounds.bottom <= panel.bottom)
        target.performClick()
        rule.runOnIdle { assertEquals(listOf("double_pinyin_flypy", "t9_pinyin"), selected) }
        rule.onNodeWithTag("panel-scheme:t9_pinyin").assertIsSelected()
        rule.onNodeWithTag("panel-layout:qwerty").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("double_pinyin_flypy", "t9_pinyin", "rime_ice"), selected) }
        rule.onAllNodesWithTag("schema-tile:t9_pinyin").assertCountEquals(0)
    }

    @Test fun roomyPanelCentersActualChoicesWithoutAnApplyButton() {
        val current = mutableStateOf("t9_pinyin")
        rule.setContent { MaterialTheme {
            SchemaListView(schemas, current.value, Color.White, Color.Blue, Color.Black, Color.LightGray,
                { current.value = it }, modifier = Modifier.size(360.dp, 360.dp))
        } }
        fun assertCentered(item: String, viewport: String) {
            val bounds = rule.onNodeWithTag(item).fetchSemanticsNode().boundsInRoot
            val parent = rule.onNodeWithTag(viewport).fetchSemanticsNode().boundsInRoot
            assertTrue("The active choice should sit in the centre of the selector",
                abs(bounds.center.y - parent.center.y) <= 2f)
        }
        rule.onNodeWithTag("panel-layout:t9").assertIsSelected()
        assertCentered("panel-layout:t9", "profile-layout-list-viewport")
        assertCentered("panel-scheme:t9_pinyin", "profile-scheme-list-viewport")
        rule.runOnIdle { current.value = "double_pinyin_flypy" }
        rule.onNodeWithTag("panel-scheme:double_pinyin_flypy").assertIsSelected()
        assertCentered("panel-scheme:double_pinyin_flypy", "profile-scheme-list-viewport")
        rule.onAllNodesWithTag("apply-input-profile").assertCountEquals(0)
    }

    @Test fun managementKeepsCustomLayoutsWithoutTheObsoleteCombinationOrder() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launches += intent }
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) { MaterialTheme {
                SchemaListView(schemas, "t9_pinyin", Color.White, Color.Blue, Color.Black, Color.LightGray,
                    {}, modifier = Modifier.size(360.dp, 260.dp))
            } }
        }
        repeat(4) {
            rule.onNodeWithTag("profile-more").assertHasClickAction().performClick()
            rule.onNodeWithTag("profile-management").assertIsDisplayed()
            rule.onAllNodes(isPopup()).assertCountEquals(0)
            rule.onNodeWithTag("profile-edit-order").assertDoesNotExist()
            rule.onNodeWithText("组合顺序").assertDoesNotExist()
            rule.onNodeWithTag("add-layout-tile").assertIsDisplayed()
            rule.onNodeWithTag("profile-management-back").performClick()
            rule.onNodeWithTag("panel-scheme:t9_pinyin").assertIsSelected()
        }
        rule.onNodeWithTag("profile-more").performClick()
        rule.onNodeWithTag("add-layout-tile").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(1, launches.size)
            assertEquals("com.kingzcheung.xime.CustomLayoutActivity", launches.single().component?.className)
            assertTrue(launches.single().getBooleanExtra("create_layout", false))
            assertTrue(launches.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        rule.onNodeWithTag("profile-management-back").performClick()
        rule.onNodeWithTag("panel-layout:t9").assertIsSelected()
    }

    @Test fun shortLargeTextManagementActionsStayScrollableAndCanReturn() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { MaterialTheme {
                SchemaListView(schemas, "t9_pinyin", Color.White, Color.Blue, Color.Black, Color.LightGray,
                    {}, modifier = Modifier.size(280.dp, 184.dp))
            } }
        }
        rule.onNodeWithTag("profile-more").performClick()
        rule.onNodeWithTag("profile-edit-order").assertDoesNotExist()
        rule.onNodeWithTag("add-layout-tile").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(isPopup()).assertCountEquals(0)
        rule.onNodeWithTag("profile-management-back").assertIsDisplayed().performClick()
        rule.onNodeWithTag("panel-scheme:t9_pinyin").performScrollTo().assertIsDisplayed()
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
        rule.onNodeWithTag("variant-choice:zh").performScrollTo()
        rule.onNodeWithTag("variant-choice:zh:option:double_pinyin_abc").performClick()
        rule.runOnIdle {
            assertEquals("double_pinyin_abc", selected.schemaId)
            assertEquals(InputScheme.DOUBLE_PINYIN, selected.profile.scheme)
            assertEquals(InputLayout.QWERTY.id, selected.profile.layout.id)
        }
    }
}
