package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import com.kingzcheung.xime.service.InputCommandOwner
import com.kingzcheung.xime.service.PendingCandidateCommit
import com.kingzcheung.xime.service.TextCommitResult
import com.kingzcheung.xime.service.deliver
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class InputTransactionTest {
    @Test fun fullPinyinDeletionPublishesCurrentEmptySnapshotAfterFinalLetter() {
        val e = engine()
        try {
            assertTrue(e.switchSchema("rime_ice"))
            e.setOption("ascii_mode", false)
            "ggd".forEach { e.processQueuedKeyAndGetResult(it.code, 0) }
            assertEquals("ggd", e.getInput())
            for (expected in listOf("gg", "g", "")) {
                val result = e.deleteCompositionAndGetResult()
                assertEquals(expected, result.inputText)
                assertTrue("delete snapshot must survive cleanup", e.isCandidateRevisionCurrent(result.engineRevision))
                assertEquals("", result.committedText)
                if (expected.isEmpty()) assertTrue(result.candidates.isEmpty())
            }
        } finally { e.clearQueuedComposition() }
    }

    @Test fun unconsumedT9DeleteStaysAheadOfFollowingDigit() = runBlocking {
        val e = engine()
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val deleting = CompletableDeferred<Unit>()
        val finishDelete = CompletableDeferred<Unit>()
        var editorDeletes = 0
        val controller = T9InputController(e, inputCommands = queue, onUnconsumedDelete = {
            deleting.complete(Unit)
            finishDelete.await()
            assertEquals("", e.getInput())
            editorDeletes++
        })
        try {
            controller.onDeleted { assertNotEquals(T9InputController.DeleteResult.NOT_CONSUMED, it) }
            withTimeout(5000) { deleting.await() }
            controller.onDigitPressed("6")
            val done = CompletableDeferred<Unit>()
            controller.enqueueLiteralInput { done.complete(Unit) }
            finishDelete.complete(Unit)
            withTimeout(5000) { done.await() }
            assertEquals(1, editorDeletes)
            assertEquals("6", e.getInput())
        } finally { controller.close(); queue.close(); e.clearQueuedT9Composition() }
    }

    @Test fun acceptedT9ChoiceFinishesBeforeAlreadyQueuedNextDigit() = runBlocking {
        val e = engine()
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val owner = InputCommandOwner(1, 1) {}
        val controller = T9InputController(e, inputCommands = queue, captureInputContext = { owner.context() })
        val selected = CompletableDeferred<Unit>()
        val accept = CompletableDeferred<Unit>()
        val delivered = CompletableDeferred<Unit>()
        try {
            "64".forEach { e.processQueuedT9KeyAndGetResult(it.code) }
            val state = e.readQueuedComposition()!!
            val ni = e.getAllCandidates(500).first { it.text == "你" }
            queue.submit(owner.context()) {
                try {
                    assertEquals(true, controller.onRightCandidateSelected(ni.comment, ni.text, 1, state.engineRevision))
                    selected.complete(Unit)
                    accept.await() // A slow InputConnection must not allow the next key into native state.
                    PendingCandidateCommit(owner, ni.text, true).deliver(
                        { TextCommitResult.ACCEPTED_HOST }, { fail("unexpected rejection") }, {
                            assertTrue(e.t9MemorizeSelection(it.text))
                            e.clearComposition()
                            withContext(Dispatchers.Main) { controller.resetDisplayAfterCommit() }
                        }
                    )
                    delivered.complete(Unit)
                } catch (failure: Throwable) {
                    selected.completeExceptionally(failure)
                    delivered.completeExceptionally(failure)
                }
            }
            withTimeout(5000) { selected.await() }
            controller.onDigitPressed("6")
            val afterDigit = CompletableDeferred<Unit>()
            controller.enqueueLiteralInput { afterDigit.complete(Unit) }
            assertFalse(afterDigit.isCompleted)
            accept.complete(Unit)
            withTimeout(5000) { delivered.await(); afterDigit.await() }
            assertEquals("6", e.getInput())
            // Disposing the keyboard must not stop other commands in the host's queue.
            controller.close()
            assertEquals("6", withTimeout(5000) { queue.execute { e.getInput() } })
        } finally { controller.close(); queue.close(); e.clearQueuedT9Composition() }
    }

    @Test fun partialChoiceRefreshCannotRunAfterFollowingDigit() = runBlocking {
        val e = engine()
        val queue = InputCommandQueue(this, Dispatchers.Default)
        val controller = T9InputController(e, inputCommands = queue)
        val selected = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val refreshed = CompletableDeferred<Unit>()
        try {
            "64426".forEach { e.processQueuedT9KeyAndGetResult(it.code) }
            val state = e.readQueuedComposition()!!
            val ni = e.getAllCandidates(500).first { it.text == "你" }
            queue.submit {
                try {
                    assertEquals(false, controller.onRightCandidateSelected(ni.comment, ni.text, 1, state.engineRevision))
                    selected.complete(Unit)
                    proceed.await()
                    controller.refreshRemainingInput()
                    assertEquals("426", e.getInput())
                    refreshed.complete(Unit)
                } catch (failure: Throwable) {
                    selected.completeExceptionally(failure)
                    refreshed.completeExceptionally(failure)
                }
            }
            withTimeout(5000) { selected.await() }
            controller.onDigitPressed("6")
            val done = CompletableDeferred<Unit>()
            controller.enqueueLiteralInput { done.complete(Unit) }
            proceed.complete(Unit)
            withTimeout(5000) { refreshed.await(); done.await() }
            assertEquals("4266", e.getInput())
        } finally { controller.close(); queue.close(); e.clearQueuedT9Composition() }
    }

    @Test fun multiSegmentCapturedCodesSurviveRejectedCommitAndLearnOnlyAfterRetry() = runBlocking {
        val e = engine()
        val controller = T9InputController(e)
        val owner = InputCommandOwner(1, 1) {}
        var retained: PendingCandidateCommit? = null
        var learned = false
        try {
            "64426".forEach { e.processQueuedT9KeyAndGetResult(it.code) }
            val first = e.readQueuedComposition()!!
            val ni = e.getAllCandidates(500).firstOrNull { it.text == "你" }
            assertNotNull("nihao must offer partial 你", ni)
            assertEquals(false, e.selectQueuedT9Candidate(ni!!.comment, ni.text, 1, first.engineRevision)?.first)
            // The production Router explicitly refreshes the remaining code after partial selection.
            controller.forceSendToRime()
            val refreshed = CountDownLatch(1)
            controller.enqueueLiteralInput { refreshed.countDown() }
            assertTrue(refreshed.await(5, TimeUnit.SECONDS))
            val second = e.readQueuedComposition()!!
            val hao = e.getAllCandidates(500).firstOrNull { it.text == "好" }
            assertNotNull("remaining=${e.getInput()}, candidates=${second.candidates.toList()}", hao)
            assertEquals(true, e.selectQueuedT9Candidate(hao!!.comment, hao.text, 1, second.engineRevision)?.first)
            val pending = PendingCandidateCommit(owner, "你好", true)
            withContext(owner.context()) {
                pending.deliver({ TextCommitResult.REJECTED }, { retained = it }, {
                    learned = e.t9MemorizeSelection(it.text)
                    e.clearQueuedT9Composition()
                })
                assertSame(pending, retained)
                assertFalse(learned)
                pending.deliver({ TextCommitResult.ACCEPTED_HOST }, { fail("retry should succeed") }, {
                    // No display pinyin is supplied: success requires both captured native codes.
                    learned = e.t9MemorizeSelection(it.text)
                    e.clearQueuedT9Composition()
                    retained = null
                })
            }
            assertTrue("multi-segment learning must use complete captured syllables", learned)
            assertNull(retained)
            assertEquals("", e.getInput())
        } finally { controller.close(); e.clearQueuedT9Composition() }
    }

    @Test fun ordinaryKeyWaitingInsideEngineCannotCrossEditorBoundary() {
        val e = engine()
        val executor = Executors.newSingleThreadExecutor()
        val reached = CountDownLatch(1)
        val generation = AtomicLong(1)
        val owner = InputCommandOwner(1, 1) {
            if (generation.get() != 1L) throw kotlinx.coroutines.CancellationException("expired")
        }
        val future = RimeEngine.rimeLock.run {
            lock()
            try {
                val task = executor.submit<Boolean> {
                    runBlocking(owner.context()) {
                        reached.countDown()
                        e.processKeyAndGetResult('a'.code, 0)
                        true
                    }
                }
                assertTrue(reached.await(3, TimeUnit.SECONDS))
                generation.incrementAndGet()
                task
            } finally { unlock() }
        }
        try {
            assertThrows(java.util.concurrent.ExecutionException::class.java) { future.get(5, TimeUnit.SECONDS) }
            assertEquals("", e.getInput())
        } finally { executor.shutdownNow(); e.clearQueuedT9Composition() }
    }
    private fun engine(): RimeEngine = runBlocking {
        assertTrue(RimeConfigHelper.prepareEngine(InstrumentationRegistry.getInstrumentation().targetContext))
        RimeEngine.getInstance().also {
            assertTrue(it.switchSchema("t9_pinyin"))
            it.setOption("ascii_mode", false)
            it.clearQueuedT9Composition()
        }
    }

    @Test fun batchIndicesMatchNativeListAndOldRevisionCannotReadNewInput() {
        val e = engine()
        try {
            val result = e.processQueuedT9KeyAndGetResult('7'.code)!!.state
            val all = e.getAllCandidates(500).toList()
            val batches = mutableListOf<RimeCandidate>()
            while (batches.size < all.size) {
                batches += e.readCandidateBatch(result.engineRevision, batches.size, minOf(64, all.size - batches.size))!!
            }
            assertEquals(all, batches)
            if (all.size < 500) assertEquals(emptyList<RimeCandidate>(), e.readCandidateBatch(result.engineRevision, all.size, 64))
            e.processQueuedT9KeyAndGetResult('4'.code)
            assertNull(e.readCandidateBatch(result.engineRevision, 0, 64))
            assertNull(e.readCandidateBatch(0, 0, 64))
        } finally { e.clearQueuedT9Composition() }
    }

    @Test fun queuedKeyWaitsForReaderAndReturnsItsOwnSnapshot() {
        val e = engine()
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val result = RimeEngine.rimeLock.run {
            lock()
            try {
                val future = executor.submit<RimeEngine.T9KeyResult?> {
                    started.countDown()
                    e.processQueuedT9KeyAndGetResult('7'.code)
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                assertFalse(future.isDone)
                future
            } finally { unlock() }
        }
        try {
            val snapshot = result.get(10, TimeUnit.SECONDS)!!
            assertEquals("7", snapshot.state.inputText)
            assertTrue(snapshot.state.candidates.isNotEmpty())
            assertTrue(snapshot.state.engineRevision > 0)
            assertEquals(snapshot.state.engineRevision, snapshot.state.toComposition().engineRevision)
        } finally { executor.shutdownNow(); e.clearQueuedT9Composition() }
    }

    @Test fun controllerClearWaitsForEngineInsteadOfDroppingTheClear() {
        val e = engine()
        e.processQueuedT9KeyAndGetResult('7'.code)
        val controller = T9InputController(e)
        val done = CountDownLatch(1)
        RimeEngine.rimeLock.lock()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.clearAll() }
            controller.enqueueLiteralInput { done.countDown() }
            assertFalse("clear returned while another thread owned the engine", done.await(200, TimeUnit.MILLISECONDS))
        } finally { RimeEngine.rimeLock.unlock() }
        try {
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals("", e.getInput())
        } finally { controller.close(); e.clearQueuedT9Composition() }
    }

    private fun delayedDelivery(invalidate: (T9InputController, AtomicLong) -> Unit) {
        val e = engine()
        val ins = InstrumentationRegistry.getInstrumentation()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val count = AtomicInteger()
        val admission = AtomicLong()
        val controller = T9InputController(e, onCompositionRefresh = { _, _ -> count.incrementAndGet() },
            inputAdmissionTicket = { admission.get() }, candidateTransform = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                emptyList()
            })
        try {
            ins.runOnMainSync { controller.onDigitPressed("7") }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            ins.runOnMainSync { invalidate(controller, admission) }
            release.countDown()
            controller.enqueueLiteralInput { drained.countDown() }
            assertTrue(drained.await(10, TimeUnit.SECONDS))
            ins.runOnMainSync { assertEquals(0, count.get()); assertEquals("", controller.inputBuffer) }
        } finally { release.countDown(); controller.close(); e.clearQueuedT9Composition() }
    }
    @Test fun resetDropsSnapshotAlreadyComputingOnWorker() = delayedDelivery { controller, _ -> controller.resetLocalState() }
    @Test fun deploymentDropsSnapshotAlreadyComputingOnWorker() = delayedDelivery { _, ticket -> ticket.incrementAndGet() }

    @Test fun deleteDigitDeleteKeepsPhysicalOrderWhileEngineIsBusy() {
        val e = engine()
        e.processQueuedT9KeyAndGetResult('7'.code)
        val controller = T9InputController(e)
        val drained = CountDownLatch(1)
        RimeEngine.rimeLock.lock()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller.onDeleted {}
                controller.onDigitPressed("6")
                controller.onDeleted {}
            }
            controller.enqueueLiteralInput { drained.countDown() }
        } finally { RimeEngine.rimeLock.unlock() }
        try {
            assertTrue(drained.await(10, TimeUnit.SECONDS))
            assertEquals("D-A-D must not execute as D-D-A", "", e.getInput())
        } finally { controller.close(); e.clearQueuedT9Composition() }
    }

    @Test fun snapshotReadCannotStealACommitFromItsOwner() {
        val e = engine()
        assertTrue(e.switchSchema("rime_ice"))
        e.setInput("nihao")
        assertTrue(e.selectCandidate(e.getCandidates().indexOf("你好")))
        assertEquals("", e.readQueuedComposition()!!.committedText)
        assertEquals("你好", e.commit())
    }

    @Test fun oldCandidateTapAndDeleteCannotMutateNewList() {
        val e = engine()
        try {
            val old = e.processQueuedT9KeyAndGetResult('7'.code)!!.state
            e.processQueuedT9KeyAndGetResult('4'.code)
            val current = e.readQueuedComposition()!!
            assertNull(e.selectCandidateAtRevision(0, old.engineRevision))
            assertFalse(e.deleteCandidateAtRevision(0, old.engineRevision))
            assertNull(e.selectQueuedT9Candidate("shi", "是", 1, old.engineRevision))
            val after = e.readQueuedComposition()!!
            assertEquals(current.input, after.input)
            assertEquals(current.engineRevision, after.engineRevision)
            assertEquals(current.candidates.toList(), after.candidates.toList())
        } finally { e.clearQueuedT9Composition() }
    }

    @Test fun ordinarySelectionReturnsItsCommitAtomically() {
        val e = engine()
        try {
            assertTrue(e.switchSchema("rime_ice"))
            e.setInput("nihao")
            val snapshot = e.readQueuedComposition()!!
            val index = snapshot.candidates.indexOfFirst { it.text == "你好" }
            assertTrue(index >= 0)
            val selected = e.selectCandidateAtRevision(index, snapshot.engineRevision)!!
            assertEquals("你好", selected.committedText)
            assertEquals("", e.commit())
        } finally { e.clearComposition() }
    }

    @Test fun switchEditorWhileWaitingForNativeLockRejectsOldKey() {
        val e = engine()
        val admission = AtomicLong(1)
        val controller = T9InputController(e, inputAdmissionTicket = { admission.get() })
        val drained = CountDownLatch(1)
        RimeEngine.rimeLock.lock()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.onDigitPressed("7") }
            admission.incrementAndGet()
            controller.enqueueLiteralInput { drained.countDown() }
        } finally { RimeEngine.rimeLock.unlock() }
        try {
            assertTrue(drained.await(10, TimeUnit.SECONDS))
            assertEquals("", e.getInput())
        } finally { controller.close(); e.clearQueuedT9Composition() }
    }
}
