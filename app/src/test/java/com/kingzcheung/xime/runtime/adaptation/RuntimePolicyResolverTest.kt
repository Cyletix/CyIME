package com.kingzcheung.xime.runtime.adaptation

import org.junit.Assert.*
import org.junit.Test

class RuntimePolicyResolverTest {
    private val device = DeviceCapabilities(0, 35, listOf("arm64-v8a"), false, 8 * DeviceCapabilities.GIB, 8)
    private fun <T> request(value: T) = RuntimeRequest(value, PreferenceOrigin.SAVED_UNATTRIBUTED)
    private val requests = RuntimeRequests(request(AnimationLevel.STANDARD), request(true), request(true), request(true))
    private fun healthy(at: Long) = RuntimeSignals(at, false, false, false, ThermalState.NONE,
        2 * DeviceCapabilities.GIB, false, true)
    private fun resolve(signals: RuntimeSignals, old: RuntimePolicyResolver.History? = null,
        requested: RuntimeRequests = requests, hardware: DeviceCapabilities = device) =
        RuntimePolicyResolver.resolve(hardware, signals, requested, old)

    @Test fun `memory is only an estimate and invalid readings remain unknown`() {
        assertEquals(MemoryEstimate.UNKNOWN, device.copy(totalMemoryBytes = 0).memoryEstimate)
        assertEquals(MemoryEstimate.UNKNOWN, device.copy(totalMemoryBytes = -1).memoryEstimate)
        assertEquals(MemoryEstimate.UNKNOWN, device.copy(lowRam = null).memoryEstimate)
        assertEquals(MemoryEstimate.CONSERVATIVE, device.copy(lowRam = true).memoryEstimate)
        assertEquals(MemoryEstimate.CONSERVATIVE, device.copy(totalMemoryBytes = 3 * DeviceCapabilities.GIB).memoryEstimate)
        assertEquals(MemoryEstimate.STANDARD, device.copy(totalMemoryBytes = 4 * DeviceCapabilities.GIB).memoryEstimate)
        assertEquals(MemoryEstimate.MEASUREMENT_ELIGIBLE, device.memoryEstimate)
    }

    @Test fun `high memory and core counts never qualify correction or speculative prewarm`() {
        val proposal = resolve(healthy(0), hardware = device.copy(processors = 32)).proposal
        assertFalse(proposal.voiceCorrection.proposed)
        assertEquals(setOf(PolicyReason.MEASUREMENT_REQUIRED), proposal.voiceCorrection.reasons)
        assertFalse(proposal.speculativePrewarm)
        assertTrue(proposal.neuralPrediction.proposed)
        assertEquals(setOf(PolicyReason.RESOURCE_CHECK_REQUIRED), proposal.neuralPrediction.reasons)
    }

    @Test fun `unknown signals cannot silently enable enhancements`() {
        val proposal = resolve(RuntimeSignals(0)).proposal
        assertEquals(AnimationLevel.STANDARD, proposal.animation.proposed)
        assertFalse(proposal.keyGlow.proposed)
        assertFalse(proposal.neuralPrediction.proposed)
        assertTrue(PolicyReason.UNKNOWN_SIGNALS in proposal.neuralPrediction.reasons)
        assertFalse(resolve(healthy(0).copy(powerSave = true, thermal = ThermalState.UNKNOWN)).proposal.neuralPrediction.proposed)
    }

    @Test fun `disabled user features stay disabled without changing the request`() {
        val requested = RuntimeRequests(request(AnimationLevel.OFF), request(false), request(false), request(false))
        val proposal = resolve(healthy(0), requested = requested).proposal
        assertEquals(requested.animation, proposal.animation.requested)
        assertEquals(AnimationLevel.OFF, proposal.animation.proposed)
        assertFalse(proposal.neuralPrediction.proposed)
        assertEquals(setOf(PolicyReason.NOT_REQUESTED), proposal.neuralPrediction.reasons)
        assertFalse(proposal.voiceCorrection.proposed)
        assertFalse(proposal.keyGlow.proposed)
    }

    @Test fun `system animation stop and severe heat apply immediately`() {
        val normal = resolve(healthy(0))
        val stopped = resolve(healthy(1).copy(animationsEnabled = false, thermal = ThermalState.SEVERE), normal)
        assertEquals(AnimationLevel.OFF, stopped.proposal.animation.proposed)
        assertEquals(setOf(PolicyReason.SYSTEM_ANIMATIONS_OFF), stopped.proposal.animation.reasons)
        assertFalse(stopped.proposal.keyGlow.proposed)
        assertFalse(stopped.proposal.neuralPrediction.proposed)
        assertEquals(requests.neuralPrediction, stopped.proposal.neuralPrediction.requested)
    }

    @Test fun `system low memory is immediate even with large total memory`() {
        val proposal = resolve(healthy(1).copy(memoryLow = true), resolve(healthy(0))).proposal
        assertEquals(AnimationLevel.REDUCED, proposal.animation.proposed)
        assertFalse(proposal.neuralPrediction.proposed)
        assertTrue(PolicyReason.LOW_MEMORY in proposal.neuralPrediction.reasons)
    }

