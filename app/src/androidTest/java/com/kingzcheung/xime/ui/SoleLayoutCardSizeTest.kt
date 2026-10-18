package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.ui.settings.InputProfileSelectors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 语言管理: a language with one layout keeps a normal-size card, on phones and tablets. */
class SoleLayoutCardSizeTest {
    @get:Rule val rule = createComposeRule()
    private val english = SchemaInfo(InputModes.ENGLISH, "英文", "", "", "")

    @Test fun phone() = check(360, "sole-layout-phone")
    @Test fun tablet() = check(900, "sole-layout-tablet")

    private fun check(widthDp: Int, name: String) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Surface { InputProfileSelectors(listOf(english), english, Modifier.width(widthDp.dp)) {} }
                }
            }
        }
        rule.waitForIdle()
        val list = rule.onNodeWithTag("layout-choice:en:list").fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("layout-choice:en:option:qwerty").fetchSemanticsNode().boundsInRoot
        saveUiEvidence(name, rule.onNodeWithTag("profile-settings-selectors"))
        assertTrue("card ${card.width} stays a normal card, not the row ${list.width}", card.width <= maxOf(112f, list.width / 3f) + 1f)
        assertTrue("card stays on screen", card.right <= list.right + 1f)
    }
}
