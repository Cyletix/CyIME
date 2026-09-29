package com.kingzcheung.xime.service

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InputCommandOwnerTest {
    @Test fun editorSwitchDuringSuspensionPreventsDeliveryAndRestoresContext() = runBlocking {
        val gate = InputReadiness().also { it.completeStartup() }
        val owner = InputCommandOwner(gate.ticket()!!, 1) {}
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var delivered = false
        val job = launch(Dispatchers.Default + owner.context()) {
            assertEquals(owner, InputCommandOwner.current.get())
            entered.complete(Unit)
            resume.await()
            withContext(Dispatchers.IO) {
                InputCommandOwner.current.get()!!.requireCurrent(gate, 2)
                delivered = true
            }
        }
        entered.await()
        gate.newEditorSession()
        resume.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(delivered)
        assertNull(InputCommandOwner.current.get())
    }

    @Test fun deploymentInvalidatesSameEditorButNewCommandCanProceed() = runBlocking {
        val gate = InputReadiness().also { it.completeStartup() }
        val old = InputCommandOwner(gate.ticket()!!, 1) {}
        gate.deployment(true)
        gate.deployment(false)
        assertThrows(CancellationException::class.java) { old.requireCurrent(gate, 1) }
        val current = InputCommandOwner(gate.ticket()!!, 1) {}
        withContext(Dispatchers.Default + current.context()) {
            InputCommandOwner.current.get()!!.requireCurrent(gate, 1)
        }
        assertNull(InputCommandOwner.current.get())
    }
}
