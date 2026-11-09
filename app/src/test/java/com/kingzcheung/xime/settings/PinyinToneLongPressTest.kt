package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 中文 26 键长按字母的气泡（角标选「滑动＋长按气泡」时）：要有拼音四声（用户 11-08：长按字母要能出带调拼音），
 * 原来的大写和法德等变音字母一个不少、顺序不变，只是排在四声后面。
 */
class PinyinToneLongPressTest {
    private fun repoFile(rel: String): File {
        for (base in listOf(File("."), File("app"), File(".."))) {
            val f = File(base, rel)
            if (f.exists()) return f
        }
        error("file not found: $rel")
    }

    /** 中文 26 键（keyboard.qwerty）里某个字母的长按列表。 */
    private fun qwertyLongPress(key: String): List<String> {
        val text = repoFile("src/main/assets/xime.yaml").readText()
        val section = text.substringAfter("\n  qwerty:\n").substringBefore("\n  qwerty_en:\n")
        val line = section.lines().first { it.trimStart().startsWith("$key: { tap: \"$key\"") }
        val values = line.substringAfter("long_press:").substringAfter("values: [").substringBefore("]")
        return Regex("\"([^\"]*)\"").findAll(values).map { it.groupValues[1] }.toList()
    }

    @Test fun vowelsOfferPinyinTonesBeforeTheOldAccents() {
        val expected = mapOf(
            "a" to listOf("a", "A", "ā", "á", "ǎ", "à", "â", "ã", "ä", "å", "æ"),
            "e" to listOf("e", "E", "ē", "é", "ě", "è", "ê", "ë"),
            "i" to listOf("i", "I", "ī", "í", "ǐ", "ì", "î", "ï"),
            "o" to listOf("o", "O", "ō", "ó", "ǒ", "ò", "ô", "õ", "ö", "ø"),
            "u" to listOf("u", "U", "ū", "ú", "ǔ", "ù", "û", "ü"),
            "v" to listOf("v", "V", "ü", "ǖ", "ǘ", "ǚ", "ǜ"),
        )
        for ((key, list) in expected) assertEquals(key, list, qwertyLongPress(key))
    }

    @Test fun oldEntriesAreAllKept() {
        // 1.15.71 之前的列表：每一项都还在；四声那一组按 1～4 声排，其余旧项前后顺序不变。
        val tones = setOf("ā", "á", "ǎ", "à", "ē", "é", "ě", "è", "ī", "í", "ǐ", "ì", "ō", "ó", "ǒ", "ò", "ū", "ú", "ǔ", "ù", "ǖ", "ǘ", "ǚ", "ǜ")
        val before = mapOf(
            "a" to listOf("a", "A", "à", "á", "â", "ã", "ä", "å", "æ"),
            "e" to listOf("e", "E", "è", "é", "ê", "ë"),
            "i" to listOf("i", "I", "ì", "í", "î", "ï"),
            "o" to listOf("o", "O", "ò", "ó", "ô", "õ", "ö", "ø"),
            "u" to listOf("u", "U", "ù", "ú", "û", "ü"),
            "v" to listOf("v", "V"),
            "y" to listOf("y", "Y", "ÿ"),
            "c" to listOf("c", "C", "ç"),
            "n" to listOf("n", "N", "ñ"),
            "s" to listOf("s", "S", "ß"),
        )
        for ((key, old) in before) {
            val now = qwertyLongPress(key)
            assertTrue("$key 少了旧项：$old → $now", now.containsAll(old))
            assertEquals("$key 旧项顺序变了", old.filter { it !in tones }, now.filter { it in old && it !in tones })
        }
    }
}
