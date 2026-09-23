package com.kingzcheung.xime.ui.keyboard

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class JapaneseKanaInputTest {
    @Test fun `all base and modified kana codes exist in the bundled dictionary`() {
        val path = "src/main/assets/rime_japanese/jaroomaji.kana_kigou.dict.yaml"
        val file = listOf(File(path), File("app/$path")).first { it.isFile }
        val codes = file.readLines().mapNotNull { it.split('\t').getOrNull(1) }.toSet()
        val base = japaneseKanaRomaji.filter { it == "-" || it.all { char -> char in 'a'..'z' } }
        (base + kanaModifierCycles.flatten()).forEach { assertTrue("Missing dictionary code: $it", it in codes) }
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
