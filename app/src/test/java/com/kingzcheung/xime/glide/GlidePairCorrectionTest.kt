package com.kingzcheung.xime.glide

import com.kingzcheung.xime.keyboard.LetterKeyRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 英文滑行连着划时前后两个词一起选（用户 10-30 给的 iOS 例子：i 划到 m、n 之间再划 done → I'm done），
 * 缩写换成上屏写法，光标前的词作上下文。双词表、缩写表用随包文件。
 */
class GlidePairCorrectionTest {
    private val unit = 60f
    private val height = 80f
    private val keys: Map<Char, LetterKeyRect> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (letters, inset) ->
            letters.forEachIndexed { index, char -> put(char, LetterKeyRect((inset + index) * unit, row * height, unit, height)) }
        }
    }
    private val contractions = EnglishContractions(File("src/main/assets/english_contractions.txt").readLines().asSequence())
    private val extra = glideExtraWords(File("src/main/assets/english_glide_words.txt").readLines().asSequence())
    private val vocabulary = contractions.vocabulary(File("src/main/assets/english.txt").readLines(), extra)
    private val lm = File("src/main/assets/english_bigrams.txt").bufferedReader().useLines { EnglishBigrams(vocabulary, it) }
    private val decoder = GlideDecoder(GlideLexicon.keyboard(keys),
        vocabulary.filter { it.length >= 2 && it.all { char -> char in 'a'..'z' } })

    private fun center(char: Char) = keys.getValue(char).let { (it.x + it.width / 2) to (it.y + it.height / 2) }

    private fun trace(points: List<Pair<Float, Float>>, random: Random): Pair<FloatArray, FloatArray> {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        points.zipWithNext().forEach { (a, b) ->
            for (step in 0 until 10) {
                val t = step / 10f
                xs += a.first + (b.first - a.first) * t + (random.nextFloat() - .5f) * 10f
                ys += a.second + (b.second - a.second) * t + (random.nextFloat() - .5f) * 10f
            }
        }
        xs += points.last().first; ys += points.last().second
        return xs.toFloatArray() to ys.toFloatArray()
    }

    private fun glide(word: String, random: Random, noise: Float = 0f) = trace(word.map(::center).map { (x, y) ->
        (x + (random.nextFloat() - .5f) * noise) to (y + (random.nextFloat() - .5f) * noise) }, random)
        .let { (xs, ys) -> decoder.decode(xs, ys) }

    @Test fun tablesLoadAndContractionsShowWithApostrophes() {
        assertTrue("双词表 ${lm.size}", lm.size > 200_000)
        assertTrue("didnt 插进了词表", "didnt" in vocabulary && "youre" in vocabulary)
        assertEquals(listOf("I'm", "don't", "didn't", "were", "hello"), listOf("im", "dont", "didnt", "were", "hello").map(contractions::display))
        assertEquals("we're", contractions.offer("were"))
        assertNull(contractions.offer("dont"))
        assertTrue(lm.cost("im", "done") < lm.cost("in", "done"))
        assertTrue(lm.cost("i", "dont") < lm.cost("i", "font"))
    }

    /**
     * english.txt 是去掉脏话的网页词表，fuck、alright、goodbye 这类常用词不在里面，划了只能落到 duck、frederick
     * （用户 11-06：fuck 滑不出来，0% 成功率）。补充词插进滑行词表后要能划出来，原来的词照样划得出。
     */
    @Test fun commonWordsMissingFromEnglishTxtCanBeGlided() {
        val english = File("src/main/assets/english.txt").readLines().toSet()
        val added = listOf("fuck", "fucking", "shit", "damn", "huh", "alright", "goodbye", "kidding", "boyfriend", "hurry", "omg", "wtf")
        assertTrue("这些词 english.txt 本来没有", added.none { it in english })
        assertTrue("补充词都进了滑行词表和双词表", added.all { it in vocabulary && lm.cost(it) < 20f })
        val random = Random(11)
        val misses = (added + listOf("duck", "frederick", "thanks", "please", "giving", "birth")).flatMap { word ->
            List(5) { glide(word, random, noise = 15f) }.mapNotNull { candidates ->
                planGlideCommit(candidates, { it }, null, null, lm, contractions).word.takeIf { it != word }?.let { "$word→$it" }
            }
        }
        assertTrue("misses: $misses", misses.isEmpty())
        assertTrue("didn、goin 这类半截写法不收", "didn" !in vocabulary && "goin" !in vocabulary)
    }

    @Test fun glidingDoneAfterAnAmbiguousImCorrectsItToImDone() {
        val random = Random(1)
        // i 划到 m、n 正中间：单看轨迹 in、im 分不清，in 更常用。
        val (m, n) = center('m') to center('n')
        val ambiguous = trace(listOf(center('i'), ((m.first + n.first) / 2) to m.second), random).let { (xs, ys) -> decoder.decode(xs, ys) }
        val first = planGlideCommit(ambiguous, { it }, null, null, lm, contractions)
        val previous = PreviousGlide(first.word, ambiguous.take(PAIR_TOP)) { it }
        val plan = planGlideCommit(glide("done", random), { it }, previous, null, lm, contractions)
        assertEquals("I'm", plan.replacePrevious ?: previous.text)
        assertEquals("done", plan.word)
        assertTrue("整对候选：${plan.alternatives}", plan.pair && plan.alternatives.all { it.contains(' ') })
        // 实时预览写的就是抬手后的结果：要改上一个词时两个词都写。
        assertEquals(if (plan.replacePrevious != null) "I'm done" else "done", plan.preview)
        assertEquals("done", GlideCommitPlan(null, "done", emptyList(), pair = false).preview)
    }

    @Test fun contextWordAndShiftFollowTheRules() {
        assertEquals("dont", glideContextWord("I don't "))
        assertEquals("hello", glideContextWord("say \"hello"))
        assertNull(glideContextWord("Done. "))
        assertNull(glideContextWord(""))
        val plan = planGlideCommit(glide("dont", Random(2)), { it.replaceFirstChar(Char::uppercaseChar) }, null, "i", lm, contractions)
        assertEquals("Don't", plan.word)
        val were = planGlideCommit(glide("were", Random(3)), { it }, null, "you", lm, contractions)
        assertEquals("were", were.word)
        assertEquals("原词也常用的缩写排第一个候选", "we're", were.alternatives.first())
    }

    /** 抖动一个键宽时，连着划的整句里每个词的首选比各划各的更准。 */
    @Test fun pairsBeatIndependentWordsOnNoisyGlides() {
        val sentences = listOf("i dont know what to do", "im not sure about that", "thats what i said", "where are you going",
            "can you help me with this", "i think we should leave now", "what are you doing here", "she said she was tired",
            "let me know when you get home", "they were here last night", "i didnt see him today", "you have to tell me the truth")
        val random = Random(7)
        var independent = 0; var paired = 0; var total = 0
        for (sentence in sentences) repeat(5) {
            val out = ArrayList<String>()
            var previous: PreviousGlide? = null
            for (word in sentence.split(' ')) {
                total++
                if (word.length < 2) { out += word; previous = null; continue }
                val candidates = glide(word, random, noise = 60f)
                if (candidates.firstOrNull()?.word == word) independent++
                if (candidates.isEmpty()) { out += "?"; previous = null; continue }
                val before = if (previous != null) out.getOrNull(out.size - 2) else out.lastOrNull()
                val plan = planGlideCommit(candidates, { it }, previous, before?.let { glideContextWord(it) }, lm, null)
                plan.replacePrevious?.let { out[out.lastIndex] = it }
                out += plan.word
                previous = PreviousGlide(plan.word, candidates.take(PAIR_TOP)) { it }
            }
            paired += sentence.split(' ').zip(out).count { (a, b) -> a == b }
        }
        println("glide pairs: independent $independent/$total, paired $paired/$total")
        assertTrue("paired $paired independent $independent", paired > independent)
    }
}
