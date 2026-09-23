package com.kingzcheung.xime.service

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorVerticalCursorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun actualWrappedLinesMoveVerticallyWithoutLosingEditorFocus() {
        lateinit var editor: EditText
        lateinit var connection: InputConnection
        val cursor = EditorCursor()
        rule.setContent { AndroidView(factory = { EditText(it).also { view -> editor = view; view.setText("あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめも") } }, modifier = Modifier.size(220.dp, 220.dp)) }
        rule.runOnUiThread {
            editor.requestFocus()
            assertTrue(editor.layout.lineCount >= 3)
            editor.setSelection(editor.layout.getLineStart(1) + 2)
            connection = editor.onCreateInputConnection(EditorInfo())!!
            cursor.moveVertical(connection, -1)
        }
        rule.waitUntil(3000) { var line = -1; rule.runOnUiThread { line = editor.layout.getLineForOffset(editor.selectionEnd) }; line == 0 }
        rule.runOnUiThread { assertTrue(editor.hasFocus()); cursor.moveVertical(connection, 1) }
        rule.waitUntil(3000) { var line = -1; rule.runOnUiThread { line = editor.layout.getLineForOffset(editor.selectionEnd) }; line == 1 }
        rule.runOnUiThread { editor.setSelection(0); cursor.moveVertical(connection, -20); assertEquals(0, editor.selectionEnd); assertTrue(editor.hasFocus()) }
        rule.runOnUiThread { editor.setSelection(editor.length()); cursor.moveVertical(connection, 20); assertEquals(editor.length(), editor.selectionEnd); assertTrue(editor.hasFocus()) }
    }
}
