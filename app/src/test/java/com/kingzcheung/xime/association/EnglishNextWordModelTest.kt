package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishNextWordModelTest {
    @Test fun keepsTheBestContinuationsInScoreOrder() {
        val model = EnglishNextWordModel.read(sequenceOf(
            "thank you 3",
            "thank the 40",
            "thank all 55",
            "thank god 33",
        ))
        assertEquals(listOf("you", "god", "the", "all"), model.predict("thank", 4))
    }

    @Test fun lowercasesAndIgnoresUnigramsAndBadScores() {
        val model = EnglishNextWordModel.read(sequenceOf(
            "the 33",
            "Thank You 3",
            "thank the 999",
            "thank all nope",
            "thank 123 4",
        ))
        assertEquals(listOf("you"), model.predict("THANK", 5))
    }

    @Test fun apostropheLookupMatchesTheStem() {
        val model = EnglishNextWordModel.read(sequenceOf("im fine 5", "youre right 7"))
        assertEquals(listOf("fine"), model.predict("I'm", 5))
        assertEquals(listOf("right"), model.predict("you're", 5))
    }

    @Test fun duplicateContinuationsDoNotRepeat() {
        val model = EnglishNextWordModel.read(sequenceOf("my name 4", "my name 8", "my email 5"))
        assertEquals(listOf("name", "email"), model.predict("my", 5))
    }

    @Test fun displayCanExpandContractions() {
        val model = EnglishNextWordModel.read(
            sequenceOf("i youre 3", "i all 9"),
        ) { word -> if (word == "youre") "you're" else word }
        assertEquals(listOf("you're", "all"), model.predict("i", 5))
    }

    @Test fun keepsAtMostEightPerPreviousWord() {
        val lines = (0 until 20).asSequence().map { index ->
            val next = "b" + ('a' + index)
            "a $next 1"
        }
        val model = EnglishNextWordModel.read(lines)
        assertTrue(model.predict("a", 20).size <= EnglishNextWordModel.MAX_PER_PREVIOUS)
    }

    @Test fun emptyLimitOrUnknownPreviousHasNoSuggestions() {
        val model = EnglishNextWordModel.read(sequenceOf("thank you 3"))
        assertEquals(emptyList<String>(), model.predict("thank", 0))
        assertEquals(emptyList<String>(), model.predict("unknown", 5))
        assertEquals(emptyList<String>(), model.predict(null, 5))
    }

    @Test fun bundledTableKnowsTheCommonContinuations() {
        val file = listOf(
            java.io.File("src/main/assets/english_bigrams.txt"),
            java.io.File("app/src/main/assets/english_bigrams.txt"),
        ).first { it.isFile }
        val model = file.bufferedReader().useLines { EnglishNextWordModel.read(it) }
        assertEquals("you", model.predict("thank", 1).first())
        assertEquals("to", model.predict("how", 1).first())
        assertTrue("life" in model.predict("my", 8))
    }
}
