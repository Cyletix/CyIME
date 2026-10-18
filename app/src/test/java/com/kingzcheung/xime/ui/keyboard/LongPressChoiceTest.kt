package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressChoiceTest {
    private fun route(label: String): Pair<String, String> {
        var path = ""
        var value = ""
        commitLongPressChoice(label, { path = "commit"; value = it }) { path = "key"; value = it }
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
}
