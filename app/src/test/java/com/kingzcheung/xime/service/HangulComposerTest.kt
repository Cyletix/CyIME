package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 两벌식组字：按一串字母，看交出去的文字加上正在拼的音节是不是韩文里该有的样子。 */
class HangulComposerTest {
    private fun type(letters: String, composer: HangulComposer = HangulComposer()): String =
        letters.fold("") { text, letter -> text + composer.input(letter) } + composer.composing

    @Test fun `letters join into syllables`() {
        assertEquals("한", type("ㅎㅏㄴ"))
        assertEquals("한국", type("ㅎㅏㄴㄱㅜㄱ"))
        assertEquals("안녕하세요", type("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("감사합니다", type("ㄱㅏㅁㅅㅏㅎㅏㅂㄴㅣㄷㅏ"))
    }

    @Test fun `a vowel after a final takes that consonant into the next syllable`() {
        assertEquals("가바", type("ㄱㅏㅂㅏ"))
        // 两个字母的收音只带走后一个：닭 + ㅏ → 달가。
        assertEquals("달가", type("ㄷㅏㄹㄱㅏ"))
        assertEquals("닭", type("ㄷㅏㄹㄱ"))
        assertEquals("없어", type("ㅇㅓㅂㅅㅇㅓ"))
    }

    @Test fun `compound vowels and finals`() {
        assertEquals("과", type("ㄱㅗㅏ"))
        assertEquals("원", type("ㅇㅜㅓㄴ"))
        assertEquals("의", type("ㅇㅡㅣ"))
        assertEquals("값", type("ㄱㅏㅂㅅ"))
    }

    @Test fun `letters that cannot join stay as they are`() {
        assertEquals("ㄱㄱ", type("ㄱㄱ"))
        assertEquals("ㅏㅏ", type("ㅏㅏ"))
        // ㄸ 不能做收音：另起一个音节。
        assertEquals("가ㄸ", type("ㄱㅏㄸ"))
        assertEquals("가따", type("ㄱㅏㄸㅏ"))
    }

    @Test fun `backspace removes one letter of the syllable being typed`() {
        val composer = HangulComposer()
        type("ㄱㅏㄱ", composer)
        assertEquals("각", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("가", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("ㄱ", composer.composing)
        assertTrue(composer.backspace())
        assertEquals("", composer.composing)
        assertFalse(composer.backspace())
        // 退回去以后接着打，从剩下的字母接着拼。
        type("ㄱㅗ", composer)
        assertTrue(composer.backspace())
        type("ㅗㅏ", composer)
        assertEquals("과", composer.composing)
    }

    @Test fun `shift gives double consonants and ㅒ ㅖ`() {
        assertEquals('ㄲ', HangulComposer.SHIFTED['ㄱ'])
        assertEquals('ㅖ', HangulComposer.SHIFTED['ㅔ'])
        assertEquals("깨", type("ㄲㅐ"))
        assertTrue(HangulComposer.isJamo('ㅃ'))
        assertFalse(HangulComposer.isJamo('a'))
        assertFalse(HangulComposer.isJamo('ㅘ'))
    }
}
