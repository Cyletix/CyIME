package com.kingzcheung.xime.association

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishLexiconTest {
    private val lexicon by lazy {
        val path = "src/main/assets/spanish/words.txt"
        SpanishLexicon(listOf(File(path), File("app/$path")).first { it.isFile }.readLines().filter { it.isNotBlank() })
    }

    @Test fun `accents and ñ do not have to be typed`() {
        assertEquals("canción", lexicon.complete("cancion", 5).first())
        assertEquals("año", lexicon.complete("ano", 5).first())
        assertEquals("mañana", lexicon.complete("manana", 5).first())
        assertEquals("también", lexicon.complete("tambien", 5).first())
        assertEquals("español", lexicon.complete("espanol", 5).first())
        // 去掉重音后整词相同的排在补全前面：esta → está，que → qué。
        assertEquals("está", lexicon.complete("esta", 5).first())
        assertEquals("qué", lexicon.complete("que", 5).first())
    }

    @Test fun `a typed accent or ñ only matches that letter`() {
        for (typed in listOf("añ", "cé", "pú")) {
            val result = lexicon.complete(typed, 20)
            assertTrue(typed, result.isNotEmpty())
            for (word in result) assertEquals("$typed → $word", typed.last(), word.lowercase()[typed.length - 1])
        }
    }

    @Test fun `completions follow frequency and never repeat the typed word`() {
        val result = lexicon.complete("graci", 3)
        assertEquals(listOf("gracias", "gracioso", "gracia"), result)
        assertFalse("tambien" in lexicon.complete("tambien", 20))
        assertTrue(lexicon.complete("", 5).isEmpty())
        assertTrue(lexicon.complete("zzzz", 5).isEmpty())
    }

    @Test fun `case follows what was typed and proper nouns stay capitalized`() {
        assertEquals(listOf("Canción", "Canciones"), lexicon.complete("Cancion", 5))
        assertEquals(listOf("CANCIÓN", "CANCIONES"), lexicon.complete("CANCION", 5))
        assertEquals("Madrid", lexicon.complete("madrid", 5).first())
    }

    @Test fun `exact accent variants come before longer words, each group by rank`() {
        val small = SpanishLexicon(listOf("de", "dé", "del", "decir"))
        assertEquals(listOf("dé", "del", "decir"), small.complete("de", 5))
        // 打了 é 就只认 é：dé 本身已在候选栏第一个，没有别的词。
        assertEquals(emptyList<String>(), small.complete("dé", 5))
    }
}
