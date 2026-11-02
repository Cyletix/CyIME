package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.service.*
import com.kingzcheung.xime.ui.keyboard.EditKeyboardLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h760dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorPlainPasteTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun holdingPastePreservesAllSourceTextAndDropsFormatting() {
        val editor = EditText(rule.activity)
        val service = mock<XimeInputMethodService> {
            on { uiState } doReturn mutableStateOf(InputUIState(toolPanelInputFocused = true))
            on { currentInputConnection } doReturn editor.onCreateInputConnection(EditorInfo())
        }
        val controller = ImeSchemaController(service)
        val actions = mutableListOf<String>()
        val source = ("<p>你好 &amp; welcome</p>\n").repeat(4200)
        val styled = SpannableString(source).apply { setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val clip = ClipData.newHtmlText("HTML 源码", styled, "<b>formatted source</b>")
        try {
            ToolPanelEditTextHolder.editText = editor
            rule.setContent {
                Column {
                    AndroidView({editor}, Modifier.fillMaxWidth().height(60.dp))
                    EditKeyboardLayout(onAction = { actions.add(it); controller.handleToolbarEditingAction(it) }, onBack = {},
                        backgroundColor = Color.DarkGray, textColor = Color.White, accentColor = Color.Cyan, keyBgColor = Color.Gray,
                        modifier = Modifier.fillMaxWidth().height(240.dp).testTag("edit"))
                }
            }
            rule.runOnIdle {
                editor.requestFocus()
                editor.setText("前替换后")
                editor.setSelection(1, 3)
                (rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            }
            val paste = rule.onNodeWithContentDescription("粘贴")
            paste.performTouchInput { longClick() }
            rule.runOnIdle {
                assertEquals(listOf("paste_plain"), actions)
                assertEquals("前"+source+"后", editor.text.toString())
                assertTrue(editor.text.getSpans(0, editor.length(), StyleSpan::class.java).isEmpty())
                editor.setText(""); editor.setSelection(0)
            }
            paste.performTouchInput { click() }
            rule.runOnIdle {
                assertEquals(listOf("paste_plain", "paste"), actions)
                assertEquals(source, editor.text.toString())
                assertTrue(editor.text.getSpans(0, editor.length(), StyleSpan::class.java).isNotEmpty())
            }
        } finally { ToolPanelEditTextHolder.editText = null }
    }

}