    @Test fun `power save needs two time separated observations not duplicate callbacks`() {
        var history = resolve(healthy(0))
        for (time in listOf(1L, 1L, 2L, 9999L)) {
            history = resolve(healthy(time).copy(powerSave = true), history)
            assertTrue(history.proposal.neuralPrediction.proposed)
        }
        history = resolve(healthy(10001).copy(powerSave = true), history)
        assertFalse(history.proposal.neuralPrediction.proposed)
        assertEquals(AnimationLevel.REDUCED, history.proposal.animation.proposed)
    }

    @Test fun `charging cancels only low battery pressure not thermal pressure`() {
        var history = resolve(healthy(0).copy(batteryLow = true, charging = true))
        history = resolve(healthy(10000).copy(batteryLow = true, charging = true), history)
        assertTrue(history.proposal.neuralPrediction.proposed)
        history = resolve(healthy(10001).copy(batteryLow = true, charging = true, thermal = ThermalState.SEVERE), history)
        assertFalse(history.proposal.neuralPrediction.proposed)
    }

    @Test fun `low battery without known charging is not invented as normal`() {
        assertFalse(resolve(healthy(0).copy(batteryLow = true, charging = null)).proposal.neuralPrediction.proposed)
    }

    @Test fun `only one feature recovers every thirty seconds of healthy observations`() {
        var history = resolve(healthy(0).copy(thermal = ThermalState.SEVERE))
        for (time in listOf(10000L, 20000L, 30000L)) {
            history = resolve(healthy(time), history)
            assertEquals(AnimationLevel.REDUCED, history.proposal.animation.proposed)
            assertFalse(history.proposal.neuralPrediction.proposed)
        }
        history = resolve(healthy(40000), history)
        assertEquals(AnimationLevel.STANDARD, history.proposal.animation.proposed)
        assertFalse(history.proposal.neuralPrediction.proposed)
        for (time in listOf(50000L, 60000L, 70000L)) history = resolve(healthy(time), history)
        assertTrue(history.proposal.neuralPrediction.proposed)
        assertFalse(history.proposal.keyGlow.proposed)
        for (time in listOf(80000L, 90000L, 100000L)) history = resolve(healthy(time), history)
        assertTrue(history.proposal.keyGlow.proposed)
    }

    @Test fun `sampling gaps and unknown temperatures do not count as recovery`() {
        var history = resolve(healthy(0).copy(thermal = ThermalState.SEVERE))
        history = resolve(healthy(10000), history)
        history = resolve(healthy(100000), history)
        assertEquals(AnimationLevel.REDUCED, history.proposal.animation.proposed)
        for (time in listOf(110000L, 120000L, 130000L, 140000L)) {
            history = resolve(healthy(time).copy(thermal = ThermalState.UNKNOWN), history)
        }
        assertEquals(AnimationLevel.REDUCED, history.proposal.animation.proposed)
    }

    @Test fun `stale timestamps never remove an urgent restriction`() {
        val hot = resolve(healthy(200).copy(thermal = ThermalState.SEVERE))
        assertEquals(hot, resolve(healthy(100), hot))
    }

    @Test fun `API 28 recovers basic animations without pretending thermal is known`() {
        val older = device.copy(sdk = 28)
        var history = resolve(healthy(0).copy(thermal = ThermalState.UNKNOWN, animationsEnabled = false), hardware = older)
        for (time in listOf(10000L, 20000L, 30000L, 40000L)) {
            history = resolve(healthy(time).copy(thermal = ThermalState.UNKNOWN), history, hardware = older)
        }
        assertEquals(AnimationLevel.STANDARD, history.proposal.animation.proposed)
        assertFalse(history.proposal.neuralPrediction.proposed)
    }

    @Test fun `brief pressure spike cannot be accumulated across normal windows`() {
        var history = resolve(healthy(0).copy(powerSave = true))
        history = resolve(healthy(10000), history)
        history = resolve(healthy(20000).copy(powerSave = true), history)
        assertTrue(history.proposal.neuralPrediction.proposed)
    }

    @Test fun `glow only and motion only requests never enable their disabled counterpart`() {
        val glowOnly = requests.copy(animation = request(AnimationLevel.OFF))
        val lit = resolve(healthy(0), requested = glowOnly).proposal
        assertEquals(AnimationLevel.OFF, lit.animation.proposed)
        assertTrue(lit.keyGlow.proposed)
        val motionOnly = requests.copy(keyGlow = request(false))
        val moving = resolve(healthy(0), requested = motionOnly).proposal
        assertEquals(AnimationLevel.STANDARD, moving.animation.proposed)
        assertFalse(moving.keyGlow.proposed)
        assertEquals(setOf(PolicyReason.NOT_REQUESTED), moving.keyGlow.reasons)
    }
}
