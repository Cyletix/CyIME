package com.kingzcheung.xime.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechDetectionLevelTest {
    @Test fun `quiet input is bounded and raw audio is not mutated`() {
        val input = FloatArray(512) { if (it % 2 == 0) .001f else -.001f }
        val before = input.copyOf()
        val output = SpeechDetectionLevel().process(input)
        assertArrayEquals(before, input, 0f)
        assertEquals(.016f, output[0], .000001f)
        assertEquals(-.016f, output[1], .000001f)
        assertTrue(SpeechDetectionLevel().process(FloatArray(512)).all { it == 0f })
    }

    @Test fun `loud input never clips and its history cannot suppress the next session`() {
        val level = SpeechDetectionLevel()
        val loud = floatArrayOf(-.9f, 1f)
        assertSame(loud, level.process(loud))
        repeat(55) { level.process(FloatArray(512)) }
        assertTrue(level.process(floatArrayOf(.001f))[0] > .01f)
        level.process(loud); level.reset()
        assertEquals(.016f, level.process(floatArrayOf(.001f))[0], .000001f)
    }
}
