package com.kingzcheung.xime.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.QuickSendFormEditTextHolder
import com.kingzcheung.xime.service.QuickSendFormCodeEditTextHolder
import com.kingzcheung.xime.service.XimeInputMethodService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real service callbacks and actual popup editors. Pointer timing is covered separately by SpaceKeyGestureTest. */
class InternalEditorCursorImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cursorCallbacksTargetClipboardOcrAndCodeThenReturnToHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var host: EditText
        rule.setContent {
            AndroidView(factory = { EditText(it).also { view -> host = view; view.setText("host stays here") } },
                modifier = Modifier.fillMaxWidth().height(80.dp))
        }
        rule.runOnUiThread {
            host.requestFocus(); host.setSelection(3)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(host); imm.showSoftInput(host, InputMethodManager.SHOW_IMPLICIT)
        }
        var service: XimeInputMethodService? = null
        rule.waitUntil(30_000) {
            rule.runOnUiThread { service = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findService) }
            service?.keyboardCallbacks != null
        }
        val ime = checkNotNull(service)
        val callbacks = checkNotNull(ime.keyboardCallbacks)
        try {
            for (ocr in listOf(false, true)) {
                rule.runOnUiThread {
                    if (ocr) callbacks.onReviewOcrText!!.invoke("a😀bc\n第二行")
                    else callbacks.onShowQuickSendForm!!.invoke()
                }
                rule.waitUntil(5_000) { QuickSendFormEditTextHolder.editText != null }
                rule.waitForIdle()
                val draft = QuickSendFormEditTextHolder.editText!!
                rule.runOnUiThread {
                    draft.requestFocus(); draft.setText("a😀bc\n第二行"); draft.setSelection(5)
                    assertTrue(ime.uiState.value.quickSendFormFocused)
                    callbacks.onCursorMove!!.invoke(-2)
                    assertEquals(3, draft.selectionEnd)
                    callbacks.onCursorMove!!.invoke(-1)
                    assertEquals("Emoji stays intact", 1, draft.selectionEnd)
                    callbacks.onCursorMove!!.invoke(-20)
                    assertEquals(0, draft.selectionEnd)
                    draft.setSelection(7)
                    callbacks.onCursorMoveVertical!!.invoke(-1)
                    assertTrue("Vertical movement stays in the draft", draft.selectionEnd < 6)
                    assertEquals("a😀bc\n第二行", draft.text.toString())
                    assertEquals(3, host.selectionEnd); assertEquals("host stays here", host.text.toString())
                    if (!ocr) {
                        val code = QuickSendFormCodeEditTextHolder.editText!!
                        code.requestFocus(); code.setText("code"); code.setSelection(4)
                        callbacks.onCursorMove!!.invoke(-2)
                        assertEquals(2, code.selectionEnd); assertEquals("code", code.text.toString())
                    }
                    draft.requestFocus(); draft.setSelection(3)
                    callbacks.onKeyPress("space", false)
                }
                rule.waitUntil(5_000) { draft.text.toString() == "a😀 bc\n第二行" }
                rule.runOnIdle { assertEquals(3, host.selectionEnd); assertEquals("host stays here", host.text.toString()) }
                rule.runOnUiThread {
                    draft.setSelection(4)
                    callbacks.onKeyPress("enter", false)
                }
                rule.waitUntil(5_000) { draft.text.toString() == "a😀 \nbc\n第二行" }
                rule.runOnUiThread {
                    assertTrue(ime.uiState.value.showQuickSendForm)
                    draft.setSelection(5, 7)
                    ime.onKeyDown(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ime.onKeyUp(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                rule.waitUntil(5_000) { draft.text.toString() == "a😀 \n\n\n第二行" }
                rule.runOnUiThread {
                    assertTrue(ime.uiState.value.showQuickSendForm)
                    assertSame(draft, QuickSendFormEditTextHolder.editText)
                    assertEquals("host stays here", host.text.toString())
                }
                val image = instrumentation.uiAutomation.takeScreenshot()
                val directory = File(context.cacheDir, "ui-verification").apply { mkdirs() }
                File(directory, "internal-editor-cursor-${if (ocr) "ocr" else "clipboard"}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
                rule.runOnUiThread { callbacks.onHideQuickSendForm!!.invoke() }
                rule.waitUntil(5_000) { QuickSendFormEditTextHolder.editText == null }
            }
            rule.runOnUiThread { callbacks.onCursorMove!!.invoke(1) }
            rule.waitUntil(5_000) { host.selectionEnd == 4 }
            rule.runOnIdle { assertEquals("host stays here", host.text.toString()) }
        } finally {
            rule.runOnUiThread { callbacks.onHideQuickSendForm!!.invoke() }
        }
    }

    private fun findService(view: View): XimeInputMethodService? {
        var context: Context? = view.context
        while (context != null) {
            if (context is XimeInputMethodService) return context
            context = (context as? ContextWrapper)?.baseContext?.takeUnless { it === context }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) findService(view.getChildAt(index))?.let { return it }
        return null
    }
}
