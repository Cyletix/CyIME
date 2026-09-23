package com.kingzcheung.xime.service

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 使用真实 EditText/InputConnection 验证增量上屏，ASR 文本事件由测试提供。 */
class StreamingVoiceTextTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun realEditorReceivesSpaceSeparatedSpeechPartialsAndFinals() {
        lateinit var editor: EditText
        rule.setContent { AndroidView(factory = { EditText(it).also { view -> editor = view } }) }
        rule.runOnUiThread {
            editor.requestFocus()
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            var state = InputUIState(voiceSticky = true)
            val handler = VoiceRecognitionHandler(rule.activity, { state = it }, { state }, { ic })
            VoiceRecognitionHandler::class.java.getDeclaredField("toolbarSession").apply { isAccessible = true }.setBoolean(handler, true)
            fun emit(method: String, text: String) {
                VoiceRecognitionHandler::class.java.getDeclaredMethod(method, String::class.java).apply { isAccessible = true }.invoke(handler, text)
            }
            emit("handlePartialResult", "你好，wor")
            assertEquals("你好 wor", editor.text.toString())
            emit("handleSpeechResult", "你好，world。")
            assertEquals("你好 world", editor.text.toString())
            emit("handlePartialResult", "How are")
            assertEquals("你好 world How are", editor.text.toString())
            emit("handleSpeechResult", "How are you？")
            assertEquals("你好 world How are you", editor.text.toString())
            handler.abandonSession()
            emit("handlePartialResult", "late result")
            assertEquals("你好 world How are you", editor.text.toString())
        }
    }

    @Test fun partialsAreVisibleImmediatelyAndOnlyOwnedTextCanBeRevised() {
        lateinit var editor: EditText
        rule.setContent { AndroidView(factory = { EditText(it).also { view -> editor = view } }) }
        rule.runOnUiThread {
            editor.requestFocus()
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            val stream = StreamingVoiceText()
            stream.update(ic, "你")
            assertEquals("第一段部分结果应立即显示", "你", editor.text.toString())
            stream.update(ic, "你好")
            assertEquals("后续部分结果实时追加", "你好", editor.text.toString())
            stream.update(ic, "您好")
            assertEquals("修订不能重复追加上一部分", "您好", editor.text.toString())
            stream.update(ic, "您好。")
            stream.reset()
            assertEquals("您好。", editor.text.toString())
            stream.update(ic, "第二句")
            ic.commitText("手动输入", 1)
            stream.update(ic, "第二句话")
            assertEquals("用户手动文字不能被识别结果回删", "您好。第二句手动输入话", editor.text.toString())
            stream.update(ic, "第二句话。")
            assertEquals("您好。第二句手动输入话。", editor.text.toString())
            editor.setSelection(0, 2)
            stream.update(ic, "第二句话还有后文")
            assertEquals("用户选区不能被部分结果覆盖", "您好。第二句手动输入话。", editor.text.toString())
        }
    }
    @Test fun movingToIdenticalEarlierTextDoesNotGiveVoiceOwnershipOfIt() {
        lateinit var editor: EditText
        rule.setContent { AndroidView(factory = { EditText(it).also { view -> editor = view } }) }
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setText("你好，旧文字。")
            editor.setSelection(editor.text.length)
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            val stream = StreamingVoiceText()
            stream.update(ic, "你好")
            assertEquals("你好，旧文字。你好", editor.text.toString())
            editor.setSelection(2)
            stream.update(ic, "您好")
            assertEquals("光标前文字虽相同，也不能修改旧段落", "你好，旧文字。你好", editor.text.toString())
        }
    }

}
