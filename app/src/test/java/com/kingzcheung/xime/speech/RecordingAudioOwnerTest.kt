package com.kingzcheung.xime.speech

import android.media.AudioRecord
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RecordingAudioOwnerTest {
    @Test fun preStartedCaptureIsReleasedEvenBeforeWorkerRuns() {
        val record = mock<AudioRecord>()
        val owner = RecordingAudioOwner(record)
        owner.close()
        assertNull(owner.acquire { error("Cancelled session must not allocate") })
        assertFalse(owner.start())
        owner.close()
        verify(record, times(1)).release()
        verify(record, never()).startRecording()
    }

    @Test fun recorderCreatedAfterCancellationIsImmediatelyReleased() {
        val record = mock<AudioRecord>()
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val owner = RecordingAudioOwner()
        val worker = Thread {
            try {
                assertNull(owner.acquire { entered.countDown(); check(proceed.await(3, TimeUnit.SECONDS)); record })
            } catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            owner.close()
        } finally { proceed.countDown(); worker.join(4000) }
        assertFalse(worker.isAlive)
        failure.get()?.let { throw it }
        verify(record).release()
        assertFalse(owner.start())
    }

    @Test fun failedStopCannotSkipRelease() {
        val record = mock<AudioRecord>()
        doThrow(IllegalStateException("not recording")).whenever(record).stop()
        val owner = RecordingAudioOwner(record)
        owner.close()
        verify(record).release()
    }

    @Test fun startupExceptionStillAllowsExactlyOnceCleanup() {
        val record = mock<AudioRecord>()
        doThrow(IllegalStateException("mic busy")).whenever(record).startRecording()
        val owner = RecordingAudioOwner(record)
        try { owner.start(); fail("Expected microphone startup failure") }
        catch (_: IllegalStateException) { }
        finally { owner.close() }
        owner.close()
        verify(record, times(1)).release()
    }

    @Test fun activePreStartIsReusedWithoutStartingAgain() {
        val record = mock<AudioRecord>()
        whenever(record.recordingState).thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        val owner = RecordingAudioOwner(record)
        assertSame(record, owner.acquire { error("Must reuse pre-started capture") })
        assertTrue(owner.start())
        owner.close()
        verify(record, never()).startRecording()
        verify(record).stop()
        verify(record).release()
    }
}
