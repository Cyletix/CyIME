package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class ChineseGlideSyllablesTest {
    @Test fun doublePinyinUsesTwoKeysPerCharacter() {
        assertEquals(listOf("hv", "jk"), chineseGlideSyllables("hvjk", true, emptyList(), emptyList()))
        assertNull(chineseGlideSyllables("hvj", true, emptyList(), emptyList()))
    }

    @Test fun fullPinyinUsesCandidateReadingToResolveAmbiguity() {
        val entries = listOf(listOf("xian"), listOf("xi", "an"), listOf("hui", "jia"))
        assertEquals(listOf("hui", "jia"), chineseGlideSyllables("huijia", false, emptyList(), entries))
        assertEquals(listOf("xi", "an"), chineseGlideSyllables("xian", false, listOf("xī ān"), entries))
        assertEquals(listOf("xian"), chineseGlideSyllables("xian", false, listOf("xiān"), entries))
        assertNull(chineseGlideSyllables("xian", false, emptyList(), entries))
        assertEquals(listOf("shi", "jie"), chineseGlideSyllables("shijie", false, listOf("shi jie"), entries))
        assertNull(chineseGlideSyllables("nihao", false, listOf("nihao"), entries))
        assertEquals(listOf("xi", "an"), chineseGlideSyllables("xian", false, listOf("xi an"), listOf(listOf("xian"))))
        assertNull(chineseGlideSyllables("xian", false, listOf("", "xian"), entries))
        assertEquals(listOf("lve"), chineseGlideSyllables("lve", false, listOf("lǜe"), listOf(listOf("lve"), listOf("lv", "e"))))
    }

    @Test fun typedSeparatorsStayWithTheFollowingCharacter() {
        val entries = listOf(listOf("ni", "hao"))
        assertEquals(listOf("ni", "'hao"), chineseCodeSyllables("ni'hao", false, emptyList(), entries))
        assertEquals(listOf("hv", "'jk"), chineseCodeSyllables("hv'jk", true, emptyList(), emptyList()))
        assertNull(chineseCodeSyllables("hv'j", true, emptyList(), emptyList()))
    }
}
