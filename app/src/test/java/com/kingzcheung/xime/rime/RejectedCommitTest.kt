package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RejectedCommitTest {
    @Test fun backspacingTheWholeCommitUnlearnsItOnce() {
        val tracker = RejectedCommit()
        tracker.committed("语言机按", learned = true)
        assertNull(tracker.beforeBackspace("你好语言机按", false))
        assertNull(tracker.beforeBackspace("你好语言机", false))
        assertNull(tracker.beforeBackspace("你好语言", false))
        assertEquals("语言机按", tracker.beforeBackspace("你好语", false))
        // 删过头不再减第二次。
        assertNull(tracker.beforeBackspace("你好", false))
        assertEquals(0, tracker.neededLength())
    }

    @Test fun deletingOnlyTheTailKeepsTheLearning() {
        val tracker = RejectedCommit()
        tracker.committed("通话", learned = true)
        assertNull(tracker.beforeBackspace("通话", false))
        // 删了「话」后又打了别的字：光标前不再是「通」，作废。
        assertNull(tracker.beforeBackspace("通化", false))
        assertNull(tracker.beforeBackspace("通", false))
    }

    @Test fun somethingTypedAfterTheCommitCancelsIt() {
        val tracker = RejectedCommit()
        tracker.committed("测完了吗", learned = true)
        assertNull(tracker.beforeBackspace("测完了吗？", false))
        assertNull(tracker.beforeBackspace("测完了吗", false))
        assertEquals(0, tracker.neededLength())
    }

    @Test fun incognitoAndNonChineseCommitsAreNotTracked() {
        val tracker = RejectedCommit()
        tracker.committed("怪词", learned = false)
        assertEquals(0, tracker.neededLength())
        tracker.committed("，", learned = true)
        assertEquals(0, tracker.neededLength())
        tracker.committed("hello", learned = true)
        assertEquals(0, tracker.neededLength())
    }

    @Test fun selectionOrMissingTextCancels() {
        val tracker = RejectedCommit()
        tracker.committed("房地产", learned = true)
        assertNull(tracker.beforeBackspace("房地产", true))
        assertEquals(0, tracker.neededLength())
        tracker.committed("房地产", learned = true)
        assertNull(tracker.beforeBackspace(null, false))
        assertEquals(0, tracker.neededLength())
    }

    @Test fun clearingTheFieldRightAfterTheCommitUnlearnsIt() {
        val tracker = RejectedCommit()
        tracker.committed("上市公司", learned = true)
        assertEquals("上市公司", tracker.beforeClear("我们上市公司"))
        tracker.committed("上市公司", learned = true)
        assertNull(tracker.beforeClear("上市公司了"))
    }

    @Test fun punctuationCommittedWithTheWordIsTrimmed() {
        val tracker = RejectedCommit()
        tracker.committed("你好，", learned = true)
        assertNull(tracker.beforeBackspace("你好，", false))
        assertNull(tracker.beforeBackspace("你好", false))
        assertEquals("你好", tracker.beforeBackspace("你", false))
    }

    @Test fun supplementaryCharactersCountAsOneBackspace() {
        val tracker = RejectedCommit()
        val rare = String(Character.toChars(0x20000)) + "字"
        tracker.committed(rare, learned = true)
        assertNull(tracker.beforeBackspace(rare, false))
        assertEquals(rare, tracker.beforeBackspace(String(Character.toChars(0x20000)), false))
    }
}
