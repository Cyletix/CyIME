package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressChoiceTest {
    private fun route(label: String, accentLetters: Boolean = false): Pair<String, String> {
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
    fun spanishAccentsJoinTheWordBeingTyped() {
        // 西班牙语长按 a 选 á：接进正在打的词（cancio + ó + n），补全才认得出 canción。
        assertEquals("key" to "á", route("á", accentLetters = true))
        assertEquals("key" to "ñ", route("ñ", accentLetters = true))
        assertEquals("commit" to "¿", route("¿", accentLetters = true))
        assertEquals("commit" to "Á", route("Á", accentLetters = true))
        // 其他键盘照旧直接上屏。
        assertEquals("commit" to "á", route("á"))
    }
}
