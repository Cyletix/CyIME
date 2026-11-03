package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 宿主在打拼音时重启同一个输入框（QQ 留言板：文字一变就 setText，框架把组合区定格成普通文字、光标放回开头）。
 * 用一个模拟输入框走一遍：原来每打一个字母编码就被撤掉、拼音清空（「打一两个字母全没了」）；
 * 现在编码原样还在原处时重新标成组合区，接着打。
 */
class InlineCompositionRestartTest {
    /** 只模拟本题用到的宿主行为：组合区、光标、setText 后重启输入。 */
    private class FakeEditor(initial: String, private val resetsOnChange: Boolean, private val caretAfterReset: Int? = 0) {
        val text = StringBuilder(initial)
        var selection = initial.length
        var composing: IntRange? = null
        var restarts = 0
        var pendingRestart = false

        fun setComposingText(value: String) {
            val range = composing ?: selection until selection
            text.replace(range.first, range.last + 1, value)
            composing = range.first until range.first + value.length
            selection = range.first + value.length
            changed()
        }

        fun setComposingRegion(region: DetachedComposition): Boolean {
            if (region.start < 0 || region.end > text.length) return false
            composing = region.start until region.end
            return true
        }

        fun commitText(value: String) {
            val range = composing ?: selection until selection
            text.replace(range.first, range.last + 1, value)
            composing = null
            selection = range.first + value.length
            changed()
        }

        /** 宿主文字监听里 setText(同样的文字)：组合区没了，光标回开头，输入法收到重启。 */
        private fun changed() {
            if (!resetsOnChange) return
            composing = null
            caretAfterReset?.let { selection = it }
            restarts++
            pendingRestart = true
        }

        val before get() = text.substring(0, selection)
        val after get() = text.substring(selection)
    }

    /** CyIME 这一侧：拼音编码写进输入框（编码显示在输入框），重启时按 [resume] 选新旧做法。 */
    private class FakeIme(val editor: FakeEditor, val resume: Boolean) {
        val tracker = InlineCompositionTracker()
        var code = ""

        init { tracker.selectionReported(editor.selection, editor.selection, -1, -1) }

        fun type(letters: String, reportBeforeRestart: Boolean = false) = letters.forEach { c ->
            code += c
            editor.setComposingText(code)
            tracker.shown(code)
            if (reportBeforeRestart) report()
            deliverRestart()
            report()
        }

        fun choose(word: String) {
            editor.commitText(word)
            code = ""
            tracker.ended()
            deliverRestart()
            report()
        }

        private fun report() {
            val c = editor.composing
            tracker.selectionReported(editor.selection, editor.selection, c?.first ?: -1, c?.let { it.last + 1 } ?: -1)
        }

        private fun deliverRestart() {
            if (!editor.pendingRestart) return
            editor.pendingRestart = false
            val text = tracker.text
            if (resume && tracker.active && text != null) {
                val region = locateResumableComposition(text, editor.selection, editor.selection, editor.before, editor.after,
                    tracker.start) { editor.text.toString() to 0 }
                if (region != null && editor.setComposingRegion(region)) return
            }
            // 原来的做法：撤掉光标处定格的编码，拼音整段清空。
            val detached = tracker.detach()
            val region = locateDetachedComposition(detached, editor.selection, editor.selection, editor.before, editor.after)
            if (region != null) {
                editor.text.delete(region.start, region.end)
                editor.selection = region.start
            }
            code = ""
            tracker.selectionReported(editor.selection, editor.selection, -1, -1)
        }
    }

    @Test fun oldRestartLosesEveryLetterInAFieldThatResetsItsText() {
        val editor = FakeEditor("", resetsOnChange = true)
        val ime = FakeIme(editor, resume = false)
        ime.type("ni")
        // 每个字母一写进去就被撤掉，拼音也清空：用户看到打了一两个字母然后全没了。
        assertEquals("", editor.text.toString())
        assertEquals("", ime.code)
        assertEquals(2, editor.restarts)
    }

    @Test fun oldRestartLeavesLettersAsTextWhenTheCaretJumpsToTheStart() {
        val editor = FakeEditor("你好", resetsOnChange = true)
        val ime = FakeIme(editor, resume = false)
        ime.type("n")
        // 光标被放回开头，找不到编码：字母留成正文，拼音没了。
        assertEquals("你好n", editor.text.toString())
        assertEquals("", ime.code)
    }

