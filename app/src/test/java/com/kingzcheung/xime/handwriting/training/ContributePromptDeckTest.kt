package com.kingzcheung.xime.handwriting.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 贡献页随机出题（用户 11-12：原来每人题目一样，收上来的样本全一样）。 */
class ContributePromptDeckTest {

    private fun asset(name: String): File? =
        listOf(File("src/main/assets"), File("app/src/main/assets")).firstOrNull { it.isDirectory }?.resolve(name)
            ?.takeIf { it.isFile }

    private fun lines(name: String): Sequence<String> = asset(name)!!.readLines(Charsets.UTF_8).asSequence()

    private fun bundledPools(): ContributePromptDeck.Pools? {
        if (asset("handwriting/contribute_prompts.tsv") == null || asset(ContributePromptDeck.POOL_ASSET) == null) return null
        val fixed = HandwritingContribution.parse(lines("handwriting/contribute_prompts.tsv"))
        return ContributePromptDeck.pools(fixed, lines(ContributePromptDeck.POOL_ASSET))
    }

    private fun char(kind: String, text: String) = TrainingPrompt("contribute:$kind:$text", TrainingPromptType.Chinese, text,
        source = "contribute", kind = kind)

    @Test fun differentWritersGetDifferentPromptsAndTheSameWriterKeepsOrder() {
        val pools = bundledPools() ?: return
        val a = ContributePromptDeck.handwriting(pools, ContributePromptDeck.seedOf("0123456789abcdef"))
        val b = ContributePromptDeck.handwriting(pools, ContributePromptDeck.seedOf("fedcba9876543210"))
        val again = ContributePromptDeck.handwriting(pools, ContributePromptDeck.seedOf("0123456789abcdef"))
        assertEquals(a, again)
        // 前 50 题两人重合很少
        val overlap = a.take(50).map { it.id }.toSet().intersect(b.take(50).map { it.id }.toSet())
        assertTrue("重合 ${overlap.size}", overlap.size < 10)
        // 题够多，写不完；每题只出一次
        assertTrue(a.size > 8000)
        assertEquals(a.size, a.map { it.id }.toSet().size)
        assertEquals(setOf("char", "rare", "word"), a.map { it.kind }.toSet())
        // 生僻字都带读音，词是 2～4 个字；没有繁体字和偏旁
        assertTrue(a.filter { it.kind == "rare" }.all { !it.hint.isNullOrEmpty() })
        assertTrue(a.none { it.label in setOf("將", "說", "宀", "艹", "氵") })
        assertTrue(a.filter { it.kind == "word" }.all { it.label.length in 2..4 })
    }

    @Test fun firstRoundsMixKindsAndPutMissingDataCharsEarly() {
        val pools = bundledPools() ?: return
        val deck = ContributePromptDeck.handwriting(pools, ContributePromptDeck.seedOf("0123456789abcdef"))
        val first = deck.take(10)
        assertEquals(listOf("char", "char", "word", "char", "char", "rare", "word", "char", "char", "word"), first.map { it.kind })
        val priority = pools.priority.map { it.id }.toSet()
        // 100 个缺数据的常用字在前 1000 题里出完
        assertEquals(priority, deck.take(1000).map { it.id }.filter { it in priority }.toSet())
    }

    @Test fun interleavesAndFallsBackWhenAKindRunsOut() {
        val pools = ContributePromptDeck.Pools(
            priority = listOf(char("char", "号")),
            common = (1..12).map { char("char", "常$it") },
            rare = listOf(char("rare", "犇")),
            words = listOf(char("word", "我们"), char("word", "中国")),
        )
        val deck = ContributePromptDeck.handwriting(pools, 7L)
        assertEquals(16, deck.size)
        assertEquals("号", deck.first().label)
        assertEquals(deck.size, deck.map { it.id }.toSet().size)
        // 词、生僻字出完后剩下的全是常用字
        assertTrue(deck.drop(10).all { it.kind == "char" })
    }

    @Test fun poolsSkipDuplicatesAndRareWithoutReading() {
        val pools = ContributePromptDeck.pools(
            fixed = listOf(char("char", "的"), char("rare", "犇"), char("word", "我们")),
            pool = sequenceOf("# 注释", "char\t的\t", "char\t了\t", "char\tabc\t", "rare\t垚\tyáo", "rare\t燚\t",
                "word\t我们\two men", "word\t中国\tzhong guo", "word\t好\thao", "word\t中国\tzhong guo", ""),
        )
        assertEquals(listOf("的"), pools.priority.map { it.label })
        assertEquals(listOf("了"), pools.common.map { it.label })
        assertEquals(listOf("犇", "垚"), pools.rare.map { it.label })
        assertEquals("yáo", pools.rare.last().hint)
        assertEquals(listOf("我们", "中国"), pools.words.map { it.label })
    }

    @Test fun skipsPromptsAlreadyWritten() {
        val deck = listOf("a", "b", "c", "d").map { char("char", it) }
        val written = setOf("contribute:char:b", "contribute:char:c")
        assertEquals(0, ContributePromptDeck.firstUnwritten(deck, 0) { it in written })
        assertEquals(3, ContributePromptDeck.firstUnwritten(deck, 1) { it in written })
        assertEquals(0, ContributePromptDeck.firstUnwritten(deck, 9) { it in written + "contribute:char:d" })
        assertEquals(2, ContributePromptDeck.firstUnwritten(deck, 2) { true })
        assertEquals(0, ContributePromptDeck.firstUnwritten(emptyList(), 5) { false })
    }

    @Test fun glideDecksAreLargeRandomPerWriterAndClean() {
        val englishFile = asset("english.txt") ?: return
        val wordsFile = asset(ContributePromptDeck.POOL_ASSET) ?: return
        val en = { seed: Long -> englishFile.useLines { ContributePromptDeck.englishGlide(it, seed) } }
        val a = en(ContributePromptDeck.seedOf("0123456789abcdef", "glide-English"))
        val b = en(ContributePromptDeck.seedOf("fedcba9876543210", "glide-English"))
        assertTrue(a.size > 3000)
        assertNotEquals(a.take(8), b.take(8))
        assertTrue(a.all { it.letters.matches(Regex("[a-z]{3,10}")) && it.label.isEmpty() })
        assertTrue(a.none { it.letters in setOf("sex", "porn", "www", "html", "http") })
        val zh = wordsFile.useLines { ContributePromptDeck.pinyinGlide(it, 1L) }
        assertTrue(zh.size > 2000)
        assertEquals(zh.size, zh.map { it.letters }.toSet().size)
        assertTrue(zh.any { it.letters == "women" && it.label == "我们" })
    }

    @Test fun seedFallsBackForNonHexWriter() {
        assertEquals(ContributePromptDeck.seedOf("xyz"), ContributePromptDeck.seedOf("xyz"))
        assertNotEquals(ContributePromptDeck.seedOf("ab", "glide-English"), ContributePromptDeck.seedOf("ab", "glide-Nine"))
    }
}
