package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/** The end-of-input re-read revises earlier words; it may only replace text still exactly where we put it. */
class StreamingVoiceTextRereadTest {
    private val editor = StringBuilder()
    private val ic: InputConnection = mock {
        on { getTextBeforeCursor(any(), any()) } doAnswer { val n = it.getArgument<Int>(0); editor.substring(maxOf(0, editor.length - n)) }
        on { commitText(anyOrNull(), any()) } doAnswer { editor.append(it.getArgument<CharSequence>(0)); true }
        on { deleteSurroundingText(any(), any()) } doAnswer { val n = it.getArgument<Int>(0); editor.setLength(maxOf(0, editor.length - n)); true }
    }

    @Test fun `untouched dictation is replaced by the re-read text`() {
        val text = StreamingVoiceText()
        text.update(ic, "今天天气不错 我们出去走走吧")
        text.update(ic, "今天天气不错，我们出去走走吧。")
        assertEquals("今天天气不错，我们出去走走吧。", editor.toString())
    }

    @Test fun `text the user already edited is left alone`() {
        val text = StreamingVoiceText()
        text.update(ic, "今天天气不错 我们出去走走吧")
        editor.append("！") // the user typed after the dictation
        text.update(ic, "今天天气不错，我们出去走走吧。")
        assertEquals("今天天气不错 我们出去走走吧！", editor.toString())
    }
}