    @Test fun resumedCompositionKeepsTypingInAnEmptyField() {
        val editor = FakeEditor("", resetsOnChange = true)
        val ime = FakeIme(editor, resume = true)
        ime.type("nihao")
        assertEquals("nihao", ime.code)
        assertEquals("nihao", editor.text.toString())
        assertEquals(0 until 5, editor.composing)
        ime.choose("你好")
        assertEquals("你好", editor.text.toString())
        assertNull(editor.composing)
    }

    @Test fun resumedCompositionAfterExistingTextWhenTheCaretJumpsToTheStart() {
        val editor = FakeEditor("留言：", resetsOnChange = true)
        val ime = FakeIme(editor, resume = true)
        ime.type("nihao")
        assertEquals("留言：nihao", editor.text.toString())
        assertEquals(3 until 8, editor.composing)
        assertEquals(5, editor.restarts)
        ime.choose("你好")
        assertEquals("留言：你好", editor.text.toString())
    }

    @Test fun hostCaretKeptAtTheEndAlsoResumes() {
        val editor = FakeEditor("abc", resetsOnChange = true, caretAfterReset = null)
        val ime = FakeIme(editor, resume = true)
        ime.type("hao", reportBeforeRestart = true)
        assertEquals("abchao", editor.text.toString())
        assertEquals(3 until 6, editor.composing)
        assertEquals("hao", ime.code)
    }

    @Test fun aHostThatNeverRestartsIsUnchanged() {
        val editor = FakeEditor("x", resetsOnChange = false)
        val ime = FakeIme(editor, resume = true)
        ime.type("ni")
        assertEquals("xni", editor.text.toString())
        assertEquals(0, editor.restarts)
    }

    @Test fun aHostThatChangedTheTextIsNotResumed() {
        // 宿主重启前把文字换了（发送后清空、改了编码）：编码起点处不是原编码，照原来清空，不碰别的字。
        assertNull(locateResumableComposition("ni", 0, 0, "", "", start = 3) { "留言：" to 0 })
        assertNull(locateResumableComposition("ni", 0, 0, "", "nia", start = 3) { "留言：n" to 0 })
        // 知道起点时只认起点：光标在开头、开头碰巧也是 ni 不算。
        assertNull(locateResumableComposition("ni", 0, 0, "", "nian留言：", start = 6) { "nian留言：" to 0 })
        assertEquals(DetachedComposition(6, 8), locateResumableComposition("ni", 0, 0, "", "nian留言ni", start = 6) { "nian留言ni" to 0 })
        // 读到的只是一段（startOffset）：按偏移换算。
        assertEquals(DetachedComposition(100, 102), locateResumableComposition("ni", 0, 0, "", "", start = 100) { "xxni" to 98 })
        assertNull(locateResumableComposition("ni", 0, 0, "", "", start = 100) { null })
        // 不知道起点：退回光标处核对。
        assertEquals(DetachedComposition(2, 4), locateResumableComposition("ni", 4, 4, "abni", "", start = -1) { error("not read") })
    }

    @Test fun trackerStartFollowsTheHostAndForgetsAfterCommit() {
        val tracker = InlineCompositionTracker()
        tracker.selectionReported(5, 5, -1, -1)
        tracker.shown("n")
        assertEquals(5, tracker.start)
        tracker.selectionReported(6, 6, 5, 6)
        tracker.shown("ni")
        assertEquals(5, tracker.start)
        // 打拼音时宿主报来的光标（没有组合区）不改起点。
        tracker.selectionReported(0, 0, -1, -1)
        assertEquals(5, tracker.start)
        tracker.ended()
        assertEquals(-1, tracker.start)
        // 上屏后宿主还没报光标就开始打：起点未知，不猜。
        tracker.shown("h")
        assertEquals(-1, tracker.start)
        tracker.ended()
        // 有选区时也不猜。
        tracker.selectionReported(2, 4, -1, -1)
        tracker.shown("h")
        assertEquals(-1, tracker.start)
    }
}
