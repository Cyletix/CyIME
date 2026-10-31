package com.kingzcheung.xime.clipboard

import com.kingzcheung.xime.clipboard.ClipboardManager.Companion.SMS_CODE_LIFETIME_MS
import com.kingzcheung.xime.clipboard.ClipboardManager.Companion.SMS_CODE_SUGGESTION_MS
import org.junit.Assert.*
import org.junit.Test

class SmsCodeSuggestionTest {
    @Test fun pillOnlyShowsShortlyAfterArrivalWhileClipboardKeepsCodeLonger() {
        val arrived = 1_000_000L
        assertTrue(ClipboardManager.smsCodeSuggested(arrived, arrived))
        assertTrue(ClipboardManager.smsCodeSuggested(arrived, arrived + SMS_CODE_SUGGESTION_MS - 1))
        assertFalse(ClipboardManager.smsCodeSuggested(arrived, arrived + SMS_CODE_SUGGESTION_MS))
        assertFalse(ClipboardManager.smsCodeSuggested(arrived, arrived + 5 * 60_000L))
        assertTrue(SMS_CODE_SUGGESTION_MS < SMS_CODE_LIFETIME_MS)
    }

    @Test fun codeBeforeCursorCountsAsEntered() {
        assertTrue(ClipboardManager.smsCodeEntered("012345", "012345"))
        assertTrue(ClipboardManager.smsCodeEntered("012345", "验证码012345"))
        assertFalse(ClipboardManager.smsCodeEntered("012345", "01234"))
        assertFalse(ClipboardManager.smsCodeEntered("012345", "0123456"))
        assertFalse(ClipboardManager.smsCodeEntered("012345", ""))
        assertFalse(ClipboardManager.smsCodeEntered("012345", null))
        assertFalse(ClipboardManager.smsCodeEntered("", "012345"))
    }
}
