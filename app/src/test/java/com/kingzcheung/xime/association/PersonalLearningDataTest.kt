package com.kingzcheung.xime.association

import org.junit.Assert.*
import org.junit.Test

class PersonalLearningDataTest {
    @Test fun legacyObservationsAreNotUniqueWordCounts() {
        val data = PersonalLearningData.decode("""{"bigrams":[{"tokens":["你","好"],"count":3}],"trigrams":[],"recentInputs":[]}""")
        assertEquals(1, data.uniqueSequences)
        assertEquals(3L, data.observations)
        assertEquals(data, PersonalLearningData.decode(data.encode()))
    }
    @Test fun wholeWordProfileRoundTripsSeparatelyFromPhoneLearning() {
        val data = PersonalLearningData(profileName = "自用", continuations = listOf(PersonalContinuation("我想", "看看", 12)))
        assertEquals(data, PersonalLearningData.decode(data.encode()))
        assertEquals(0L, data.observations)
        assertEquals("看看", PersonalContinuationIndex(data.continuations).predict("今天我想").single().text)
        assertTrue(PersonalContinuationIndex(data.continuations).predict("我想。").isEmpty())
    }
    @Test fun weightedTrieUsesConditionalCountsAndDoesNotRepeatInsertion() {
        val trie = NgramTrie()
        trie.insert(listOf("你", "好"), 900_000)
        trie.insert(listOf("你", "们"), 100_000)
        assertEquals(.9f, trie.next(listOf("你")).toMap().getValue("好"), .00001f)
        assertEquals(.1f, trie.getFrequency(listOf("你", "们")), .00001f)
        assertTrue(trie.next(listOf("我")).isEmpty())
    }
    @Test fun malformedOrDuplicateImportsAreRejectedBeforeReplacement() {
        for (row in listOf(
            """{"tokens":["你","好"],"count":0}""",
            """{"tokens":["你","好"],"count":1.5}""",
            """{"tokens":["你"],"count":1}""",
            """{"tokens":["你","好"],"count":1},{"tokens":["你","好"],"count":2}"""
        )) {
            try { PersonalLearningData.decode("""{"bigrams":[$row],"trigrams":[]}"""); fail(row) }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun longestContextWinsWithoutScanningUnrelatedRows() {
        val rows = (1..10_000).map { PersonalContinuation("词$it", "接话", 1) } +
            listOf(PersonalContinuation("我想", "看看", 2), PersonalContinuation("想", "问问", 10))
        val index = PersonalContinuationIndex(rows)
        repeat(1000) { assertEquals("看看", index.predict("我想").first().text) }
    }

    @Test fun storedLearningOverLimitsIsTrimmedInsteadOfRejected() {
        val rows = (0 until PersonalLearningData.MAX_SEQUENCES + 5).joinToString(",") {
            """{"tokens":["${'a' + it % 26}","${(0x4e00 + it % 20_000).toChar()}${(0x4e00 + it / 20_000).toChar()}"],"count":${if (it == 0) 3_000_000 else 1 + it % 7}}"""
        }
        val text = """{"bigrams":[$rows],"trigrams":[],"recentInputs":[]}"""
        try { PersonalLearningData.decode(text); fail("imports stay strict") } catch (_: IllegalArgumentException) { }
        val data = PersonalLearningData.decode(text, storedLocally = true)
        assertEquals(PersonalLearningData.STORED_KEPT, data.bigrams.size)
        assertTrue(data.bigrams.all { it.count in 1..PersonalLearningData.MAX_COUNT })
        assertEquals(listOf("a", "一一"), data.bigrams.first().tokens)
        assertEquals(data, PersonalLearningData.decode(data.encode()))
    }
    @Test fun dataWithinLimitsIsStoredUnchanged() {
        val data = PersonalLearningData(bigrams = listOf(LearnedSequence(listOf("你", "好"), 3)))
        assertSame(data, data.boundedForStorage())
    }
}
