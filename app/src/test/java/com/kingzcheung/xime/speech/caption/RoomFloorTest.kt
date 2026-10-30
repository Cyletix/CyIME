package com.kingzcheung.xime.speech.caption

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The ribbon's input: flat in a quiet or humming room, up with the voice on any microphone. */
class RoomFloorTest {
    private val rate = CaptionRecorder.SAMPLE_RATE
    private val hop = rate / 30

    private fun db(dbfs: Double) = 10.0.pow(dbfs / 20)

    /** Room: white noise plus mains hum, [dbfs] RMS. */
    private fun room(seconds: Double, dbfs: Double, seed: Long = 1): DoubleArray {
        val random = Random(seed)
        val a = db(dbfs)
        return DoubleArray((seconds * rate).toInt()) { i -> a * (random.nextGaussian() + .7 * sin(2 * PI * 100 * i / rate)) }
    }

    /** Voiced syllables, four a second with gaps between them, peak at [dbfs]. */
    private fun voice(seconds: Double, dbfs: Double): Pair<DoubleArray, (Int) -> Double> {
        val a = db(dbfs) / 4
        val envelope = { i: Int -> sin(2 * PI * 4 * i / rate).coerceAtLeast(0.0) }
        return DoubleArray((seconds * rate).toInt()) { i ->
            val e = envelope(i)
            var s = 0.0
            for (h in 1..10) s += sin(2 * PI * 150 * h * i / rate) / h
            a * e * s
        } to envelope
    }

    private fun frames(signal: DoubleArray): List<Pair<Int, FloatArray>> {
        val pcm = ByteArray(signal.size * 2)
        for (i in signal.indices) {
            val v = (signal[i] * 32767).toInt().coerceIn(-32768, 32767)
            pcm[i * 2] = v.toByte(); pcm[i * 2 + 1] = (v shr 8).toByte()
        }
        val spectrum = CaptionSpectrum()
        return (0 until signal.size - CaptionSpectrum.SIZE step hop).map { it to spectrum.bands(pcm, it) }
    }

    private fun mix(a: DoubleArray, b: DoubleArray) = DoubleArray(minOf(a.size, b.size)) { a[it] + b[it] }

    @Test fun `a hot microphone's room lies flat`() {
        val raw = frames(room(6.0, -45.0))
        // The phone's case: the room alone used to fill the window and stand as a wave.
        assertTrue(raw.drop(30).all { (_, b) -> b.max() > .3f })
        val floor = RoomFloor()
        val out = raw.map { (_, b) -> floor.above(b) }
        assertTrue(out.drop(15).all { b -> b.max() < .06f })
    }

    @Test fun `a quiet room lies flat`() {
        val floor = RoomFloor()
        val out = frames(room(6.0, -80.0)).map { (_, b) -> floor.above(b) }
        assertTrue(out.all { b -> b.max() < .06f })
    }

    @Test fun `the voice still lifts the ribbon over a humming room`() {
        val (speech, envelope) = voice(6.0, -20.0)
        val signal = mix(room(8.0, -45.0), DoubleArray(2 * rate) + speech)
        val floor = RoomFloor()
        val out = frames(signal).map { (at, b) -> at to floor.above(b) }
        val voiced = out.filter { (at, _) -> at >= 2 * rate && envelope(at - 2 * rate + CaptionSpectrum.SIZE / 2) > .7 }
        val lifted = voiced.count { (_, b) -> b.max() > .3f }
        assertTrue("$lifted of ${voiced.size}", lifted >= voiced.size * .9)
    }

    @Test fun `in a quiet room the voice looks as before`() {
        val (speech, envelope) = voice(4.0, -20.0)
        val raw = frames(mix(room(6.0, -80.0), DoubleArray(2 * rate) + speech))
        val floor = RoomFloor()
        val voiced = raw.map { (at, b) -> Triple(at, b, floor.above(b)) }
            .filter { (at, _, _) -> at >= 2 * rate && envelope(at - 2 * rate + CaptionSpectrum.SIZE / 2) > .7 }
        assertTrue(voiced.isNotEmpty())
        assertTrue(voiced.all { (_, b, out) -> out.max() >= b.max() - .12f })
    }
}
