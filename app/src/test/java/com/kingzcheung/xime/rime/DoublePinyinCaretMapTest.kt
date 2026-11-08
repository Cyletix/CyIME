package com.kingzcheung.xime.rime

import com.kingzcheung.xime.settings.DoublePinyinScheme
import org.junit.Assert.*
import org.junit.Test

class DoublePinyinCaretMapTest {
    private val flypy = DoublePinyinScheme.FLYPY

    @Test fun fullPinyinShownInTheInputBoxMapsToTheDoublePinyinKeys() {
        val map = DoublePinyinCaretMap.build("shuang'pin", "ulpb", flypy)!!
        // Tapping after shuang (on either side of the separator) puts the caret after ul.
        assertEquals(0, map.rawOffset(0))
        assertEquals(2, map.rawOffset(6))
        assertEquals(2, map.rawOffset(7))
        assertEquals(4, map.rawOffset(10))
        // After sh: between the initial key and the final key.
        assertEquals(1, map.rawOffset(2))
        assertEquals(1, map.rawOffset(3))
        assertEquals(2, map.rawOffset(5))
        // And back: the visible caret goes right after the syllable it follows.
        assertEquals(0, map.displayOffset(0))
        assertEquals(2, map.displayOffset(1))
        assertEquals(6, map.displayOffset(2))
        assertEquals(8, map.displayOffset(3))
        assertEquals(10, map.displayOffset(4))
    }

    @Test fun keysEchoedAfterTheCaretAndBareInitialsMapOneByOne() {
        val echoed = DoublePinyinCaretMap.build("shuang'pb", "ulpb", flypy)!!
        assertEquals(6, echoed.displayOffset(2))
        assertEquals(8, echoed.displayOffset(3))
        assertEquals(3, echoed.rawOffset(8))
        // ul deleted at the caret: u alone reads sh.
        val bare = DoublePinyinCaretMap.build("sh'pin", "upb", flypy)!!
        assertEquals(2, bare.displayOffset(1))
        assertEquals(1, bare.rawOffset(2))
        assertEquals(3, bare.rawOffset(6))
        assertNotNull(DoublePinyinCaretMap.build("shuang'p", "ulp", flypy))
    }

    @Test fun zeroInitialSyllablesAndTypedSeparatorsKeepTheirKeys() {
        val map = DoublePinyinCaretMap.build("ang'shuang", "ahul", flypy)!!
        assertEquals(2, map.rawOffset(3))
        assertEquals(3, map.displayOffset(2))
        val a = DoublePinyinCaretMap.build("a'ni", "aani", flypy)!!
        assertEquals(2, a.rawOffset(1))
        val typed = DoublePinyinCaretMap.build("shuang'pin", "ul'pb", flypy)!!
        assertEquals(2, typed.rawOffset(6))
        assertEquals(3, typed.rawOffset(7))
        assertEquals(6, typed.displayOffset(2))
        assertEquals(5, typed.rawOffset(10))
    }

    @Test fun otherSchemesUseTheirOwnKeys() {
        val scheme = DoublePinyinScheme.BUILTINS.first { it.id == DoublePinyinScheme.ZIRANMA_ID }
        val keys = scheme.code("shuang")!! + scheme.code("pin")!!
        val map = DoublePinyinCaretMap.build("shuang'pin", keys, scheme)!!
        assertEquals(2, map.rawOffset(6))
        if (keys != "ulpb") assertNull(DoublePinyinCaretMap.build("shuang'pin", "ulpb", scheme))
    }

    @Test fun anUnrelatedDisplayIsNotGuessed() {
        assertNull(DoublePinyinCaretMap.build("ni'hao", "ulpb", flypy))
        assertNull(DoublePinyinCaretMap.build("shuang", "ulpb", flypy))
        assertNull(DoublePinyinCaretMap.build("shuang'pin'ni", "ulpb", flypy))
    }
}
