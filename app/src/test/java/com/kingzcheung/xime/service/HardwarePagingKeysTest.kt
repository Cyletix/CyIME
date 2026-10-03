package com.kingzcheung.xime.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwarePagingKeysTest {
    private val previous = listOf(KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_COMMA)
    private val next = listOf(KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_PERIOD)

    @Test fun compositionReservesAllPagingKeysEvenWithoutCandidates() {
        previous.forEach { assertEquals(-1, hardwareCandidatePageDirection(it, hasInput = true, modified = false)) }
        next.forEach { assertEquals(1, hardwareCandidatePageDirection(it, hasInput = true, modified = false)) }
    }

    @Test fun symbolsOnlyReturnWhenCompositionIsEmpty() {
        (previous + next).forEach { assertEquals(0, hardwareCandidatePageDirection(it, hasInput = false, modified = false)) }
        for ((key, text) in listOf(KeyEvent.KEYCODE_MINUS to "-", KeyEvent.KEYCODE_EQUALS to "=",
            KeyEvent.KEYCODE_LEFT_BRACKET to "[", KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
            KeyEvent.KEYCODE_COMMA to ",", KeyEvent.KEYCODE_PERIOD to ".")) {
            assertEquals(text, keyCodeToKey(key, false))
        }
        assertEquals("<", keyCodeToKey(KeyEvent.KEYCODE_COMMA, true))
        assertEquals(">", keyCodeToKey(KeyEvent.KEYCODE_PERIOD, true))
    }

    @Test fun controlAltAndMetaChordsAreNotPagingCommands() {
        (previous + next).forEach { assertEquals(0, hardwareCandidatePageDirection(it, true, modified = true)) }
        assertEquals(0, hardwareCandidatePageDirection(KeyEvent.KEYCODE_A, true, false))
    }
}
