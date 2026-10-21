package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateClipMemoryTest {
    @Test fun aClipReadInAPasswordFieldIsRecognisedWhenReadAgain() {
        val memory = PrivateClipMemory()
        val password = PrivateClipMemory.key(1_000, "hunter2")
        memory.remember(password)
        // The same clip re-read from a normal field within 30 s stays private.
        assertTrue(password in memory)
        // Copying the same text again later is a new clip (new timestamp): saved normally.
        assertFalse(PrivateClipMemory.key(50_000, "hunter2") in memory)
        assertFalse(PrivateClipMemory.key(1_000, "something else") in memory)
    }

    @Test fun onlyTheLatestClipsAreKept() {
        val memory = PrivateClipMemory(capacity = 2)
        memory.remember(PrivateClipMemory.key(1, "a"))
        memory.remember(PrivateClipMemory.key(2, "b"))
        memory.remember(PrivateClipMemory.key(3, "c"))
        assertFalse(PrivateClipMemory.key(1, "a") in memory)
        assertTrue(PrivateClipMemory.key(3, "c") in memory)
    }
}
