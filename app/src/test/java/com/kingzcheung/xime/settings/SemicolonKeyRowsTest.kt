package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 微软双拼（ing 在分号上）：26 键第二排补一个分号键；已有分号或排不下时保持原样。 */
class SemicolonKeyRowsTest {
    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { row -> row.map(Char::toString) }

    @Test fun qwertyGetsASemicolonAfterL() {
        val rows = KeysConfigHelper.withSemicolonKey(qwerty)
        assertEquals(qwerty[1] + ";", rows[1])
        assertEquals(listOf(qwerty[0], qwerty[2]), listOf(rows[0], rows[2]))
        assertEquals(rows, KeysConfigHelper.withSemicolonKey(rows))
    }

    @Test fun fullOrUnusualRowsStayAsTheyAre() {
        val colemak = listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm").map { row -> row.map(Char::toString) }
        assertEquals(colemak, KeysConfigHelper.withSemicolonKey(colemak))
        val honeycomb = listOf("qwerty", "uiopasd", "fghjkl", "zxcvbnm").map { row -> row.map(Char::toString) }
        assertEquals(honeycomb, KeysConfigHelper.withSemicolonKey(honeycomb))
    }
}
