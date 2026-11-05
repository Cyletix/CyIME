package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** 二次校正把关：第二遍改的汉字要过词组模型，过不了的地方留第一遍的字，标点照第二遍。 */
class SecondPassGateTest {
    /** Small word model: listed words and pairs; any other pair costs the word's own probability minus 2. */
    private class FakeModel(private val unigrams: Map<String, Double>, private val pairs: Map<Pair<String, String>, Double>) : WordLanguageModel {
        private val words = unigrams.keys.toList()
        override fun id(text: String) = words.indexOf(text)
        override fun unigram(id: Int) = if (id < 0) -18.0 else unigrams.getValue(words[id])
        override fun bigram(history: Int, word: Int): Double {
            if (word < 0) return -18.0
            if (history < 0) return unigram(word)
            return pairs[words[history] to words[word]] ?: (unigram(word) - 2)
        }
        override fun trigram(history2: Int, history: Int, word: Int) = bigram(history, word)
    }

    private val model = FakeModel(
        mapOf("帮我" to -8.0, "帮" to -9.0, "我" to -6.0, "拿" to -9.0, "那" to -8.0, "个" to -6.0, "那个" to -7.0, "快递" to -9.0,
            "浅绿" to -12.0, "浅" to -11.0, "绿" to -10.0, "米灰" to -13.0, "这些" to -7.0, "开会" to -9.0, "点" to -7.0,
            "今天" to -8.0, "天气" to -9.0, "不错" to -8.0, "很" to -6.0),
        mapOf(("帮我" to "拿") to -2.0, ("拿" to "个") to -1.0, ("个" to "快递") to -2.0, ("今天" to "天气") to -2.0,
            ("天气" to "很") to -2.0, ("很" to "不错") to -1.0))
    private val gate = SecondPassGate(model)

    @Test fun `a second-pass word the model never saw goes back to the first pass, punctuation stays`() {
        assertEquals("试了一下，浅绿米灰这些。", gate.merge("试了一下浅绿米灰这些", "试了一下，嵌绿米灰这些。"))
    }

    @Test fun `a correction the model prefers is taken`() {
        assertEquals("帮我拿个快递。", gate.merge("帮我那个快递", "帮我拿个快递。"))
    }

    @Test fun `a second pass that the model scores clearly worse is put back`() {
        assertEquals("帮我拿个快递吧。", gate.merge("帮我拿个快递吧", "帮我那个快递吧。"))
    }

    @Test fun `digits and English from the second pass are always taken`() {
        assertEquals("3点开会。", gate.merge("三点开会", "3点开会。"))
        assertEquals("打开 WiFi。", gate.merge("打开 why fi", "打开 WiFi。"))
    }

    @Test fun `identical text, an empty first pass or an empty second pass is the second pass`() {
        assertEquals("今天天气很不错。", gate.merge("今天天气很不错", "今天天气很不错。"))
        assertEquals("今天天气很不错。", gate.merge("", "今天天气很不错。"))
        assertEquals("", gate.merge("今天天气很不错", ""))
    }

    @Test fun `added or dropped characters follow the second pass`() {
        assertEquals("今天天很不错。", gate.merge("今天天气很不错", "今天天很不错。"))
        assertEquals("今天天气嵌很不错。", gate.merge("今天天气很不错", "今天天气嵌很不错。"))
    }

    @Test fun `only the rejected place goes back, other changes in the same sentence stay`() {
        assertEquals("3点帮我拿个快递吧。", gate.merge("三点帮我拿个快递吧", "3点帮我那个快递吧。"))
    }

    @Test fun `regions are aligned on letters and digits only`() {
        val regions = diffRegions("abcd".map { it.toString() }, "abxd".map { it.toString() })
        assertEquals(listOf(Region(2, 3, 2, 3)), regions)
        assertEquals(emptyList<Region>(), diffRegions(listOf("a"), listOf("a")))
        assertEquals(listOf(Region(0, 0, 0, 1)), diffRegions(emptyList(), listOf("a")))
    }
}
