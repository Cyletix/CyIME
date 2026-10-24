package com.kingzcheung.xime.speech.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Unit-circle voiceprints: angle = voice; cos(angle difference) = similarity (standard threshold 0.45 ≈ 1.10 rad). */
class SpeakerTrackerTest {
    private fun print(angle: Double) = floatArrayOf(cos(angle).toFloat(), sin(angle).toFloat(), 0f)

    /** Windows every second, 2 s long, for [seconds] of one voice starting at [from] ms. */
    private fun speak(t: SpeakerTracker, from: Long, seconds: Int, angle: Double, jitter: Double = 0.0): List<SpeakerTracker.Label> =
        (1..seconds).flatMap { k -> t.addWindow(from + (k - 2) * 1000L, from + k * 1000L, print(angle + if (k % 2 == 0) jitter else -jitter)) }

    @Test fun `a merged-away voice never labels later sentences`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0)                   // A
        speak(t, 4000, 6, 2.0)                // B, windows ending 5000..10000
        val a = t.speakerIds.first(); val b = t.speakerIds.last()
        // Short probes over 8-10 s are sure it is B; then the two turn out to be one person.
        for (end in 8750L..10000L step 250) t.addProbe(end - 750, end, print(2.0))
        t.merge(b, a)
        val labels = t.addSegment(1, 8200, 9800) + t.settle()
        assertTrue(labels.isNotEmpty())
        assertTrue("labels name only living voices: $labels", labels.all { it.speakerId in t.speakerIds && it.runs.all { r -> r.first in t.speakerIds } })
    }

    @Test fun `a splinter of one sentence joins the voice it sounds most like, not the one nearest in time`() {
        val t = SpeakerTracker()
        speak(t, 0, 10, 0.0)                                  // A
        t.addWindow(10_000, 12_000, print(1.6))               // a voice like nobody: a new person at once
        t.addWindow(11_000, 13_000, print(1.6))
        t.addSegment(1, 10_500, 12_500)
        speak(t, 13_000, 7, 0.0)                              // A again, right after it
        speak(t, 20_000, 20, 3.0)                             // B, closer to the splinter than A is
        t.addSegment(2, 1000, 9000); t.addSegment(3, 21_000, 39_000)
        t.settle()
        val splinter = t.speakerIds.first { it != t.speakerIds.first() && it != t.speakerIds.last() }
        val b = t.speakerIds.last()
        val moved = t.absorbFragments()
        assertTrue(splinter !in t.speakerIds)
        assertEquals(b, moved.first { it.segmentId == 1L }.speakerId)
    }

    @Test fun `in-person captions still find a third person`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0); speak(t, 4000, 4, 2.0); speak(t, 8000, 4, 4.0)
        assertEquals(3, t.speakerIds.size)
    }

    @Test fun `a sentence takes the voice that covers most of its span`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0)
        speak(t, 4000, 4, 2.0)
        speak(t, 8000, 4, 2.0)
        val labels = t.addSegment(1, 500, 3800) + t.addSegment(2, 4300, 7800)
        val a = labels.first { it.segmentId == 1L }.speakerId
        val b = labels.first { it.segmentId == 2L }.speakerId
        assertNotEquals(a, b)
    }

    @Test fun `short comma-length sentences of one voice never open new speakers`() {
        val t = SpeakerTracker()
        speak(t, 0, 10, 0.0, jitter = .5) // a wobbling but single voice (cos 1.0 ≈ 0.54 between extremes)
        val labels = (0 until 8).flatMap { t.addSegment(it.toLong(), it * 1200L, it * 1200L + 900) }
        assertEquals(1, labels.map { it.speakerId }.toSet().size)
        assertEquals(1, t.speakerIds.size)
    }

    @Test fun `one somewhat odd window does not create a person, two alike do`() {
        val t = SpeakerTracker()
        speak(t, 0, 3, 0.0)
        t.addWindow(3000, 4000, print(1.3)) // cos 1.3 ≈ 0.27: odd, but not clearly someone else
        speak(t, 4000, 3, 0.0)
        assertEquals(1, t.speakerIds.size)
        t.addWindow(8000, 9000, print(1.3))
        t.addWindow(9000, 10000, print(1.32))
        assertEquals(2, t.speakerIds.size)
    }

    @Test fun `a clearly different voice is a new person at once and relabels its sentence`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0)
        t.addSegment(1, 0, 3800)
        val labels = t.addWindow(3000, 5000, print(2.5)) + t.addSegment(2, 4200, 5000)
        assertEquals(2, t.speakerIds.size)
        assertNotEquals(labels.first { it.segmentId == 2L }.speakerId, t.speakerIds.first())
    }

    @Test fun `a sentence holding two voices reports both runs in order`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0)          // windows ending 1000..4000
        speak(t, 4000, 4, 2.5)       // windows ending 5000..8000
        val label = t.addSegment(1, 1000, 7500).first { it.segmentId == 1L } // windows already cover its end
        assertTrue(label.initial)
        assertEquals(2, label.runs.size)
        assertNotEquals(label.runs[0].first, label.runs[1].first)
        assertTrue(label.runs[1].second in 3000L..5000L)
    }

    @Test fun `clusters of one voice that drift together are merged and earlier sentences relabelled`() {
        val t = SpeakerTracker()
        speak(t, 0, 3, 0.0)
        t.addSegment(1, 0, 2500)
        // A second cluster forms at 1.3 rad (cos 1.3 ≈ 0.27 < 0.40) ...
        speak(t, 3000, 3, 1.3)
        t.addSegment(2, 3000, 5500)
        assertEquals(2, t.speakerIds.size)
        // ... then the voice settles between them and pulls the centroids together.
        val labels = speak(t, 6000, 30, 0.65, jitter = .1)
        assertTrue(t.speakerIds.size == 1)
        assertTrue(labels.any { it.segmentId == 2L } || labels.any { it.segmentId == 1L })
    }

    @Test fun `manual merge moves every sentence`() {
        val t = SpeakerTracker()
        speak(t, 0, 3, 0.0); speak(t, 3000, 3, 2.0)
        val l = t.addSegment(1, 0, 2500) + t.addSegment(2, 3200, 5500)
        val from = l.first { it.segmentId == 2L }.speakerId
        val into = l.first { it.segmentId == 1L }.speakerId
        val moved = t.merge(from, into)
        assertEquals(listOf(SpeakerTracker.Label(2, into)), moved)
        assertEquals(listOf(into), t.speakerIds)
    }

    @Test fun `ignored television voice absorbs a near match`() {
        val t = SpeakerTracker()
        speak(t, 0, 3, 2.0)
        val tv = t.speakerIds.single()
        t.setIgnored(tv, true)
        speak(t, 3000, 2, 2.0 + 1.19) // cos ≈ 0.37: below the 0.45 standard but within the ignored slack
        assertEquals(listOf(tv), t.speakerIds)
    }

    @Test fun `settle labels sentences with no window after them`() {
        val t = SpeakerTracker()
        speak(t, 0, 3, 0.0)
        assertTrue(t.addSegment(1, 2500, 5000).isEmpty())
        assertEquals(1, t.settle().size)
    }

    @Test fun `a voice marked as me is remembered and takes the holder's next sentences`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0); speak(t, 4000, 4, 2.0)
        val labels = t.addSegment(1, 0, 3800) + t.addSegment(2, 4300, 7800)
        val holder = labels.first { it.segmentId == 1L }.speakerId
        val print = t.markSelf(holder)!!
        assertTrue(SpeakerTracker.SELF in t.speakerIds && holder !in t.speakerIds)
        // Next session: only the remembered print is known; the holder speaking again goes to SELF.
        val next = SpeakerTracker()
        next.seedSelf(print)
        speak(next, 0, 4, 0.05); speak(next, 4000, 4, 2.0)
        val again = next.addSegment(1, 0, 3800) + next.addSegment(2, 4300, 7800)
        assertEquals(SpeakerTracker.SELF, again.first { it.segmentId == 1L }.speakerId)
        assertNotEquals(SpeakerTracker.SELF, again.first { it.segmentId == 2L }.speakerId)
    }

    @Test fun `a remembered person is recognised by name in the next session`() {
        val t = SpeakerTracker()
        speak(t, 0, 4, 0.0); speak(t, 4000, 4, 2.0)
        val labels = t.addSegment(1, 0, 3800) + t.addSegment(2, 4300, 7800)
        val grandson = labels.first { it.segmentId == 2L }.speakerId
        val print = t.remember(grandson, 1001)!!
        assertTrue(1001 in t.speakerIds && grandson !in t.speakerIds)
        val next = SpeakerTracker()
        next.seedKnown(1001, print)
        speak(next, 0, 4, 2.05); speak(next, 4000, 4, 0.0)
        val again = next.addSegment(1, 0, 3800) + next.addSegment(2, 4300, 7800)
        assertEquals(1001, again.first { it.segmentId == 1L }.speakerId)
        assertNotEquals(1001, again.first { it.segmentId == 2L }.speakerId)
    }

    @Test fun `two pass keeps the preview when SenseVoice drops most of the speech`() {
        assertEquals("明天上午我来接你去医院", chooseFinal("明天上午我来接你去医院", "明天。"))
        assertEquals("明天上午我来接你，去医院。", chooseFinal("明天上午我来接你去医院", "明天上午我来接你，去医院。"))
        assertEquals("好的。", chooseFinal("好的", "好的。"))
        assertEquals("", chooseFinal("嗯", ""))
    }
}
