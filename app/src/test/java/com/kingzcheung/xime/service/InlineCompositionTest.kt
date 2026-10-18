package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 宿主重启输入后被定格的拼音编码：只在光标处文字正好拼回原编码时找回，其他情况一律不碰。 */
class InlineCompositionTest {
    @Test fun caretAtTheEndOfTheFrozenPinyin() {
        // 截图一：“解别人的完整观点,duan xiang qu”，光标在末尾。
        val prefix = "解别人的完整观点,"
        val text = "duan xiang qu"
        val region = locateDetachedComposition(text, prefix.length + text.length, prefix.length + text.length,
            before = ",duan xiang qu", after = "")
        assertEquals(DetachedComposition(prefix.length, prefix.length + text.length), region)
    }

    @Test fun hostRestoredTheCaretOneCharacterEarly() {
        // 截图二：“我估计dou mei la”，宿主把光标放回 l 和 a 之间。
        val prefix = "我估计"
        val text = "dou mei la"
        val caret = prefix.length + text.length - 1
        val region = locateDetachedComposition(text, caret, caret, before = "计dou mei l", after = "a")
        assertEquals(DetachedComposition(prefix.length, prefix.length + text.length), region)
    }

    @Test fun anythingThatDoesNotSpellTheCompositionIsLeftAlone() {
        // 宿主改了文字。
        assertNull(locateDetachedComposition("duan xiang qu", 13, 13, "duan xiang q", ""))
        // 有选区：绝不删除用户选中的文字。
        assertNull(locateDetachedComposition("dou", 3, 1, "dou", ""))
        // 读不到周围文字或没有记录。
        assertNull(locateDetachedComposition("dou", 3, 3, null, ""))
        assertNull(locateDetachedComposition(null, 3, 3, "dou", ""))
        // 光标位置与文字不符（会越过开头）。
        assertNull(locateDetachedComposition("dou", 1, 1, "dou", ""))
    }

    @Test fun ambiguousSplitsAreLeftAlone() {
        // “aa”前后各有一个 a 时有两种拼法之外的歧义：只接受唯一拼法。
        assertEquals(DetachedComposition(4, 6), locateDetachedComposition("aa", 5, 5, "a", "a"))
        assertNull(locateDetachedComposition("a a", 5, 5, "a a", "a a"))
    }

    @Test fun trackerOnlyRemembersPinyinItWroteAndForgetsOnDetach() {
        val tracker = InlineCompositionTracker()
        assertNull(tracker.detach())
        tracker.shown("ni hao")
        assertTrue(tracker.active)
        assertEquals("ni hao", tracker.detach())
        assertFalse(tracker.active)
        assertNull(tracker.detach())
        // 语音临时文本只标记存在，不参与核对删除。
        tracker.shown(null)
        assertTrue(tracker.active)
        assertNull(tracker.detach())
        assertFalse(tracker.active)
    }
}
