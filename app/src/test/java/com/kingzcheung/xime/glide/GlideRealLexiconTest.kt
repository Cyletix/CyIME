package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** 随包词表（english.txt）上的合成轨迹：常用词应排第一，单次识别耗时有上限。 */
class GlideRealLexiconTest {
    private val unit = 60f
    private val height = 80f
    private val keys: Map<Char, LetterKeyRect> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (letters, inset) ->
            letters.forEachIndexed { index, char -> put(char, LetterKeyRect((inset + index) * unit, row * height, unit, height)) }
        }
    }
    private val words = File("src/main/assets/english.txt").readLines()
        .map { it.trim().lowercase() }.filter { it.length >= 2 && it.all { c -> c in 'a'..'z' } }.distinct()

    private fun trace(word: String, seed: Int): Pair<FloatArray, FloatArray> {
        val random = Random(seed)
        val centers = word.map { char -> keys.getValue(char).let { (it.x + it.width / 2) to (it.y + it.height / 2) } }
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        centers.zipWithNext().forEach { (a, b) ->
            for (step in 0 until 10) {
                val t = step / 10f
                xs += a.first + (b.first - a.first) * t + (random.nextFloat() - .5f) * 10f
                ys += a.second + (b.second - a.second) * t + (random.nextFloat() - .5f) * 10f
            }
        }
        xs += centers.last().first; ys += centers.last().second
        return xs.toFloatArray() to ys.toFloatArray()
    }

    @Test fun commonWordsComeFirstWithinTheLatencyBudget() {
        val decoder = GlideDecoder(GlideLexicon.keyboard(keys), words)
        val targets = listOf("hello", "world", "people", "think", "because", "keyboard", "would", "about", "there",
            "their", "which", "could", "other", "after", "first", "great", "where", "through", "between", "information",
            "thanks", "please", "today", "really", "something", "should", "never", "music", "online", "question")
        val misses = ArrayList<String>()
        val started = System.nanoTime()
        targets.forEachIndexed { index, word ->
            val (xs, ys) = trace(word, index)
            val result = decoder.decode(xs, ys).map { it.word }
            if (result.firstOrNull() != word) misses += "$word→${result.take(3)}"
        }
        val perWordMs = (System.nanoTime() - started) / 1e6 / targets.size
        println("glide: ${decoder.size} templates, ${"%.2f".format(perWordMs)} ms/word, misses=$misses")
        assertTrue("misses: $misses", misses.size <= 2)
        assertTrue("too slow: $perWordMs ms", perWordMs < 50.0)
    }
}
