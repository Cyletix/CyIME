package com.kingzcheung.xime.service

import org.junit.Assert.*
import org.junit.Test

class ChineseGlideDeleteHistoryTest {
    private fun state(input: String, revision: Long, caret: Int = input.length) =
        ChineseGlideDeleteHistory.State(input, caret, 0, "", "rime_ice", 1, InputTarget.Host, revision)

    @Test fun consecutiveGlidesDeleteInReverseOrder() {
        val history = ChineseGlideDeleteHistory()
        val empty = state("", 1)
        val first = state("nihao", 2)
        val second = state("nihao'zhongguo", 3)
        history.record(empty, first, "nihao", listOf("ni", "hao"))
        history.record(first, second, "zhongguo", listOf("zhong", "guo"))
        assertEquals("nihao'zhong", history.last(second)?.remainingInput)
        val afterGuo = state("nihao'zhong", 4)
        history.removed(afterGuo)
        assertEquals("nihao", history.last(afterGuo)?.remainingInput)
        val restored = first.copy(revision = 5)
        history.removed(restored)
        assertEquals("ni", history.last(restored)?.remainingInput)
    }

    @Test fun typedOrSelectedChangesInvalidateTheHistory() {
        val history = ChineseGlideDeleteHistory()
        val first = state("nihao", 2)
        history.record(state("", 1), first, "nihao", listOf("ni", "hao"))
        assertNull(history.last(first.copy(revision = 3)))
        assertNull(history.last(first))
        history.record(state("", 4), state("nihaox", 5), "nihao", listOf("ni", "hao"))
        assertNull(history.last(state("nihaox", 5)))
    }

    @Test fun undoingClearPreservesTheLatestGlide() {
        val history = ChineseGlideDeleteHistory()
        val initial = state("", 1)
        val first = state("nihao", 2)
        history.record(initial, first, "nihao", listOf("ni", "hao"))
        val restored = first.copy(revision = 5)
        history.refresh(restored)
        assertEquals("ni", history.last(restored)?.remainingInput)
    }

    @Test fun glideInTheMiddleRestoresItsOriginalCaret() {
        val history = ChineseGlideDeleteHistory()
        val before = state("nim", 1, 2)
        val after = state("nihaom", 2, 5)
        history.record(before, after, "hao", listOf("hao"))
        assertEquals(before, history.last(after)?.before)
        assertNull(history.last(after.copy(caret = 6)))
    }

    @Test fun doublePinyinDeletesOneCharacterAtATime() {
        val history = ChineseGlideDeleteHistory()
        val after = state("hvjk", 2)
        history.record(state("", 1), after, "hvjk", listOf("hv", "jk"))
        assertEquals("hv", history.last(after)?.remainingInput)
        val next = state("hv", 3)
        history.removed(next)
        assertEquals("", history.last(next)?.remainingInput)
    }

    @Test fun typingAndDeletingAfterGlideReturnsToSyllableDeletion() {
        val history = ChineseGlideDeleteHistory()
        val afterGlide = state("hvjk", 2)
        history.record(state("", 1), afterGlide, "hvjk", listOf("hv", "jk"))
        assertNull(history.last(state("hvjkn", 3)))
        val backToGlide = state("hvjk", 4)
        assertEquals("hv", history.last(backToGlide)?.remainingInput)
        history.removed(state("hv", 5))
        assertEquals("", history.last(state("hv", 5))?.remainingInput)
    }

    @Test fun glideAfterTypedInputKeepsEarlierGlideHistory() {
        val history = ChineseGlideDeleteHistory()
        val first = state("hvjk", 2)
        val typed = state("hvjkn", 3)
        val second = state("hvjkn'aa", 4)
        history.record(state("", 1), first, "hvjk", listOf("hv", "jk"))
        history.record(typed, second, "aa", listOf("aa"))
        assertEquals("hvjkn", history.last(second)?.remainingInput)
        val afterSecond = typed.copy(revision = 5)
        history.removed(afterSecond)
        assertNull(history.last(afterSecond))
        assertEquals("hv", history.last(first.copy(revision = 6))?.remainingInput)
    }

    @Test fun typedInputBeforeGlideBecomesSyllableDeletionHistory() {
        val history = ChineseGlideDeleteHistory()
        val typed = state("hvjk", 2)
        val tail = history.typedTail(typed)!!
        assertEquals("", tail.before.input)
        history.record(tail.before, typed, tail.code, listOf("hv", "jk"))
        val glided = state("hvjk'aa", 3)
        history.record(typed, glided, "aa", listOf("aa"))
        assertEquals("hvjk", history.last(glided)?.remainingInput)
        val restored = typed.copy(revision = 4)
        history.removed(restored)
        assertEquals("hv", history.last(restored)?.remainingInput)
    }

    @Test fun typedSuffixAfterAnEarlierGlideIsRecordedSeparately() {
        val history = ChineseGlideDeleteHistory()
        val first = state("hvjk", 2)
        history.record(state("", 1), first, "hvjk", listOf("hv", "jk"))
        val typed = state("hvjkni", 3)
        val tail = history.typedTail(typed)!!
        assertEquals(first, tail.before)
        assertEquals("ni", tail.code)
        history.record(tail.before, typed, tail.code, listOf("ni"))
        assertEquals("hvjk", history.last(typed)?.remainingInput)
        val restored = first.copy(revision = 4)
        history.removed(restored)
        assertEquals("hv", history.last(restored)?.remainingInput)
    }
}
