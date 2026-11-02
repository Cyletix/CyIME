package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressChoiceTest {
    private fun route(label: String, accentLetters: String = ""): Pair<String, String> {
        var path = ""
        var value = ""
        commitLongPressChoice(label, { path = "commit"; value = it }, { path = "key"; value = it }, accentLetters)
        return path to value
    }

    @Test
    fun lowercaseLetterJoinsPinyinLikeATap() {
        // h + 长按 g 选 g：应接到拼音里，不能先上屏首选「和」再上屏 g。
        assertEquals("key" to "g", route("g"))
    }

    @Test
    fun otherChoicesStillCommitDirectly() {
        assertEquals("commit" to "G", route("G"))
        assertEquals("commit" to "è", route("è"))
        assertEquals("commit" to "3", route("3"))
    }

    @Test
    fun accentLettersJoinTheWordBeingTyped() {
        // 西班牙语长按 a 选 á：接进正在打的词（cancio + ó + n），补全才认得出 canción。
        val spanish = com.kingzcheung.xime.settings.LetterLanguage.SPANISH.extraLetters
        assertEquals("key" to "á", route("á", spanish))
        assertEquals("key" to "ñ", route("ñ", spanish))
        assertEquals("commit" to "¿", route("¿", spanish))
        assertEquals("commit" to "Á", route("Á", spanish))
        // 德语 ß、俄语 ё 同样接进词里；韩语长按的 ㄲ 交给组字。
        assertEquals("key" to "ß", route("ß", com.kingzcheung.xime.settings.LetterLanguage.GERMAN.extraLetters))
        assertEquals("key" to "ё", route("ё", com.kingzcheung.xime.settings.LetterLanguage.RUSSIAN.extraLetters))
        assertEquals("key" to "ㄲ", route("ㄲ", com.kingzcheung.xime.settings.LetterLanguage.KOREAN.extraLetters))
        // 别的语言的字母不算：西班牙语键盘上的 ß 照旧直接上屏。
        assertEquals("commit" to "ß", route("ß", spanish))
        // 其他键盘照旧直接上屏。
        assertEquals("commit" to "á", route("á"))
    }
}
