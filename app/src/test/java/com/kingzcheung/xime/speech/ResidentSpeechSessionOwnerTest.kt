package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ResidentSpeechSessionOwnerTest {
    private open class Engine : LocalSpeechEngine {
        override val hasRefinement = false
        override val isStreaming = false
        val decoderEntered = CountDownLatch(1)
        val allowDecode = CountDownLatch(1)
        val busy = AtomicBoolean()
        val unsafeReset = AtomicBoolean()
        val unsafeClose = AtomicBoolean()
        val resets = AtomicInteger()
        val closes = AtomicInteger()

        override fun detector() = object : SpeechDetector {
            override fun speech(frame: FloatArray) = frame.any { it != 0f }
            override fun reset() { }
        }
        override fun accept(samples: FloatArray): String = error("Offline engine does not stream")
        override fun finishStream(): String = error("Offline engine does not stream")
        override fun refine(samples: FloatArray): String {
            check(busy.compareAndSet(false, true)) { "Concurrent model use" }
            decoderEntered.countDown()
            try {
                check(allowDecode.await(5, TimeUnit.SECONDS)) { "Test did not release decoder" }
                return "识别结果"
            } finally { busy.set(false) }
        }
        override fun reset() {
            if (busy.get()) unsafeReset.set(true)
            resets.incrementAndGet()
        }
        override fun close() {
            if (busy.get()) unsafeClose.set(true)
            closes.incrementAndGet()
        }
    }

    private fun pcm(count: Int, sample: Int = 1600) = ByteArray(count * 2) {
        if (it % 2 == 0) sample.toByte() else (sample shr 8).toByte()
    }
    private fun session(engine: LocalSpeechEngine) = LocalSpeechSession(engine, {}, {})
    private fun blockDecode(owner: ResidentSpeechSessionOwner, engine: Engine) {
        owner.acceptPcm(pcm(16000))
        owner.acceptPcm(pcm(16000, 0))
        assertTrue("Production session should reach native decoding", engine.decoderEntered.await(2, TimeUnit.SECONDS))
    }

    @Test fun `cancel during decode retains same model and restart waits for native idle`() {
        val engine = Engine()
        val creations = AtomicInteger()
        val owner = ResidentSpeechSessionOwner { it() }
        val executor = Executors.newSingleThreadExecutor()
        val startEntered = CountDownLatch(1)
        val newSession = CountDownLatch(1)
        fun create(): Engine { creations.incrementAndGet(); return engine }
        try {
            assertTrue(owner.start("same", ::create, ::session))
            blockDecode(owner, engine)
            owner.cancel()
            val next = executor.submit<Boolean> {
                startEntered.countDown()
                owner.start("same", ::create, { session(it).also { newSession.countDown() } })
            }
            assertTrue(startEntered.await(2, TimeUnit.SECONDS))
            assertFalse("Cannot create another session while recognizer is busy", newSession.await(100, TimeUnit.MILLISECONDS))
            assertEquals(1, creations.get())
            assertEquals(0, engine.resets.get())
            assertEquals(0, engine.closes.get())
            engine.allowDecode.countDown()
            assertTrue(next.get(2, TimeUnit.SECONDS))
            assertEquals(1, creations.get())
            assertEquals(1, engine.resets.get())
            assertFalse(owner.isIdle)
            assertFalse(engine.unsafeReset.get())
            assertFalse(engine.unsafeClose.get())
        } finally {
            engine.allowDecode.countDown()
            owner.release()
            executor.shutdownNow()
        }
    }

    @Test fun `new start cancels pending decode without dropping resident weights`() {
        val engine = Engine()
        val owner = ResidentSpeechSessionOwner { it() }
        val executor = Executors.newSingleThreadExecutor()
        try {
            assertTrue(owner.start("same", { engine }, ::session))
            blockDecode(owner, engine)
            val next = executor.submit<Boolean> { owner.start("same", { error("Unexpected reload") }, ::session) }
            engine.allowDecode.countDown()
            assertTrue(next.get(2, TimeUnit.SECONDS))
            assertFalse(owner.isIdle)
            assertEquals(0, engine.closes.get())
            assertFalse(engine.unsafeReset.get())
        } finally {
            engine.allowDecode.countDown()
            owner.release()
            executor.shutdownNow()
        }
    }

    @Test fun `late stop completion cannot clear new session or invalidate its callback generation`() {
        val engine = Engine().also { it.allowDecode.countDown() }
        val owner = ResidentSpeechSessionOwner { it() }
        val executor = Executors.newSingleThreadExecutor()
        val finishReached = CountDownLatch(1)
        val completeOldStop = CountDownLatch(1)
        val generation = AtomicLong(1)
        try {
            assertTrue(owner.start("same", { engine }, ::session))
            val oldToken = generation.get()
            val oldStop = executor.submit<String> {
                checkNotNull(owner.finish {
                    finishReached.countDown()
                    check(completeOldStop.await(2, TimeUnit.SECONDS))
                    generation.compareAndSet(oldToken, oldToken + 1)
                })
            }
            assertTrue(finishReached.await(2, TimeUnit.SECONDS))
            val newToken = generation.incrementAndGet()
            assertTrue(owner.start("same", { error("Unexpected reload") }, ::session))
            completeOldStop.countDown()
            oldStop.get(2, TimeUnit.SECONDS)
            assertEquals(newToken, generation.get())
            assertFalse("Old stop must retain the new current session", owner.isIdle)
            owner.acceptPcm(pcm(16000))
            assertEquals("识别结果", owner.finish())
        } finally {
            completeOldStop.countDown()
            owner.release()
            executor.shutdownNow()
        }
    }

    @Test fun `queued old release cannot close a newer same-key session`() {
        val releases = ConcurrentLinkedQueue<() -> Unit>()
        val owner = ResidentSpeechSessionOwner { releases.add(it) }
        val engine = Engine()
        assertTrue(owner.start("same", { engine }, ::session))
        assertTrue(owner.release())
        assertTrue(owner.start("same", { error("Unexpected reload") }, ::session))
        releases.remove().invoke()
        assertEquals(0, engine.closes.get())
        assertFalse(owner.isIdle)
        owner.release()
        releases.remove().invoke()
        assertEquals(1, engine.closes.get())
    }

    @Test fun `explicit release waits for in-flight native decode before closing weights`() {
        val releases = ConcurrentLinkedQueue<() -> Unit>()
        val owner = ResidentSpeechSessionOwner { releases.add(it) }
        val engine = Engine()
        val executor = Executors.newSingleThreadExecutor()
        val releaseEntered = CountDownLatch(1)
        try {
            assertTrue(owner.start("same", { engine }, ::session))
            blockDecode(owner, engine)
            assertTrue(owner.release())
            assertEquals(0, engine.closes.get())
            val retirement = executor.submit {
                releaseEntered.countDown()
                releases.remove().invoke()
            }
            assertTrue(releaseEntered.await(2, TimeUnit.SECONDS))
            assertEquals(0, engine.closes.get())
            engine.allowDecode.countDown()
            retirement.get(2, TimeUnit.SECONDS)
            assertEquals(1, engine.closes.get())
            assertFalse(engine.unsafeClose.get())
            assertFalse(engine.unsafeReset.get())
        } finally {
            engine.allowDecode.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `changed model waits for old decode before releasing and constructing`() {
        val old = Engine()
        val replacement = Engine()
        val owner = ResidentSpeechSessionOwner { it() }
        val executor = Executors.newSingleThreadExecutor()
        val createNew = CountDownLatch(1)
        try {
            assertTrue(owner.start("old", { old }, ::session))
            blockDecode(owner, old)
            val next = executor.submit<Boolean> {
                owner.start("new", { createNew.countDown(); replacement }, ::session)
            }
            assertFalse(createNew.await(100, TimeUnit.MILLISECONDS))
            assertEquals(0, old.closes.get())
            old.allowDecode.countDown()
            assertTrue(next.get(2, TimeUnit.SECONDS))
            assertEquals(1, old.closes.get())
            assertEquals(0, replacement.closes.get())
            assertFalse(old.unsafeClose.get())
        } finally {
            old.allowDecode.countDown()
            owner.release()
            executor.shutdownNow()
        }
    }

    @Test fun `superseded request cannot create a session after model selection completes`() {
        val owner = ResidentSpeechSessionOwner { it() }
        val engine = Engine()
        assertTrue(owner.start("same", { engine }, ::session))
        assertFalse(owner.start("other", { error("Stale request must not load a model") },
            { error("Stale request must not create a session") }, isActive = { false }))
        assertFalse(owner.isIdle)
        assertEquals(0, engine.closes.get())
        owner.release()
    }

    @Test fun `cancel while restart waits for native idle prevents the pending session`() {
        val owner = ResidentSpeechSessionOwner { it() }
        val engine = Engine()
        val executor = Executors.newSingleThreadExecutor()
        val startRegistered = CountDownLatch(1)
        try {
            assertTrue(owner.start("same", { engine }, ::session))
            blockDecode(owner, engine)
            owner.cancel()
            val next = executor.submit<Boolean> {
                owner.start("same", { error("Unexpected reload") },
                    { error("Cancelled restart must not create a session") },
                    isActive = { startRegistered.countDown(); true })
            }
            assertTrue(startRegistered.await(2, TimeUnit.SECONDS))
            owner.cancel()
            engine.allowDecode.countDown()
            assertFalse(next.get(2, TimeUnit.SECONDS))
            assertTrue(owner.isIdle)
            assertEquals(0, engine.closes.get())
            assertTrue(owner.start("same", { error("Cancelled restart discarded weights") }, ::session))
        } finally {
            engine.allowDecode.countDown()
            owner.release()
            executor.shutdownNow()
        }
    }

    @Test fun `idle cleanup does not cancel a live session`() {
        val engine = Engine()
        val owner = ResidentSpeechSessionOwner { it() }
        assertTrue(owner.start("same", { engine }, ::session))
        assertFalse(owner.release(onlyIfIdle = true))
        assertFalse(owner.isIdle)
        assertEquals(0, engine.closes.get())
        owner.cancel()
        assertTrue(owner.release(onlyIfIdle = true))
        assertEquals(1, engine.closes.get())
    }

    @Test fun `idle reset is performed once and other waiters wait for reset completion`() {
        val resetEntered = CountDownLatch(1)
        val allowReset = CountDownLatch(1)
        val engine = object : Engine() {
            override fun reset() {
                resetEntered.countDown()
                check(allowReset.await(2, TimeUnit.SECONDS))
                super.reset()
            }
        }
        val session = session(engine)
        val executor = Executors.newFixedThreadPool(2)
        val secondEntered = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        session.cancel()
        try {
            val first = executor.submit { session.awaitIdle() }
            assertTrue(resetEntered.await(2, TimeUnit.SECONDS))
            val second = executor.submit {
                secondEntered.countDown()
                session.awaitIdle()
                secondFinished.countDown()
            }
            assertTrue(secondEntered.await(2, TimeUnit.SECONDS))
            assertFalse(secondFinished.await(100, TimeUnit.MILLISECONDS))
            allowReset.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            session.awaitIdle()
            assertEquals(1, engine.resets.get())
        } finally {
            allowReset.countDown()
            executor.shutdownNow()
        }
    }
}
