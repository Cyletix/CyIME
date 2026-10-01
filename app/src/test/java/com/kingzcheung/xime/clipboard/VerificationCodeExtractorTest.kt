package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class VerificationCodeExtractorTest {
    @Test fun recognizesKeywordsBeforeAndAfterAllSupportedLengths() {
        for (code in listOf("0123", "012345", "0123456", "01234567")) {
            for (word in listOf("验证码", "确认码", "認証コード", "確認コード", "認証番号", "OTP", "verification code", "ワンタイムパスワード")) {
                assertEquals("$word $code", code, VerificationCodeExtractor.extract("$word: $code"))
                assertEquals("$code $word", code, VerificationCodeExtractor.extract("$code はあなたの $word"))
            }
        }
    }
    @Test fun acceptsFullWidthDigitsAndLeadingZero() {
        assertEquals("012345", VerificationCodeExtractor.extract("確認コードは０１２３４５です。"))
        assertEquals("012345", VerificationCodeExtractor.extract("確認コード（6桁）は012345です。"))
    }
    @Test fun ignoresUnrelatedNumbersAndRequiresKeyword() {
        assertNull(VerificationCodeExtractor.extract("订单123456，金额8888，电话13812345678"))
        assertEquals("123456", VerificationCodeExtractor.extract("客服4001234567，验证码123456，5分钟内有效。"))
        assertNull(VerificationCodeExtractor.extract("验证码123456789"))
        assertNull(VerificationCodeExtractor.extract("验证码A123456B"))
        assertNull(VerificationCodeExtractor.extract("验证码。订单123456"))
        assertNull(VerificationCodeExtractor.extract("123456"))
        assertEquals("123456", VerificationCodeExtractor.extract("認証コード\n123456"))
        assertNull(VerificationCodeExtractor.extract("验证码有效期2026年10月1日"))
        assertNull(VerificationCodeExtractor.extract("确认码请咨询电话03-1234-5678"))
    }
    @Test fun doesNotGuessBetweenEquallyPlausibleCodes() {
        assertNull(VerificationCodeExtractor.extract("验证码:1234，确认码:5678"))
    }
}
