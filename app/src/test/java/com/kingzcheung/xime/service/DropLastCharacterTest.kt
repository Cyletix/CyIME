package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** 审计 F10：退格后联想上下文与正文同步删掉一个完整的字，不留半个代理项。 */
class DropLastCharacterTest {
    @Test fun dropsWholeSurrogatePairs() {
        assertEquals("a", "a😀".dropLastCharacter()) // a😀
        assertEquals("吉", "吉𠮷".dropLastCharacter()) // 吉𠮷
    }

    @Test fun dropsCombiningMarksWithTheirLetter() {
        assertEquals("caf", "café".dropLastCharacter())
    }

    @Test fun dropsAJoinedEmojiAsOne() {
        // 👨‍👩‍👧：三个 emoji 用 ZWJ 连成一个
        assertEquals("你", "你👨‍👩‍👧".dropLastCharacter())
    }

    @Test fun ordinaryTextLosesOneCharacter() {
        assertEquals("你", "你好".dropLastCharacter())
        assertEquals("", "好".dropLastCharacter())
        assertEquals("", "".dropLastCharacter())
    }
}
