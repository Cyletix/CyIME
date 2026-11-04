package com.kingzcheung.xime.contacts

import com.kingzcheung.xime.service.CandidateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCandidatesTest {
    private val zhang = ContactIndex.Entry("张三", listOf("13800138000"))
    private fun rows(vararg texts: String) = CandidateRows(texts.toList(), texts.map { "" }, emptyList())

    @Test fun nameAndNumberGoRightAfterTheFirstCandidate() {
        val out = ContactCandidates.merge(rows("章三", "张", "长"), ContactCandidates.items(listOf(zhang), true, true))
        assertEquals(listOf("章三", "张三", "13800138000", "张", "长"), out.texts)
        assertEquals(listOf("", "", "张三", "", ""), out.comments)
        assertEquals(CandidateAction.engine(0), out.actions[0])
        assertTrue(out.actions[1].isPrivate && out.actions[1].commitText == "张三")
        assertTrue(out.actions[2].isPrivate && out.actions[2].commitText == "13800138000")
        // 原来的引擎候选保留各自的引擎位置，点了照常学习。
        assertEquals(CandidateAction.engine(1), out.actions[3])
        assertEquals(listOf(0, -1, -1, 1, 2), out.origin)
    }

    @Test fun nameTheEngineAlreadyOffersStaysInPlaceButNoLongerLearns() {
        val out = ContactCandidates.merge(rows("张三", "章三"), ContactCandidates.items(listOf(zhang), true, true))
        assertEquals(listOf("张三", "13800138000", "章三"), out.texts)
        assertEquals(CandidateAction.contact("张三"), out.actions[0])
        assertFalse(out.actions[2].isPrivate)
    }

    @Test fun switchesDecideWhatIsShown() {
        assertEquals(listOf("张三"), ContactCandidates.items(listOf(zhang), true, false).map { it.text })
        assertEquals(listOf("13800138000"), ContactCandidates.items(listOf(zhang), false, true).map { it.text })
        assertTrue(ContactCandidates.items(listOf(zhang), false, false).isEmpty())
    }

    @Test fun emptyBarAndNoItems() {
        val out = ContactCandidates.merge(rows(), ContactCandidates.items(listOf(zhang), true, true))
        assertEquals(listOf("张三", "13800138000"), out.texts)
        val same = rows("你好")
        assertSame(same, ContactCandidates.merge(same, emptyList()))
    }

    @Test fun pluginActionsAreKept() {
        val base = CandidateRows(listOf("甲", "乙"), listOf("", "插件"), listOf(CandidateAction.engine(0), CandidateAction.plugin("乙")))
        val out = ContactCandidates.merge(base, ContactCandidates.items(listOf(zhang), true, false))
        assertEquals(listOf(CandidateAction.engine(0), CandidateAction.contact("张三"), CandidateAction.plugin("乙")), out.actions)
        assertEquals(listOf("", "", "插件"), out.comments)
    }
}
