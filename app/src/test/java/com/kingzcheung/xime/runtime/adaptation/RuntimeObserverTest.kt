package com.kingzcheung.xime.runtime.adaptation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeObserverTest {
    private val request = RuntimeRequests(
        RuntimeRequest(AnimationLevel.STANDARD, PreferenceOrigin.SAVED_UNATTRIBUTED),
        RuntimeRequest(true, PreferenceOrigin.CURRENT_DEFAULT),
        RuntimeRequest(false, PreferenceOrigin.CURRENT_DEFAULT),
        RuntimeRequest(true, PreferenceOrigin.CURRENT_DEFAULT),
    )

    private class Source(private val clock: () -> Long) : RuntimeSignalSource {
        var samples = 0
        var subscriptions = 0
        var closes = 0
        var thermal = ThermalState.NONE
        var duringSample: (() -> Unit)? = null
        val listeners = mutableSetOf<() -> Unit>()
        override fun capabilities() = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * DeviceCapabilities.GIB, 8)
        override fun sample(): RuntimeSignals {
            samples++
            duringSample?.invoke()
            return RuntimeSignals(clock(), false, false, false, thermal, DeviceCapabilities.GIB, false, true)
        }
        override fun subscribe(onChanged: () -> Unit): AutoCloseable {
            subscriptions++
            listeners.add(onChanged)
            return AutoCloseable { listeners.remove(onChanged); closes++ }
        }
        fun signal() { listeners.toList().forEach { it() } }
    }

    @Test fun `visible observer samples on worker windows and stops completely when hidden`() = runTest {
        val source = Source { testScheduler.currentTime }
        val changes = mutableListOf<RuntimeObservation>()
        val observer = RuntimeObserver(backgroundScope, source, { request }, changes::add)
        observer.start()
        observer.start()
        runCurrent()
        assertEquals(1, source.samples)
        assertEquals(1, source.subscriptions)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(4, source.samples)
        assertEquals(1, changes.size) // No unchanged-policy log spam.
        observer.stop()
        runCurrent()
        assertNull(observer.observation.value)
        assertEquals(1, source.closes)
        assertTrue(source.listeners.isEmpty())
        advanceTimeBy(120000)
        source.signal()
        runCurrent()
        assertEquals(4, source.samples)
    }

    @Test fun `thermal event is observed without waiting for periodic sampling`() = runTest {
        val source = Source { testScheduler.currentTime }
        val observer = RuntimeObserver(backgroundScope, source, { request })
        observer.start()
        runCurrent()
        source.thermal = ThermalState.SEVERE
        source.signal()
        runCurrent()
        assertFalse(observer.observation.value!!.proposal.neuralPrediction.proposed)
        assertEquals(2, source.samples)
        observer.stop()
        runCurrent()
    }

    @Test fun `late sample from a hidden generation cannot publish`() = runTest {
        val source = Source { testScheduler.currentTime }
        val changes = mutableListOf<RuntimeObservation>()
        val observer = RuntimeObserver(backgroundScope, source, { request }, changes::add)
        source.duringSample = { observer.stop() }
        observer.start()
        runCurrent()
        assertNull(observer.observation.value)
        assertTrue(changes.isEmpty())
        assertEquals(1, source.closes)
    }

    @Test fun `rapid reopen drops an old probe and creates only one live subscription`() = runTest {
        val source = Source { testScheduler.currentTime }
        val changes = mutableListOf<RuntimeObservation>()
        val observer = RuntimeObserver(backgroundScope, source, { request }, changes::add)
        source.duringSample = {
            source.duringSample = null
            observer.stop()
            observer.start()
        }
        observer.start()
        runCurrent()
        assertEquals(1, changes.size)
        assertEquals(1, source.listeners.size)
        assertEquals(2, source.subscriptions)
        assertEquals(1, source.closes)
        observer.stop()
        runCurrent()
        assertEquals(2, source.closes)
    }

    @Test fun `changed settings are read without enabling user-disabled work`() = runTest {
        val source = Source { testScheduler.currentTime }
        var requested = request
        val observer = RuntimeObserver(backgroundScope, source, { requested })
        observer.start()
        runCurrent()
        requested = requested.copy(neuralPrediction = RuntimeRequest(false, PreferenceOrigin.SAVED_UNATTRIBUTED))
        advanceTimeBy(10000)
        runCurrent()
        val proposal = observer.observation.value!!.proposal
        assertFalse(proposal.neuralPrediction.proposed)
        assertEquals(requested.neuralPrediction, proposal.neuralPrediction.requested)
        observer.stop()
        runCurrent()
    }

    @Test fun `hidden time cannot bypass gradual recovery on reopen`() = runTest {
        val source = Source { testScheduler.currentTime }
        source.thermal = ThermalState.SEVERE
        val observer = RuntimeObserver(backgroundScope, source, { request })
        observer.start()
        runCurrent()
        observer.stop()
        runCurrent()
        advanceTimeBy(120000)
        source.thermal = ThermalState.NONE
        observer.start()
        runCurrent()
        assertEquals(AnimationLevel.REDUCED, observer.observation.value!!.proposal.animation.proposed)
        assertFalse(observer.observation.value!!.proposal.neuralPrediction.proposed)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(AnimationLevel.STANDARD, observer.observation.value!!.proposal.animation.proposed)
        assertFalse(observer.observation.value!!.proposal.neuralPrediction.proposed)
        observer.stop()
        runCurrent()
    }
}
