package com.kingzcheung.xime.ocr

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.service.QuickSendFormEditTextHolder
import com.kingzcheung.xime.ui.keyboard.QuickSendFormArea
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real touch injection: the editor lives in a popup sub-window where Android has no text menu. */
class PopupEditorSelectionTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun screen(name: String) {
        rule.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.cacheDir, "ui-verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun inject(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    /** Screen position of a text offset in the editor, at the vertical middle of its line. */
    private fun point(offset: Int): Pair<Float, Float> = rule.runOnIdle {
        val editor = QuickSendFormEditTextHolder.editText!!
        val layout = editor.layout
        val line = layout.getLineForOffset(offset)
        val xy = IntArray(2).also(editor::getLocationOnScreen)
        Pair(xy[0] + editor.totalPaddingLeft + layout.getPrimaryHorizontal(offset) - editor.scrollX,
            xy[1] + editor.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f - editor.scrollY)
    }

    private fun longPressAndDrag(from: Pair<Float, Float>, to: Pair<Float, Float>?) {
        val down = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, down, from.first, from.second)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 300L)
        if (to != null) for (step in 1..8) {
            inject(MotionEvent.ACTION_MOVE, down, from.first + (to.first - from.first) * step / 8,
                from.second + (to.second - from.second) * step / 8)
            SystemClock.sleep(20)
        }
        val end = to ?: from
        inject(MotionEvent.ACTION_UP, down, end.first, end.second)
        rule.waitForIdle()
    }

    private fun selection() = rule.runOnIdle {
        QuickSendFormEditTextHolder.editText!!.let { it.text.substring(it.selectionStart, it.selectionEnd) }
    }

    @Test fun longPressSelectsDragsAndCopiesInClipboardAndOcrEditors() {
        var ocr by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xff101018)), contentAlignment = Alignment.BottomStart) {
                    Column(Modifier.fillMaxWidth().height(220.dp)) {
                        if (ocr) OcrReviewEditor("识别结果 Hello world 第二段文字", Color.White, Color(0xff252630),
                            Color(0xffc6b0f4), {}, {}, {}, {})
                        else QuickSendFormArea(Color.Transparent, Color.White, Color(0xffc6b0f4), true,
                            initialText = "剪贴板 Hello world 测试内容", cardBgColor = Color(0xff252630), editingItemId = 7L,
                            onClose = { _, _ -> }, onCancel = {}, onFocusChange = {}, onCodeFocusChange = {})
                        Box(Modifier.fillMaxSize().background(Color(0xff33343f)))
                    }
                }
            }
        }
        for (name in listOf("clipboard", "ocr")) {
            if (name == "ocr") rule.runOnIdle { ocr = true }
            rule.waitForIdle()
            val text = rule.runOnIdle { QuickSendFormEditTextHolder.editText!!.text.toString() }
            val hello = text.indexOf("Hello")
            longPressAndDrag(point(hello + 2), null)
            assertEquals("$name: long-press selects the word", "Hello", selection())
            rule.onNodeWithText("复制").assertIsDisplayed()
            rule.onNodeWithText("保存").assertDoesNotExist()
            screen("popup-selection-$name-word")
            // Drag the drawn end handle over the next word.
            val world = text.indexOf("world")
            longPressAndDrag(point(hello + 5), point(world + 5))
            assertEquals("$name: dragging the end handle extends the selection", "Hello world", selection())
            screen("popup-selection-$name-extended")
            rule.onNodeWithText("复制").performClick()
            val clip = rule.runOnIdle {
                (instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .primaryClip?.getItemAt(0)?.text?.toString()
            }
            assertEquals("$name: copy uses the selection", "Hello world", clip)
            rule.onNodeWithText("保存").assertIsDisplayed()
        }
    }
}
