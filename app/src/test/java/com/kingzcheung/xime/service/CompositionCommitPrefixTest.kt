package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** 打拼音时按符号、切语言：字母原样上屏，不上屏首选、不加空格（Gboard 规则，用户 2026-10-24）。 */
class CompositionCommitPrefixTest {
    private fun prefix(asTyped: Boolean, input: String, preedit: String = input, t9: Boolean = false,
        merged14: Boolean = false, partial: String = "", englishDelivered: Boolean = false,
        japanesePreview: String? = null, first: String = "首选") =
        compositionCommitPrefix(asTyped, input, preedit, t9, merged14, partial, englishDelivered, japanesePreview) { first }

    @Test fun symbolKeyKeepsTypedLettersInEitherPunctuationWidth() {
        // 中文屏幕键盘的符号键印什么上屏什么；实体键盘按全角/半角换算。
        assertEquals("why？", prefix(true, "why", "why", first = "为何") +
            keyboardLiteralWidth("？", full = true, japanese = false, chineseKeyboard = true))
        assertEquals("why?", prefix(true, "why", "why", first = "为何") +
            keyboardLiteralWidth("?", full = false, japanese = false, chineseKeyboard = true))
        assertEquals("why，", prefix(true, "why", first = "为何") + keyboardLiteralWidth(",", full = true, japanese = false))
        assertEquals("why,", prefix(true, "why", first = "为何") + keyboardLiteralWidth(",", full = false, japanese = false))
    }

    @Test fun swipeDigitAndLanguageSwitchCommitLettersWithoutSpace() {
        assertEquals("nihao2", prefix(true, "nihao", "ni hao", first = "你好") + "2")
        assertEquals("nihao", prefix(true, "nihao", "ni hao", first = "你好"))
        // 雾凇大写参与组字：原样保留大小写。
        assertEquals("zuogeDNA", prefix(true, "zuogeDNA", "zuo ge DNA", first = "做个DNA"))
    }

    @Test fun t9UsesTheDecodedReadingLikeEnterKeepingSelectedSegments() {
        assertEquals("nihao。", prefix(true, "64426", "ni hao", t9 = true, first = "你好") + "。")
        assertEquals("你好ceshi", prefix(true, "23744", "你好 ce shi", t9 = true, partial = "你好", first = "测试"))
        // Enter 用的是同一段文字。
        assertEquals(rawCompositionCommitText("23744", "你好 ce shi", true),
            prefix(true, "23744", "你好 ce shi", t9 = true, partial = "你好"))
    }

    @Test fun fourteenKeyLowercasesMergedInputLikeEnter() {
        assertEquals("nihao", prefix(true, "NIHAO", "ni hao", merged14 = true))
    }

    @Test fun voiceAndHandwritingStillCommitTheFirstCandidate() {
        assertEquals("为何", prefix(false, "why", first = "为何"))
        assertEquals("你好测试", prefix(false, "ceshi", partial = "你好", t9 = true, first = "测试"))
    }

    @Test fun japaneseKanaAndDeliveredEnglishKeepTheirOwnRules() {
        assertEquals("かな", prefix(true, "kana", japanesePreview = "かな"))
        assertEquals("", prefix(true, "", englishDelivered = true))
        assertEquals("你好", prefix(true, "", partial = "你好", t9 = true))
    }
}
