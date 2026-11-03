package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板搜索、新增固定等键盘自己的框里，打字时最后一个拼音字母删不掉（11-03）：删空后清掉组合会换新的候选版本号，
 * 清之前读的结果已过期，发布时被丢掉，候选栏一直停在最后那个字母。
 */
class BackspaceCompositionTest {
    /** Stands in for RimeEngine: every mutation starts a new revision; a read carries the current one. */
    private class FakeEngine(var input: String) {
        var revision = 1L
        var clears = 0
        fun backspace(): RimeProcessResult {
            revision++
            input = input.dropLast(1)
            return read(processed = true)
        }
        fun clear() { revision++; clears++ }
        fun read(processed: Boolean) =
            RimeProcessResult(processed, "", input, input, emptyArray(), false, false, false, engineRevision = revision)
    }

    private fun FakeEngine.deleteKey() = backspaceComposition(this::backspace, this::clear) { read(it.processed) }

    @Test fun deletingTheLastLetterPublishesAnEmptyCompositionWithTheCurrentRevision() {
        val engine = FakeEngine("n")
        val result = engine.deleteKey()
        assertEquals("", result.inputText)
        assertEquals(1, engine.clears)
        assertEquals(engine.revision, result.engineRevision)
        assertTrue(result.processed)
    }

    @Test fun deletingWhileLettersRemainDoesNotClear() {
        val engine = FakeEngine("ni")
        val result = engine.deleteKey()
        assertEquals("n", result.inputText)
        assertEquals(0, engine.clears)
        assertEquals(engine.revision, result.engineRevision)
    }

    @Test fun everyDeleteOfAWordIsPublishable() {
        val engine = FakeEngine("nihao")
        repeat(5) {
            val result = engine.deleteKey()
            assertEquals("each delete must reach the screen", engine.revision, result.engineRevision)
        }
        assertEquals("", engine.input)
    }
}
