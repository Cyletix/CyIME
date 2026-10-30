package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import com.kingzcheung.xime.settings.DoublePinyinScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** 随包中文滑行词表（glide_pinyin.txt）上的合成轨迹：常用词的拼音、双拼应排第一，单次识别耗时有上限。 */
class GlideChineseLexiconTest {
    private val unit = 60f
    private val height = 80f
    private val keys: Map<Char, LetterKeyRect> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (letters, inset) ->
            letters.forEachIndexed { index, char -> put(char, LetterKeyRect((inset + index) * unit, row * height, unit, height)) }
        }
    }
    private val entries = File("src/main/assets/glide_pinyin.txt").readLines().filter { it.isNotBlank() }.map { it.split(' ') }
    private val flypy = DoublePinyinScheme.BUILTINS.first { it.id == "double_pinyin_flypy" }

    private fun trace(code: String, seed: Int): Pair<FloatArray, FloatArray> {
        val random = Random(seed)
        val centers = code.map { char -> keys.getValue(char).let { (it.x + it.width / 2) to (it.y + it.height / 2) } }
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

    private val words = listOf("ni hao", "wo men", "shi jian", "zhong guo", "jin tian", "ming tian", "peng you",
        "gong zuo", "xue xi", "shen me", "zen me", "ke yi", "xian zai", "wen ti", "dian hua", "shou ji", "dian nao",
        "kuai di", "fang jian", "xie xie", "hai shi", "yi jing", "ran hou", "zhi dao", "mei you", "ta men",
        "gong si", "shang ban", "chi fan", "hui jia")

    private fun misses(decoder: GlideDecoder, codes: List<String>): List<String> = codes.mapIndexedNotNull { index, code ->
        val (xs, ys) = trace(code, index)
        val result = decoder.decode(xs, ys).map { it.word }
        if (result.firstOrNull() == code) null else "$code→${result.take(3)}"
    }

    @Test fun lexiconKeepsSyllablesAndEverySingleSyllable() {
        assertTrue(entries.size in 25_000..35_000)
        assertEquals(listOf("zhong", "guo"), entries.first { it.joinToString("") == "zhongguo" })
        assertTrue("单音节都在", listOf("zhuang", "nve", "chuai", "shei").all { listOf(it) in entries })
        assertTrue("书面常用词也在", listOf("ren min", "she hui", "shang shi gong si").all { it.split(' ') in entries })
        assertEquals(listOf("nihao", "vsgo"), listOf(pinyinCodes(listOf(listOf("ni", "hao")), null).single(),
            pinyinCodes(listOf(listOf("zhong", "guo")), flypy).single()))
    }

    @Test fun commonQuanpinWordsComeFirstWithinTheLatencyBudget() {
        val decoder = GlideDecoder(GlideLexicon.keyboard(keys), pinyinCodes(entries, null))
        val started = System.nanoTime()
        val missed = misses(decoder, words.map { it.replace(" ", "") })
        val perWordMs = (System.nanoTime() - started) / 1e6 / words.size
        println("glide pinyin: ${decoder.size} templates, ${"%.2f".format(perWordMs)} ms/word, misses=$missed")
        assertTrue("misses: $missed", missed.size <= 2)
        assertTrue("too slow: $perWordMs ms", perWordMs < 50.0)
    }

    @Test fun commonDoublePinyinWordsComeFirst() {
        val decoder = GlideDecoder(GlideLexicon.keyboard(keys), pinyinCodes(entries, flypy))
        val missed = misses(decoder, words.map { word -> word.split(' ').joinToString("") { flypy.code(it)!! } })
        println("glide flypy: ${decoder.size} templates, misses=$missed")
        assertTrue("misses: $missed", missed.size <= 3)
    }
}
