package com.kingzcheung.xime.ui.caption

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CaptionOrderTest {
    @Test fun `a late piece of an earlier sentence goes before the next sentence`() {
        val list = mutableListOf(SegmentUi(1, 1_000), SegmentUi(2, 4_000))
        // Piece split off sentence 1 (said at 2.5 s) arrives after sentence 2 is already shown.
        val piece = SegmentUi(3, 2_500)
        list.add(insertionIndex(list, piece.wallMs), piece)
        assertEquals(listOf(1L, 3L, 2L), list.map { it.id })
        // Ordinary new sentences still go to the end.
        val next = SegmentUi(4, 6_000)
        list.add(insertionIndex(list, next.wallMs), next)
        assertEquals(4L, list.last().id)
    }

    @Test fun `the turned card is remembered across closing and reopening`() {
        val store = HashMap<String, Boolean>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(editor.putBoolean(any(), any())).thenAnswer { store[it.getArgument(0)] = it.getArgument(1); editor }
        val prefs = mock<SharedPreferences>()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getBoolean(any(), any())).thenAnswer { store[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1) }
        assertFalse(ReplyFlipMemory(prefs).read())
        ReplyFlipMemory(prefs).write(true)
        // A fresh page (quick reply reopened, captions or the app restarted) reads the same store.
        assertTrue(ReplyFlipMemory(prefs).read())
        ReplyFlipMemory(prefs).write(false)
        assertFalse(ReplyFlipMemory(prefs).read())
    }
}
