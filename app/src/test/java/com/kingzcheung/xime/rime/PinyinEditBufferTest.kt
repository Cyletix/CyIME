package com.kingzcheung.xime.rime

import org.junit.Assert.*
import org.junit.Test

class PinyinEditBufferTest {
    @Test fun limitsEditingToPhoneticChineseModes() {
        listOf("rime_ice", "t9_pinyin", "pinyin_14jian", "double_pinyin_flypy").forEach { assertTrue(PinyinEditBuffer.supports(it)) }
        listOf("japanese", "japanese_kana", "wubi86", "english").forEach { assertFalse(PinyinEditBuffer.supports(it)) }
    }
    @Test fun keepsRealBoundariesAndUmlautAsV() {
        assertEquals("nv'er", PinyinEditBuffer.normalized("Nü er"))
        assertEquals("ni'hao", PinyinEditBuffer.normalized("ni hao"))
    }
}
