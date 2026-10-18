package com.kingzcheung.xime.speech.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CaptionSpectrumTest {
    private fun pcm(amplitude: Double, hz: Double) = ByteArray(CaptionSpectrum.SIZE * 2).also { out ->
        for (i in 0 until CaptionSpectrum.SIZE) {
            val v = (amplitude * 32767 * sin(2 * PI * hz * i / CaptionRecorder.SAMPLE_RATE)).toInt()
            out[i * 2] = v.toByte(); out[i * 2 + 1] = (v shr 8).toByte()
        }
    }

    @Test fun `silence lies flat`() {
        assertTrue(CaptionSpectrum().bands(ByteArray(CaptionSpectrum.SIZE * 2), 0).all { it == 0f })
    }

    @Test fun `a voice tone lifts its own band most`() {
        val bands = CaptionSpectrum().bands(pcm(.05, 1000.0), 0)
        val loudest = bands.indices.maxBy { bands[it] }
        assertTrue(bands[loudest] > .5f)
        assertEquals(loudest, bands.indices.first { it == loudest })
        assertTrue(bands.first() < bands[loudest] && bands.last() < bands[loudest])
    }
}
