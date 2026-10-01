package com.kingzcheung.xime.ui

import android.content.Context
import android.content.ContextWrapper
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
import com.kingzcheung.xime.ui.menubar.dragOrderItem
import androidx.compose.ui.platform.testTag
import com.kingzcheung.xime.ui.settings.reorderEnabledModes
import com.kingzcheung.xime.ui.settings.orderedInstalledModeIds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InputModeOrderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun draggedCardMovesBeforeDropAndEdgeScrollsWithoutSavingUntilRelease() {
        lateinit var state: com.kingzcheung.xime.ui.menubar.DragOrderState
        var saved: List<String>? = null
        val ids = (0..15).map { "mode$it" }
        rule.setContent {
            state = com.kingzcheung.xime.ui.menubar.rememberDragOrder(ids) { saved = it }
            androidx.compose.foundation.lazy.LazyColumn(state = state.list, modifier = Modifier.size(320.dp, 240.dp).testTag("drag-list")) {
                items(state.order.size, key = { state.order[it] }) { index ->
                    val id = state.order[index]
                    Box(Modifier.then(dragOrderItem(state, id)).fillMaxWidth().height(48.dp).testTag(id))
                }
            }
        }
        val before = rule.onNodeWithTag("mode0").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("mode0").performTouchInput { down(Offset(20f, center.y)) }
            rule.mainClock.advanceTimeBy(700)
            rule.runOnUiThread { assertEquals("mode0", state.dragging); assertEquals(0, state.list.firstVisibleItemScrollOffset) }
            rule.onRoot().performTouchInput { moveBy(Offset(0f, 12f)) }
            rule.mainClock.advanceTimeBy(32)
            val moved = rule.onNodeWithTag("mode0").fetchSemanticsNode().boundsInRoot
            assertTrue("卡片在松手之前应跟手移动", moved.top > before.top)
            assertNull(saved)
            val viewport = rule.onNodeWithTag("drag-list").fetchSemanticsNode().boundsInRoot
            rule.onRoot().performTouchInput { moveTo(Offset(before.center.x, viewport.bottom - 8f)) }
            repeat(80) { rule.mainClock.advanceTimeByFrame(); Thread.sleep(3) }
            rule.runOnUiThread {
                assertTrue("边缘拖动应自动滚动", state.list.firstVisibleItemIndex > 0)
                assertTrue("拖动应连续穿过中间卡片", state.order.indexOf("mode0") >= 4)
                assertNull(saved)
            }
            rule.onRoot().performTouchInput { up() }
            rule.runOnUiThread {
                assertEquals(state.order, saved)
                state.start(state.list.layoutInfo.visibleItemsInfo.first { it.key in state.order }.key as String)
                state.drag(80f)
                state.cancel()
                assertEquals(saved, state.order)
            }
        } finally { rule.mainClock.autoAdvance = true }
        rule.waitForIdle()
    }

    @Test fun settingsOrderUsesKeyboardPreferenceAndKeepsInactiveSlots() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "scheme-order-${System.nanoTime()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("$namespace-$name", mode)
        }
        val installed = listOf(
            SchemaMeta("rime_ice", "中文26键"),
            SchemaMeta("japanese", "日语"),
            SchemaMeta("t9_pinyin", "中文九键"),
        )
        InputModes.saveOrder(context, listOf("rime_ice", "japanese", "t9_pinyin", InputModes.ENGLISH))

        assertTrue(reorderEnabledModes(context, installed, setOf("rime_ice", "t9_pinyin"),
            englishEnabled = true, reordered = listOf("t9_pinyin", "rime_ice", InputModes.ENGLISH)))
        assertEquals(listOf("t9_pinyin", "japanese", "rime_ice", InputModes.ENGLISH),
            orderedInstalledModeIds(context, installed))

        assertTrue(reorderEnabledModes(context, installed, setOf("rime_ice", "t9_pinyin"),
            englishEnabled = true, reordered = listOf("t9_pinyin", InputModes.ENGLISH, "rime_ice")))
        assertEquals(listOf("t9_pinyin", "japanese", InputModes.ENGLISH, "rime_ice"),
            InputModes.ordered(context, installed.map {
                SchemaInfo(it.schemaId, it.name, it.version, it.author, it.description)
            }).map { it.schemaId })
        assertFalse(reorderEnabledModes(context, installed, setOf("rime_ice", "t9_pinyin"),
            englishEnabled = true, reordered = listOf("t9_pinyin", InputModes.ENGLISH, InputModes.ENGLISH)))
    }

    @Test fun dragOrderPersistsAndSurvivesReenteringAndAnotherMove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.getString("input_mode_order", null)
        val initial = listOf(SchemaInfo("t9", "拼音", "", "", ""), SchemaInfo("japanese", "日语", "", "", ""))
        InputModes.saveOrder(context, listOf("t9", "rime_ice", "japanese", InputModes.ENGLISH))
        var modes by mutableStateOf(InputModes.ordered(context, initial))
        fun drag(fromTag: String, toTag: String) {
            val source = rule.onNodeWithTag(fromTag)
            val from = source.fetchSemanticsNode().boundsInRoot
            val to = rule.onNodeWithTag(toTag).fetchSemanticsNode().boundsInRoot
            rule.mainClock.autoAdvance = false
            try {
                source.performTouchInput { down(Offset(20f, center.y)) }
                rule.mainClock.advanceTimeBy(700)
                source.performTouchInput { moveBy(Offset(0f, to.center.y - from.center.y)); up() }
            } finally { rule.mainClock.autoAdvance = true }
            rule.waitForIdle()
        }
        try {
            rule.setContent { MaterialTheme {
                SchemaListView(schemas = modes, currentSchemaId = "t9", backgroundColor = Color.White,
                    accentColor = Color.Blue, keyTextColor = Color.Black, keyBgColor = Color.LightGray,
                    onSelectSchema = {}, onReorderSchemas = { ids ->
                        val visibleIds = modes.map { it.schemaId }
                        InputModes.saveReorderedModes(context, visibleIds, visibleIds, ids)
                        modes = InputModes.ordered(context, initial)
                    }, modifier = Modifier.size(360.dp, 250.dp))
            } }
            rule.onNodeWithText("模式顺序").performClick()
            rule.onAllNodesWithContentDescription("上移英文").assertCountEquals(0)
            drag("input-mode-order:t9", "input-mode-order:japanese")
            assertEquals(listOf("japanese", "t9", InputModes.ENGLISH), InputModes.ordered(context, initial).map { it.schemaId })
            drag("input-mode-order:${InputModes.ENGLISH}", "input-mode-order:t9")
            assertEquals(listOf("japanese", InputModes.ENGLISH, "t9"), InputModes.ordered(context, initial).map { it.schemaId })
            assertEquals("rime_ice", prefs.getString(InputModes.ORDER_KEY, "").orEmpty().lines()[1])
            rule.onNodeWithText("完成").performClick()
            rule.onNodeWithText("模式顺序").performClick()
            val english = rule.onNodeWithTag("input-mode-order:__xime_english").fetchSemanticsNode().boundsInRoot
            val t9 = rule.onNodeWithTag("input-mode-order:t9").fetchSemanticsNode().boundsInRoot
            assertTrue(english.top < t9.top)
        } finally { prefs.edit().also { if (saved == null) it.remove("input_mode_order") else it.putString("input_mode_order", saved) }.commit() }
    }
}
