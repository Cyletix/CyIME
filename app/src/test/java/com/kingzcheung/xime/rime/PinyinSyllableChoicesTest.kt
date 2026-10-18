package com.kingzcheung.xime.rime

import org.junit.Assert.*
import org.junit.Test

class PinyinSyllableChoicesTest {
    @Test fun preservesDistinctReadingsOfOneDoublePinyinCode() {
        val options = parsePinyinSyllableOptions(arrayOf(
            "17\tlo\t0\t2\t0", "18\tluo\t0\t2\t1",
        ), "lo", 0)
        assertEquals(listOf("lo", "luo"), options.map { it.pinyin })
        assertEquals(listOf(17, 18), options.map { it.syllableId })
        assertEquals(1, snapshot(options = options).selectedIndex)
    }

    @Test fun selectionIsBoundToSchemaRevisionRawInputAndConfirmedPrefix() {
        val choice = snapshot(input = "niBUGJ", protectedLength = 2, protectedText = "你")
        assertTrue(choice.matches("double_pinyin_flypy_14jian", 41, "niBUGJ", 2, "你"))
        assertFalse(choice.matches("pinyin_14jian", 41, "niBUGJ", 2, "你"))
        assertFalse(choice.matches(choice.schemaId, 42, "niBUGJ", 2, "你"))
        // Lowercase is an exact letter; replacing an uppercase grouped key is not equivalent.
        assertFalse(choice.matches(choice.schemaId, 41, "nibUGJ", 2, "你"))
        assertFalse(choice.matches(choice.schemaId, 41, "niBUGJ", 0, ""))
        assertFalse(choice.matches(choice.schemaId, 41, "niBUGJ", 2, "尼"))
    }

    @Test fun rejectsAnOptionThatCouldRewriteConfirmedTextOrUseInvalidOffsets() {
        val options = parsePinyinSyllableOptions(arrayOf(
            "1\tni\t0\t2\t0", "2\thao\t2\t5\t0", "3\than\t2\t6\t0",
            "4\the\t-1\t2\t0", "5\then\t2\t2\t0", "6\tha\t2\t4\t2",
            "7\tbad input\t2\t4\t0", "not-a-record",
        ), "nihao", 2)
        assertEquals(listOf(PinyinSyllableOption(2, "hao", 2, 5)), options)
        assertTrue(parsePinyinSyllableOptions(arrayOf("1\tni\t-1\t1\t0"), "ni", -1).isEmpty())
    }

    @Test fun doesNotConfuseUtf8ByteOffsetsWithKotlinCharacterOffsets() {
        assertTrue(parsePinyinSyllableOptions(arrayOf("1\tnv\t0\t2\t0"), "nü", 0).isEmpty())
    }

    private fun snapshot(input: String = "lo", protectedLength: Int = 0,
        protectedText: String = "", options: List<PinyinSyllableOption> = emptyList()) =
        PinyinSyllableChoices(7, "double_pinyin_flypy_14jian", 41,
            input, protectedLength, protectedText, options)
}
