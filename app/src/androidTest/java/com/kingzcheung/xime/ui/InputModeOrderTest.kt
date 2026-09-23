package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.menubar.SchemaListView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InputModeOrderTest {
    @get:Rule val rule = createComposeRule()
    @Test fun dragOrderPersistsAndSurvivesReenteringAndAnotherMove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.getString("input_mode_order", null)
        val initial = listOf(SchemaInfo("t9", "拼音", "", "", ""), SchemaInfo("japanese", "日语", "", "", ""))
        var modes by mutableStateOf(InputModes.available(initial))
        try {
            rule.setContent { MaterialTheme {
                SchemaListView(schemas = modes, currentSchemaId = "t9", backgroundColor = Color.White,
                    accentColor = Color.Blue, keyTextColor = Color.Black, keyBgColor = Color.LightGray,
                    onSelectSchema = {}, onReorderSchemas = { ids ->
                        InputModes.saveOrder(context, ids); modes = InputModes.ordered(context, initial)
                    }, modifier = Modifier.size(360.dp, 250.dp))
            } }
            rule.onNodeWithText("调整顺序").performClick()
            val from = rule.onNodeWithTag("input-mode-order:t9")
            val first = from.fetchSemanticsNode().boundsInRoot
            val second = rule.onNodeWithTag("input-mode-order:japanese").fetchSemanticsNode().boundsInRoot
            rule.mainClock.autoAdvance = false
            from.performTouchInput { down(Offset(20f, center.y)) }
            rule.mainClock.advanceTimeBy(600)
            from.performTouchInput { moveBy(Offset(0f, second.center.y - first.center.y)); up() }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            assertEquals(listOf("japanese", "t9", InputModes.ENGLISH), InputModes.ordered(context, initial).map { it.schemaId })
            rule.onNodeWithContentDescription("上移英文").performClick()
            assertEquals(listOf("japanese", InputModes.ENGLISH, "t9"), InputModes.ordered(context, initial).map { it.schemaId })
            rule.onNodeWithText("完成").performClick()
            rule.onNodeWithText("调整顺序").performClick()
            val english = rule.onNodeWithTag("input-mode-order:__xime_english").fetchSemanticsNode().boundsInRoot
            val t9 = rule.onNodeWithTag("input-mode-order:t9").fetchSemanticsNode().boundsInRoot
            assertTrue(english.top < t9.top)
        } finally { prefs.edit().also { if (saved == null) it.remove("input_mode_order") else it.putString("input_mode_order", saved) }.commit() }
    }
}
