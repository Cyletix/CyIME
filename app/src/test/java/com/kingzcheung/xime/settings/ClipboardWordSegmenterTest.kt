package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardWordSegmenterTest {
    @Test fun installedDictionaryCommentsDoNotHideImportTables() {
        assertEquals(listOf("cn_dicts/8105", "cn_dicts/base", "cn_dicts/ext"), ClipboardWordSegmenter.imports("""
            import_tables:
              - cn_dicts/8105 # 字表
              # - cn_dicts/41448
              - cn_dicts/base # 常用词

              - cn_dicts/ext
            ...
        """.trimIndent()))
    }
    @Test fun rareSentenceEntryDoesNotSwallowCommonWords() {
        val words = mapOf("今天" to 501272.0, "天气" to 492855.0, "不错" to 500601.0,
            "今天天气" to 10240.0, "今天天气不错" to 100.0)
        assertEquals(listOf("今天", "天气", "不错"), ClipboardWordSegmenter.segment("今天天气不错", words, 1e11))
    }
    @Test fun dictionaryWordsBeatIndividualCharacters() {
        val words = mapOf("今天" to 1000.0, "天气" to 900.0, "不错" to 500.0)
        assertEquals(listOf("今天", "天气", "不错"), ClipboardWordSegmenter.segment("今天天气不错", words, 100000.0))
    }
    @Test fun frequencyResolvesCompetingWordBoundaries() {
        val words = mapOf("研究" to 10000.0, "生命" to 9000.0, "起源" to 5000.0, "研究生" to 100.0)
        assertEquals(listOf("研究", "生命", "起源"), ClipboardWordSegmenter.segment("研究生命起源", words, 100000.0))
    }
    @Test fun spacesNewlinesPunctuationAndEmojiAreNotLost() {
        val text = "你好 world 123\n😀𠀀！"
        val tokens = ClipboardWordSegmenter.segment(text, mapOf("你好" to 10.0), 100.0)
        assertEquals(text, tokens.joinToString(""))
        assertEquals(listOf("你好", " ", "world", " ", "123", "\n", "😀", "𠀀", "！"), tokens)
    }
}
