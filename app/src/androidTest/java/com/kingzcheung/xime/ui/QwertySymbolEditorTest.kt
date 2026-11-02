package com.kingzcheung.xime.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.CustomLayoutManager
import com.kingzcheung.xime.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QwertySymbolEditorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fixedLayoutEditsSymbolsAcrossSchemesWithoutCreatingLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = QwertySwipeSymbols.load(context, false)
        val english = QwertySwipeSymbols.load(context, true)
        val layouts = CustomKeyboardLayouts.load(context)
        val schema = SettingsPreferences.getCurrentSchema(context)
        try {
            rule.setContent { MaterialTheme { CustomLayoutManager("default", true) {} } }
            // 点 Q 键弹出小框，改上方符号，点「完成」即保存（没有单独的保存按钮）。
            fun openQ() {
                rule.onNodeWithText("编辑符号").performClick()
                rule.waitUntil(10_000) {
                    rule.onAllNodes(hasTestTag("symbol-editor-key:q") and hasClickAction() and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithTag("symbol-editor-key:q").performClick()
                rule.onNodeWithTag("symbol-editor-up").assertExists()
            }
            openQ()
            saveUiEvidence("qwerty-symbol-editor", rule.onRoot())
            rule.onNodeWithTag("symbol-editor-up").performTextReplacement("★")
            rule.onNodeWithTag("symbol-editor-up").assertTextContains("★")
            rule.onNodeWithText("完成").performClick()
            rule.waitUntil(10_000) { QwertySwipeSymbols.load(context, false)["up.q"] == "★" }
            rule.onNodeWithTag("symbol-editor-up").assertDoesNotExist()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("编辑符号").assertExists()
            openQ()
            rule.onNodeWithTag("symbol-editor-up").assertTextContains("★")
            saveUiEvidence("qwerty-symbol-editor-saved", rule.onRoot())
            assertEquals(layouts, CustomKeyboardLayouts.load(context))
            assertEquals(schema, SettingsPreferences.getCurrentSchema(context))
            assertEquals(english, QwertySwipeSymbols.load(context, true))
            for (id in listOf("rime_ice", "double_pinyin_flypy")) {
                KeysConfigHelper.setActiveKeyboardSchema(id)
                assertEquals("★", KeysConfigHelper.getSwipeUpCommitValue("q", false))
                assertEquals("q", KeysConfigHelper.getKeyCommitValue("q", false))
            }
        } catch (failure: Throwable) {
            saveUiEvidence("qwerty-symbol-editor-failure", rule.onRoot())
            android.util.Log.e("SymbolEditorTest", "prefs=" + SettingsPreferences.getPrefsPublic(context).all)
            throw failure
        } finally {
            QwertySwipeSymbols.save(context, false, previous)
            KeysConfigHelper.setActiveKeyboardSchema(schema)
        }
    }
}
