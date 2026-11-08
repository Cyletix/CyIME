package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.service.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 用户 11-08：剪贴板编辑栏里左右键能用，上下键一按就没反应；改过多次又坏。
 * 编辑栏在 focusable=false 的悬浮窗里，窗口没有焦点，真机上发给它的按键事件会被系统直接丢掉
 * （ViewRootImpl：Dropping event due to no window focus）。Robolectric 不会丢，所以这里的输入框也把按键事件丢掉，
 * 和真机一样：哪天上下键又改回“发方向键给多行框”，这条测试就会失败。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h760dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardEditorArrowKeysTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class NoWindowFocusEditText(context: Context) : EditText(context) {
        val droppedKeys = mutableListOf<Int>()
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val real = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(real, false) {
                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    droppedKeys += event.keyCode
                    return false
                }
            }
        }
    }

    @Test fun editPanelUpDownAndLineEndMoveTheCursorInTheClipboardEditor() {
        val editor = NoWindowFocusEditText(rule.activity).apply {
            // 和 QuickSendFormArea 的剪贴板编辑框一样是多行框：以前的问题就出在多行框走“发方向键”这条路。
            isSingleLine = false
            typeface = Typeface.MONOSPACE
            textSize = 16f
        }
        val service = mock<XimeInputMethodService> {
            on { uiState } doReturn mutableStateOf(InputUIState(quickSendFormFocused = true))
            on { currentInputConnection } doReturn mock<InputConnection>()
        }
        val controller = ImeSchemaController(service)
        try {
            QuickSendFormEditTextHolder.editText = editor
            rule.setContent { AndroidView({ editor }, Modifier.fillMaxWidth().height(160.dp)) }
            rule.runOnIdle {
                editor.requestFocus()
                editor.setText("abcd\nefgh\nijkl")
                editor.setSelection(7) // 第二行第 3 个字前
            }
            rule.runOnIdle {
                controller.handleToolbarEditingAction("arrow_up")
                assertEquals(2, editor.selectionStart)
                controller.handleToolbarEditingAction("arrow_down")
                controller.handleToolbarEditingAction("arrow_down")
                assertEquals(12, editor.selectionStart)
                // 空格上下滑走另一条路，同样要能动。
                controller.moveEditorCursorVertical(-1)
                assertEquals(7, editor.selectionStart)
                controller.handleToolbarEditingAction("select_arrow_down")
                assertEquals(7, editor.selectionStart)
                assertEquals(12, editor.selectionEnd)
                controller.handleToolbarEditingAction("line_end")
                assertEquals(14, editor.selectionStart)
                controller.handleToolbarEditingAction("arrow_up")
                controller.handleToolbarEditingAction("line_start")
                assertEquals(5, editor.selectionStart)
                assertEquals(emptyList<Int>(), editor.droppedKeys)
            }
        } finally {
            QuickSendFormEditTextHolder.editText = null
        }
    }
}
