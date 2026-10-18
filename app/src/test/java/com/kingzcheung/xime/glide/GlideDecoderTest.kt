package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 合成 QWERTY 键位上的轨迹：经过的字母键中心加抖动；检查识别首选与手势接管规则。 */
class GlideDecoderTest {
    private val unit = 60f
    private val height = 80f
    private val keys: Map<Char, LetterKeyRect> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (letters, inset) ->
            letters.forEachIndexed { index, char ->
                put(char, LetterKeyRect((inset + index) * unit, row * height, unit, height))
            }
        }
    }
    private val words = listOf("the", "and", "to", "we", "as", "this", "that", "with", "hello", "help", "world",
        "word", "work", "quick", "type", "tyre", "keyboard", "good", "god", "glide", "input", "typing")
    private val decoder = GlideDecoder(GlideLexicon.keyboard(keys), words)

    private fun center(char: Char) = keys.getValue(char).let { (it.x + it.width / 2) to (it.y + it.height / 2) }

    /** 依次连接各字母键中心，每段插值若干点并加抖动。 */
    private fun trace(word: String, jitter: Float = 6f, seed: Int = 1): Pair<FloatArray, FloatArray> {
        val random = Random(seed)
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        val points = word.map(::center)
        points.zipWithNext().forEach { (a, b) ->
            for (step in 0 until 8) {
                val t = step / 8f
                xs += a.first + (b.first - a.first) * t + (random.nextFloat() - .5f) * jitter
                ys += a.second + (b.second - a.second) * t + (random.nextFloat() - .5f) * jitter
            }
        }
        xs += points.last().first; ys += points.last().second
        return xs.toFloatArray() to ys.toFloatArray()
    }

    @Test fun cleanTracesDecodeToTheirWord() {
        listOf("hello", "world", "quick", "keyboard", "this", "typing", "input").forEach { word ->
            val (xs, ys) = trace(word)
            assertEquals(word, decoder.decode(xs, ys).firstOrNull()?.word)
        }
    }

    @Test fun repeatedLettersShareAPathAndFrequencyBreaksTheTie() {
        // good 与 god 轨迹相同：排名靠前的 good 优先，god 仍在候选里。
        val (xs, ys) = trace("god")
        val result = decoder.decode(xs, ys).map { it.word }
        assertEquals("good", result.first())
        assertTrue("god" in result)
    }

    @Test fun traceStartingFarFromEveryWordIsRejected() {
        val xs = floatArrayOf(5f * unit, 5.3f * unit); val ys = floatArrayOf(height * .5f, height * .5f)
        assertTrue(decoder.decode(xs, ys).isEmpty())
    }

    @Test fun shortVerticalFlickIsNotClaimed() {
        val (fx, fy) = center('f')
        val tracker = GlideTracker(keys, 8f, fx, fy, 0)
        // 上滑一行到 r：只经过两个键且横向几乎不动——留给上滑符号。
        for (step in 1..10) assertFalse(tracker.move(fx + step * 0.5f, fy - step * 9f, 10L * step))
        assertFalse(tracker.claimed)
    }

    @Test fun horizontalNeighboursAndLongPathsAreClaimed() {
        val (ax, ay) = center('a')
        val adjacent = GlideTracker(keys, 8f, ax, ay, 0)
        var claimed = false
        for (step in 1..12) claimed = claimed || adjacent.move(ax + step * 5f, ay, 10L * step)
        assertTrue(claimed)

        val (hx, hy) = center('h')
        val long = GlideTracker(keys, 8f, hx, hy, 0)
        val (ex, ey) = center('e')
        for (step in 1..20) long.move(hx + (ex - hx) * step / 20f, hy + (ey - hy) * step / 20f, 10L * step)
        assertTrue(long.claimed)
    }

    @Test fun holdingBeforeMovingLeavesTheGestureToLongPress() {
        val (hx, hy) = center('h')
        val tracker = GlideTracker(keys, 8f, hx, hy, 0)
        tracker.move(hx + 30f, hy, GlideTracker.HOLD_MS + 50)
        assertTrue(tracker.abandoned)
        for (step in 1..20) tracker.move(hx - step * 15f, hy, GlideTracker.HOLD_MS + 50 + step)
        assertFalse(tracker.claimed)
    }

    @Test fun touchesOutsideLettersNeverStartAGlide() {
        assertTrue(GlideTracker(keys, 8f, 5f * unit, 3.5f * height, 0).abandoned)
    }

    @Test fun commitTextAddsASpaceOnlyAfterText() {
        assertEquals("hello", glideCommitText("hello", null))
        assertEquals("hello", glideCommitText("hello", ' '))
        assertEquals("hello", glideCommitText("hello", '('))
        assertEquals(" hello", glideCommitText("hello", 'd'))
        assertEquals(" hello", glideCommitText("hello", '.'))
        assertEquals("Hello", glideCased("hello", shifted = true, capsLocked = false))
        assertEquals("HELLO", glideCased("hello", shifted = true, capsLocked = true))
        assertEquals("hello", glideCased("hello", shifted = false, capsLocked = false))
    }
}
