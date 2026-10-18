package com.kingzcheung.xime.speech

import com.kingzcheung.xime.speech.caption.chooseFinal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseLockTest {
    @Test fun `other scripts are detected, Chinese with English and digits is not`() {
        assertTrue(hasForeignScript("ありがとう"))
        assertTrue(hasForeignScript("今日はいい天気"))
        assertTrue(hasForeignScript("コーヒー"))
        assertTrue(hasForeignScript("안녕하세요"))
        assertTrue(hasForeignScript("สวัสดี"))
        assertTrue(hasForeignScript("привет"))
        assertFalse(hasForeignScript("今天天气很好，我们去医院。"))
        assertFalse(hasForeignScript("OK，明天见"))
        assertFalse(hasForeignScript("下午3点半（大概）"))
        assertFalse(hasForeignScript("ＷＩＦＩ密码是１２３"))
    }

    @Test fun `a Japanese second pass never replaces Mandarin text when locked`() {
        assertEquals("我想吃饭", keepChinese("我想吃饭", "私はご飯を食べたい", locked = true))
        assertEquals("", keepChinese("", "そうですね", locked = true))
        assertEquals("我想吃饭了。", keepChinese("我想吃饭", "我想吃饭了。", locked = true))
        // Japanese voice language: kana is the expected answer.
        assertEquals("そうですね", keepChinese("", "そうですね", locked = false))
    }

    @Test fun `captions keep the preview over a kana correction`() {
        assertEquals("你截个图发给我", chooseFinal("你截个图发给我", "スクショ送って"))
        assertEquals("你好", chooseFinal("你好", "안녕"))
    }
}
