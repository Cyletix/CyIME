package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HanziPinyinTest {
    private val table by lazy {
        listOf("src/main/assets/handwriting/hanzi_pinyin.tsv", "app/src/main/assets/handwriting/hanzi_pinyin.tsv")
            .map(::File).first { it.isFile }.useLines(Charsets.UTF_8) { HanziPinyin.parse(it) }
    }

    private fun reading(text: String) = HanziPinyin.reading(table, text)

    @Test fun singleCharacterShowsTonedReading() {
        assertEquals("dí", reading("嫡"))
        assertEquals("nǚ", reading("女"))
        assertEquals("lǜ", reading("绿"))
        assertEquals("yín", reading("銀"))
    }

    @Test fun polyphonicCharacterListsCommonReadingsMostFrequentFirst() {
        assertEquals("xíng/háng", reading("行"))
        assertEquals("zhǎng/cháng", reading("长"))
        assertEquals("de/dì/dí", reading("的"))
        assertEquals("le/liǎo/liào", reading("了"))
        assertEquals("de/dé/děi", reading("得"))
        // 首位的轻声照列（吗、们主要读轻声）；词里的弱读（和 he、huo）和儿化的 r 不算读音
        assertEquals("ma/má", reading("吗"))
        assertEquals("men", reading("们"))
        assertEquals("hé/hè", reading("和"))
        assertEquals("ér", reading("儿"))
        assertTrue(table.readings.all { it.isNotEmpty() && it.split('/').size <= 3 })
    }

    @Test fun phraseShowsReadingsOnlyWhenEveryCharacterIsUnambiguous() {
        assertEquals("nǚ ér", reading("女儿"))
        // 银行 的 行 读哪个查不准：不显示，免得给出错读音
        assertEquals("", reading("银行"))
    }

    @Test fun charactersOutsideTheTableShowNothing() {
        assertEquals("", reading("a"))
        assertEquals("", reading("か"))
        assertEquals("", reading(""))
        assertEquals("fǔ", reading("𫖯"))
    }

    @Test fun everyHandwritingFrequencyCharacterHasAReading() {
        val frequency = listOf("src/main/assets/handwriting/hanzi_frequency.tsv", "app/src/main/assets/handwriting/hanzi_frequency.tsv")
            .map(::File).first { it.isFile }.readLines(Charsets.UTF_8).mapNotNull { it.substringBefore('\t').takeIf(String::isNotEmpty) }
        val missing = frequency.filter { reading(it).isEmpty() }
        assertEquals(emptyList<String>(), missing)
    }
}
