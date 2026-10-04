package com.kingzcheung.xime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.service.QuickSendFormCodeEditTextHolder
import com.kingzcheung.xime.service.QuickSendFormEditTextHolder
import com.kingzcheung.xime.ui.keyboard.QuickSendFormArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ClipboardFormDraftTest {
    @get:Rule val rule = createComposeRule()

    @Test fun focusAndThemeRecompositionPreserveEditedTextCodeAndSelection() {
        val focused = mutableStateOf(true)
        val textColor = mutableStateOf(Color.White)
        var saved: Pair<String, String>? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 240.dp)) {
                    QuickSendFormArea(
                        backgroundColor = Color.Transparent,
                        textColor = textColor.value,
                        accentColor = Color.Cyan,
                        isFocused = focused.value,
                        initialText = "原内容",
                        initialCode = "old",
                        cardBgColor = Color.DarkGray,
                        editingItemId = 7L,
                        onClose = { text, code -> saved = text to code },
                        onCancel = {},
                        onFocusChange = {},
                        onCodeFocusChange = {},
                    )
                }
            }
        }
        rule.onNodeWithText("编辑剪贴板").assertExists()
        rule.onNode(isPopup()).assertExists()
        assertEquals("editor must not stretch the keyboard", 0f,
            rule.onNodeWithTag("clipboard-editor-anchor").fetchSemanticsNode().boundsInRoot.height, 0f)
        rule.runOnIdle {
            QuickSendFormEditTextHolder.editText!!.apply {
                setText("已修改的内容")
                setSelection(2)
            }
            QuickSendFormCodeEditTextHolder.editText!!.apply {
                setText("newcode")
                setSelection(1, 4)
            }
            focused.value = false
            textColor.value = Color.Magenta
        }
        rule.runOnIdle {
            QuickSendFormEditTextHolder.editText!!.let {
                assertEquals("已修改的内容", it.text.toString())
                assertEquals(2, it.selectionStart)
                assertEquals(Color.Magenta.toArgb(), it.currentTextColor)
            }
            QuickSendFormCodeEditTextHolder.editText!!.let {
                assertEquals("newcode", it.text.toString())
                assertEquals(1, it.selectionStart)
                assertEquals(4, it.selectionEnd)
            }
        }
        rule.onNodeWithText("保存").performClick()
        rule.runOnIdle { assertEquals("已修改的内容" to "newcode", saved) }
    }

    @Test fun changingRecordStartsItsOwnEmptyDraftAndClosingReleasesEditors() {
        val itemId = mutableStateOf(7L)
        val visible = mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                if (visible.value) {
                    QuickSendFormArea(
                        backgroundColor = Color.Transparent,
                        textColor = Color.White,
                        accentColor = Color.Cyan,
                        isFocused = false,
                        initialText = if (itemId.value == 7L) "旧内容" else "",
                        initialCode = if (itemId.value == 7L) "old" else "",
                        cardBgColor = Color.DarkGray,
                        editingItemId = itemId.value,
                        onClose = { _, _ -> },
                        onCancel = {},
                        onFocusChange = {},
                        onCodeFocusChange = {},
                    )
                }
            }
        }
        val previousEditor = rule.runOnIdle {
            QuickSendFormEditTextHolder.editText!!.also {
                it.setText("旧条目的草稿")
                itemId.value = 8L
            }
        }
        rule.runOnIdle {
            assertNotSame(previousEditor, QuickSendFormEditTextHolder.editText)
            assertEquals("", QuickSendFormEditTextHolder.editText!!.text.toString())
            assertEquals("", QuickSendFormCodeEditTextHolder.editText!!.text.toString())
            visible.value = false
        }
        rule.runOnIdle {
            assertNull(QuickSendFormEditTextHolder.editText)
            assertNull(QuickSendFormCodeEditTextHolder.editText)
        }
    }

    @Test fun closeCancelsDraftWithoutSavingIt() {
        val visible = mutableStateOf(true)
        var saveCount = 0
        var cancelCount = 0
        rule.setContent {
            MaterialTheme {
                if (visible.value) {
                    QuickSendFormArea(
                        backgroundColor = Color.Transparent,
                        textColor = Color.White,
                        accentColor = Color.Cyan,
                        isFocused = false,
                        initialText = "保留原内容",
                        initialCode = "old",
                        cardBgColor = Color.DarkGray,
                        editingItemId = 7L,
                        onClose = { _, _ -> saveCount++ },
                        onCancel = { cancelCount++; visible.value = false },
                        onFocusChange = {},
                        onCodeFocusChange = {},
                    )
                }
            }
        }
        rule.runOnIdle {
            QuickSendFormEditTextHolder.editText!!.setText("不保存的草稿")
            QuickSendFormCodeEditTextHolder.editText!!.setText("new")
        }
        rule.onNodeWithContentDescription("关闭").performClick()
        rule.runOnIdle {
            assertEquals(0, saveCount)
            assertEquals(1, cancelCount)
            assertNull(QuickSendFormEditTextHolder.editText)
            assertNull(QuickSendFormCodeEditTextHolder.editText)
        }
    }

    @Test fun blankSaveStaysInTheEditorAndExplainsTheProblem() {
        var saveCount = 0
        var cancelCount = 0
        rule.setContent {
            MaterialTheme {
                QuickSendFormArea(
                    backgroundColor = Color.Transparent,
                    textColor = Color.White,
                    accentColor = Color.Cyan,
                    isFocused = false,
                    initialText = " ",
                    initialCode = "keep",
                    cardBgColor = Color.DarkGray,
                    onClose = { _, _ -> saveCount++ },
                    onCancel = { cancelCount++ },
                    onFocusChange = {},
                    onCodeFocusChange = {},
                )
            }
        }
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("新增固定").assertExists()
        rule.runOnIdle {
            assertEquals(0, saveCount)
            assertEquals(0, cancelCount)
            assertEquals("内容不能为空", QuickSendFormEditTextHolder.editText!!.error.toString())
            assertEquals(" ", QuickSendFormEditTextHolder.editText!!.text.toString())
            assertEquals("keep", QuickSendFormCodeEditTextHolder.editText!!.text.toString())
        }
    }
}
