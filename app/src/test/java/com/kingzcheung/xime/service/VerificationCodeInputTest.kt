package com.kingzcheung.xime.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VerificationCodeInputTest {
    @Test fun reacquiresNumericFieldAfterEveryDigit() = runTest {
        var field = 0
        val values = mutableListOf<String>()
        assertTrue(VerificationCodeInput.fill("012345", {
            VerificationCodeInput.Target("test", field, 2, field.toLong())
        }, { values += "$field:$it"; true }, { field++ }))
        assertEquals(listOf("0:0", "1:1", "2:2", "3:3", "4:4", "5:5"), values)
    }
    @Test fun sameEditorReceivesWholeCodeAsIndividualCommits() = runTest {
        var value = ""
        assertTrue(VerificationCodeInput.fill("01234567", {
            VerificationCodeInput.Target("test", 1, 2, 1)
        }, { value += it; true }, {}))
        assertEquals("01234567", value)
    }
    @Test fun stopsWhenAppChangesOrEditorRejectsCommit() = runTest {
        var app = "test"
        var committed = ""
        assertFalse(VerificationCodeInput.fill("1234", {
            VerificationCodeInput.Target(app, 1, 2, 1)
        }, { committed += it; true }, { app = "other" }))
        assertEquals("1", committed)
        var calls = 0
        assertFalse(VerificationCodeInput.fill("1234", {
            VerificationCodeInput.Target("test", 1, 2, 1)
        }, { ++calls < 2 }, {}))
        assertEquals(2, calls)
    }
    @Test fun stopsWhenFocusChangesToUnrelatedTextEditorOrDisappears() = runTest {
        var session = 0L
        var calls = 0
        assertFalse(VerificationCodeInput.fill("1234", {
            VerificationCodeInput.Target("test", 1, 1, session)
        }, { calls++; true }, { session++ }))
        assertEquals(1, calls)
        assertFalse(VerificationCodeInput.fill("1234", { null }, { fail(); false }, {}))
    }
    @Test fun stopsIfHostAcknowledgesButDropsDigits() = runTest {
        var calls = 0
        assertFalse(VerificationCodeInput.fill("1234", {
            VerificationCodeInput.Target("test", 1, 2, 1)
        }, { calls++; true }, {}, { "unchanged" }))
        assertEquals(1, calls)
    }
}
