package com.kingzcheung.xime.service

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class PhraseLineCommitTest {
    private fun recording(accept: Boolean, calls: MutableList<String>) = mock<InputConnection> {
        on { beginBatchEdit() } doAnswer { calls += "begin"; true }
        on { endBatchEdit() } doAnswer { calls += "end"; true }
        on { commitText(anyOrNull(), any()) } doAnswer {
            calls += "${it.getArgument<CharSequence>(0)}@${it.getArgument<Int>(1)}"; accept
        }
    }

    @Test
    fun `lines go up one by one like pressing enter between them`() {
        val calls = mutableListOf<String>()
        assertTrue(recording(true, calls).commitPhraseLines("多行短语可以换行上屏\n但是有个bug\n\n就是"))
        // The first line replaces the code in the composing region, the usual one-line commit.
        assertEquals(listOf("begin", "多行短语可以换行上屏@1", "\n@1", "但是有个bug@1", "\n@1", "\n@1", "就是@1", "end"), calls)
    }

    @Test
    fun `an empty first line still replaces the code and a refused commit stops`() {
        val calls = mutableListOf<String>()
        assertFalse(recording(false, calls).commitPhraseLines("\n第二行"))
        assertEquals(listOf("begin", "@1", "end"), calls)
    }
}
