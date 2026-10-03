package com.kingzcheung.xime.association

import org.junit.Assert.*
import org.junit.Test

class UserNgramContextTest {
    @Test fun longerContextWinsInsteadOfMixingAnUnrelatedFrequentSuffix() {
        val bigrams = NgramTrie()
        val trigrams = NgramTrie()
        bigrams.insert(listOf("学", "校"), 100)
        bigrams.insert(listOf("学", "习"), 1)
        trigrams.insert(listOf("想", "学", "习"), 1)
        assertEquals(listOf("习"), predictLearnedContinuation(bigrams, trigrams, listOf("我", "想", "学")).map { it.first })
        assertEquals(listOf("校", "习"), predictLearnedContinuation(bigrams, trigrams, listOf("上", "学")).sortedByDescending { it.second }.map { it.first })
        assertTrue(predictLearnedContinuation(bigrams, trigrams, emptyList()).isEmpty())
    }
}
