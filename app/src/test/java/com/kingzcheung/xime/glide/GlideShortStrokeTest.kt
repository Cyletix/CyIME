package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import org.junit.Assert.*
import org.junit.Test

/** 真实行为边界：短滑行、角标快划、长按；宽高独立变化，覆盖单手缩窄和悬浮缩放。 */
class GlideShortStrokeTest {
    private fun keys(width: Float, height: Float, staggered: Boolean = true) = buildMap {
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { row, letters ->
            val inset = if (staggered) listOf(0f, .5f, 1.5f)[row] else 0f
            letters.forEachIndexed { index, c ->
                put(c, LetterKeyRect(100f + (index + inset) * width, 200f + row * height, width, height))
            }
        }
    }
    private fun Map<Char, LetterKeyRect>.center(c: Char) = getValue(c).let {
        (it.x + it.width / 2) to (it.y + it.height / 2)
    }
    private fun dimensions() = listOf(40f to 56f, 24f to 56f, 24f to 32f, 72f to 56f)

    @Test fun deAndEdWorkAfterAShortPauseOnFullOneHandAndFloatingBoards() {
        for ((w, h) in dimensions()) for (staggered in listOf(false, true)) for (word in listOf("de", "ed")) {
            val keys = keys(w, h, staggered)
            val a = keys.center(word[0]); val b = keys.center(word[1])
            val tracker = GlideTracker(keys, 8f, a.first, a.second, 0)
            for (step in 1..6) tracker.move(a.first + (b.first - a.first) * step / 6,
                a.second + (b.second - a.second) * step / 6, step * 20L)
            assertTrue("快滑到相邻字母即可输入：$w/$h $word", tracker.claimed)
            tracker.move(b.first, b.second, 220)
            assertTrue("停在邻键可滑行：$w/$h $word staggered=$staggered", tracker.claimed)
        }
    }

    @Test fun horizontalNeighboursNeedLessThanAWholeKeyAndNoPause() {
        for ((w, h) in dimensions()) {
            val keys = keys(w, h)
            val a = keys.center('a'); val b = keys.center('s')
            val start = a.first + .1f * w
            val end = b.first - .1f * w
            val tracker = GlideTracker(keys, 8f, start, a.second, 0)
            assertTrue(tracker.move(end, b.second, 90))
            val decoder = GlideDecoder(GlideLexicon.keyboard(keys), listOf("as"))
            assertEquals("接管的短划也必须能解码", "as", decoder.decode(tracker.xs(), tracker.ys()).firstOrNull()?.word)
        }
    }

    @Test fun movingInsideTheStartingKeyCannotDecodeAWord() {
        val keys = keys(40f, 56f)
        val (x, y) = keys.center('d')
        val tracker = GlideTracker(keys, 8f, x, y, 0)
        tracker.move(x, y - 20, 100)
        assertFalse(tracker.claimed)
    }

    @Test fun holdingAtTheStartStillBelongsToLongPress() {
        val keys = keys(24f, 32f)
        val a = keys.center('d'); val b = keys.center('e')
        val tracker = GlideTracker(keys, 8f, a.first, a.second, 0)
        tracker.move(b.first, b.second, 350)
        tracker.move(b.first, b.second, 500)
        assertTrue(tracker.abandoned)
        assertFalse(tracker.claimed)
    }

    @Test fun pausingAfterMovementDoesNotBecomeAStartHold() {
        val keys = keys(24f, 56f)
        val a = keys.center('d'); val b = keys.center('e')
        val tracker = GlideTracker(keys, 8f, a.first, a.second, 0)
        tracker.move(a.first, a.second - 12f, 70)
        tracker.move(b.first, b.second, 450)
        tracker.move(b.first, b.second, 550)
        assertTrue(tracker.claimed)
        assertFalse(tracker.abandoned)
    }

    @Test fun tapsAndJitterNeverBecomeGlides() {
        for ((w, h) in dimensions()) {
            val keys = keys(w, h)
            val a = keys.center('d')
            val tracker = GlideTracker(keys, 8f, a.first, a.second, 0)
            for (i in 1..10) tracker.move(a.first + i % 3, a.second - i % 3, i * 50L)
            assertFalse(tracker.claimed)
        }
    }

    @Test fun delayedHistoricalSamplesCannotStealAnAlreadyTriggeredLongPress() {
        val keys = keys(24f, 56f)
        val a = keys.center('d'); val b = keys.center('e')
        val tracker = GlideTracker(keys, 8f, a.first, a.second, 0)
        tracker.move(b.first, b.second, time = 280, eventTime = 330)
        tracker.move(b.first, b.second, 430)
        assertTrue(tracker.abandoned)
        assertFalse(tracker.claimed)
    }
}
