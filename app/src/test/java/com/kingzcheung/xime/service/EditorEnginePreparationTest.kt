package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.InputCommandQueue
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EditorEnginePreparationTest {
    @Test fun repeatedFocusDoesNotReloadTheSameDictionary() {
        var loads = 0
        var clears = 0
        repeat(500) {
            assertTrue(prepareEditorEngine("pinyin", "pinyin", { loads++; true }, { clears++ }, {}))
        }
        assertEquals(0, loads)
        assertEquals(500, clears)
    }

    @Test fun failedSwitchDoesNotClearOrRestoreTheWrongSchema() {
        val events = mutableListOf<String>()
        assertFalse(prepareEditorEngine("pinyin", "wubi", { events += it; false },
            { events += "clear" }, { events += "restore" }))
        assertEquals(listOf("wubi"), events)
    }

    @Test fun firstKeysWaitForPreparationAndRemainInOrder() = runBlocking {
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        try {
            queue.submit {
                started.complete(Unit)
                release.await()
                prepareEditorEngine("pinyin", "wubi", { events += "switch"; true },
                    { events += "clear" }, { events += "options" })
            }
            withTimeout(3000) { started.await() }
            val keys = "nihao".map { key -> queue.submit { events += key.toString() } }
            assertTrue(queue.hasPendingCommands)
            assertTrue(keys.none { it.isCompleted })
            release.complete(Unit)
            withTimeout(3000) { keys.joinAll() }
            assertEquals(listOf("switch", "clear", "options", "n", "i", "h", "a", "o"), events)
            assertFalse(queue.hasPendingCommands)
        } finally { queue.close() }
    }

    @Test fun delayedPreparationFromOldFocusCannotTouchTheNewEditor() = runBlocking {
        val readiness = InputReadiness().also { it.completeStartup() }
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val ticket = readiness.ticket()!!
        val owner = InputCommandOwner(ticket, 1) {
            if (!readiness.accepts(ticket)) throw CancellationException("old editor")
        }
        var oldTouchedEngine = false
        var newTyped = false
        try {
            queue.submit { started.complete(Unit); release.await() }
            withTimeout(3000) { started.await() }
            val old = queue.submit(owner.context()) {
                InputCommandOwner.requireOwner().requireCurrent(readiness, 1)
                oldTouchedEngine = true
            }
            readiness.newEditorSession()
            val next = queue.submit { newTyped = true }
            release.complete(Unit)
            withTimeout(3000) { next.join() }
            assertTrue(old.isCancelled)
            assertFalse(oldTouchedEngine)
            assertTrue(newTyped)
        } finally { queue.close() }
    }

    @Test fun newClipboardCannotInterruptCompositionEnglishOrAnUnrenderedKey() {
        assertFalse(canShowClipboardSuggestions(true, "", false))
        assertFalse(canShowClipboardSuggestions(false, "hello", false))
        assertFalse(canShowClipboardSuggestions(false, "", true))
        assertTrue(canShowClipboardSuggestions(false, "", false))
    }
}
