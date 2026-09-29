package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CandidateCommitTransactionTest {
    @Test fun rejectionRetainsTextAndRetryRunsAcceptedEffectsExactlyOnce() = runBlocking {
        val owner = InputCommandOwner(1, 1) {}
        val selected = PendingCandidateCommit(owner, "重复重复", true)
        var retained: PendingCandidateCommit? = null
        var submitted = 0
        var learned = 0
        var cleared = 0
        withContext(owner.context()) {
            for (result in listOf(TextCommitResult.REJECTED, TextCommitResult.NO_CONNECTION, TextCommitResult.ACCEPTED_HOST)) {
                selected.deliver(
                    submit = { assertEquals("重复重复", it); submitted++; result },
                    rejected = { retained = it },
                    accepted = { learned++; cleared++; retained = null },
                )
                if (!result.accepted) {
                    assertSame(selected, retained)
                    assertEquals(0, learned)
                    assertEquals(0, cleared)
                }
            }
        }
        assertNull(retained)
        assertEquals(3, submitted)
        assertEquals(1, learned)
        assertEquals(1, cleared)
    }

    @Test fun expiredRetryNeverDeliversToNewEditorAndMissingOwnerIsAnError(): Unit = runBlocking {
        var current = true
        val owner = InputCommandOwner(1, 1) { if (!current) throw CancellationException("editor changed") }
        val selected = PendingCandidateCommit(owner, "旧输入框文字", false)
        var deliveries = 0
        current = false
        try {
            withContext(owner.context()) {
                selected.deliver({ deliveries++; TextCommitResult.ACCEPTED_HOST }, {}, {})
            }
            fail("expired owner must cancel")
        } catch (_: CancellationException) { }
        assertEquals(0, deliveries)
        assertThrows(IllegalStateException::class.java) { InputCommandOwner.requireOwner() }
    }
}
