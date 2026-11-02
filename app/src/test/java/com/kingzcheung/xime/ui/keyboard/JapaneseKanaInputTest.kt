package com.kingzcheung.xime.ui.keyboard

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class JapaneseKanaInputTest {
    @Test fun `every kana key code converts to its label in azooKey`() {
        val path = "src/test/resources/azookey_readings.tsv"
        val file = listOf(File(path), File("app/$path")).first { it.isFile }
        val readings = file.readLines().filterNot { it.startsWith("#") }.map { it.split('\t') }.associate { it[0] to it[1] }
        japaneseKanaKeys.flatMap { it.choices }.filterNotNull()
            .filter { it.romaji == "-" || it.romaji.all { char -> char in 'a'..'z' } }
            .forEach { assertEquals("Key ${it.label}", it.label, readings[it.romaji]) }
        kanaModifierCycles.flatten().forEach { code ->
            val reading = readings[code]
            assertTrue("Unconverted code: $code", reading != null && reading.length == 1 && reading.none { it in 'a'..'z' })
        }
    }

    @Test fun `flick direction follows japanese phone vowel order`() {
        val key = japaneseKanaKeys.first()
        assertEquals("a", key.choice(kanaFlickDirection(0f, 0f, 18f))!!.romaji)
        assertEquals("i", key.choice(kanaFlickDirection(-30f, 0f, 18f))!!.romaji)
        assertEquals("u", key.choice(kanaFlickDirection(0f, -30f, 18f))!!.romaji)
        assertEquals("e", key.choice(kanaFlickDirection(30f, 0f, 18f))!!.romaji)
        assertEquals("o", key.choice(kanaFlickDirection(0f, 30f, 18f))!!.romaji)
        assertEquals(KanaFlickDirection.TAP, kanaFlickDirection(8f, -6f, 18f))
    }

    @Test fun `wa key offers wo syllabic n and long vowel without an accidental empty flick`() {
        val key = japaneseKanaKeys.first { it.center.romaji == "wa" }
        assertEquals(listOf("wa", "wo", "nn", "-", null), key.choices.map { it?.romaji })
        assertNull(key.choice(KanaFlickDirection.DOWN))
    }

    @Test fun `modifier cycles never contain ambiguous duplicate encodings`() {
        val codes = kanaModifierCycles.flatten()
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(kanaModifierCycles.all { it.size >= 2 })
    }
}
